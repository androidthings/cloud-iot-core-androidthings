// Copyright 2018 Google LLC.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.things.iotcore;

import android.os.Process;
import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.jose4j.lang.JoseException;

import java.io.EOFException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLException;

/**
 * IotCoreClient manages interactions with Google Cloud IoT Core for a single device.
 *
 * <p>This class provides mechanisms for using Cloud IoT Core's main features. Namely
 * <ul>
 *    <li>Publishing device telemetry</li>
 *    <li>Publishing device state</li>
 *    <li>Receiving configuration changes</li>
 *    <li>Receiving commands</li>
 * </ul>
 *
 * <p>Create a new IotCoreClient using the {@link IotCoreClient.Builder}, and call
 * {@link IotCoreClient#connect()} to initialize the client connection. When you no longer
 * need to send and receive data, call {@link IotCoreClient#disconnect()} to close the connection
 * and free up resources.
 *
 * <p>Track the current connection using {@link IotCoreClient#isConnected()} or register a
 * {@link ConnectionCallback} to listen for changes in the client's conn, and publish data to Cloud
 *
 * <p>Publish data to Cloud IoT Core using {@link IotCoreClient#publishTelemetry(TopicEvent)}
 * and {@link IotCoreClient#publishDeviceState(byte[])}. These methods can be used regardless of the
 * client's connection state. If the client is connected, messages are published immediately.
 * Otherwise, if the client is disconnected, messages are stored in memory and sent when the
 * connection is reestablished.
 *
 * <p>Register an {@link OnConfigurationListener} with the client to receive device configuration
 * changes from Cloud IoT Core.
 *
 * <pre class="prettyprint">
 *     IotCoreClient iotCoreClient = new IotCoreClient.Builder()
 *             .setConnectionParams(connectionParams)
 *             .setKeyPair(keyPair);
 *             .setOnConfigurationListener(onConfigurationListener)
 *             .setOnCommandListener(onCommandListener)
 *             .setConnectionCallback(connectionCallback)
 *             .build();
 *     iotCoreClient.connect();
 *     iotCoreClient.publishDeviceState("Hello world!".getBytes());
 * </pre>
 *
 * <p>While disconnected, the client queues all messages for delivery when the connection is
 * restored. To customize the behavior of the offline message queue, call
 * {@link IotCoreClient.Builder#setTelemetryQueue(Queue)} with a queue implementation suited to
 * your application. If no queue implementation is provided, the default queue implementation is a
 * queue that stores up to 1000 telemetry events and drops events from the head of the queue when
 * messages are inserted beyond the maximum capacity.
 */
public class IotCoreClient {
    private static final String TAG = IotCoreClient.class.getSimpleName();

    // Settings for exponential backoff behavior. These values are from Cloud IoT Core's
    // recommendations at
    // https://cloud.google.com/iot/docs/requirements#managing_excessive_load_with_exponential_backoff
    private static final long INITIAL_RETRY_INTERVAL_MS = 1000;
    private static final int MAX_RETRY_JITTER_MS = 1000;
    private static final long MAX_RETRY_INTERVAL_MS = 64 * 1000;

    // Default telemetry queue capacity.
    private static final int DEFAULT_QUEUE_CAPACITY = 1000;

    // Quality of service level for device state messages. 1 = at least once. At most once (0) is
    // also supported.
    private static final int QOS_FOR_DEVICE_STATE_MESSAGES = 1;

    // All necessary information to connect to Cloud IoT Core.
    private final ConnectionParams mConnectionParams;

    // Generates signed JWTs to authenticate with Cloud IoT Core.
    private final JwtGenerator mJwtGenerator;

    // Underlying MQTT client implementation.
    private final MqttClient mMqttClient;

    private final List<String> mSubscriptionTopics = new ArrayList<>(2);
    // Control the execution of the background thread. The thread stops if mRunBackgroundThread is
    // false.
    private final AtomicBoolean mRunBackgroundThread;

    // Store telemetry event that failed to send so it can be resent when connection to
    // Cloud IoT Core can be reestablished.
    private TopicEvent mUnsentTelemetryTopicEvent;

    // Store telemetry event that failed to send so it can be resent when connection to
    // Cloud IoT Core can be reestablished.
    private TopicEvent mUnsentPubSubTopicEvent;

    // Store device state.
    private final AtomicReference<byte[]> mUnsentDeviceState;

    // Queue of unsent telemetry events.
    private final Object mQueueLock = new Object();
    @GuardedBy("mQueueLock")
    private final Queue<TopicEvent> mTelemetryQueue;

    // Queue of unsent PubSub events.
    private final Object mPubSubTopicQueueLock = new Object();
    @GuardedBy("mQueueLock")
    private final Queue<TopicEvent> mPubSubTopicQueue;

    // Client callbacks.
    private final Executor mConnectionCallbackExecutor;
    private final ConnectionCallback mConnectionCallback;

    // Thread on which networking operations are performed.
    private Thread mBackgroundThread;

    // Controls execution of backgroundThread
    private final Semaphore mSemaphore;

    // The connection status from the client's perspective.
    private final AtomicBoolean mClientConnectionState;

    // Tracks exponential backoff interval
    private final BoundedExponentialBackoff mBackoff;

    // Will be used to strip off the commands topic prefix
    private final String mCommandsTopicPrefixRegex;

    // Throw an IllegalArgumentException if ref is null.
    private static void checkNotNull(Object ref, String name) {
        if (ref == null) {
            throw new NullPointerException(name + " cannot be null");
        }
    }

    /**
     * IotCoreClient constructor used by the Builder.
     */
    private IotCoreClient(
            @NonNull ConnectionParams connectionParams,
            @NonNull KeyPair keyPair,
            @NonNull MqttClient mqttClient,
            @NonNull Queue<TopicEvent> telemetryQueue,
            @NonNull Queue<TopicEvent> pubSubTopicQueue,
            @Nullable Executor connectionCallbackExecutor,
            @Nullable ConnectionCallback connectionCallback,
            @Nullable Executor onConfigurationExecutor,
            @Nullable OnConfigurationListener onConfigurationListener,
            @Nullable Executor onCommandExecutor,
            @Nullable OnCommandListener onCommandListener) {
        this(
                connectionParams,
                mqttClient,
                new JwtGenerator(
                        keyPair,
                        connectionParams.getProjectId(),
                        Duration.ofMillis(connectionParams.getAuthTokenLifetimeMillis())),
                new AtomicBoolean(false),
                new AtomicReference<byte[]>(),
                telemetryQueue,
                pubSubTopicQueue,
                connectionCallbackExecutor,
                connectionCallback,
                onConfigurationExecutor,
                onConfigurationListener,
                onCommandExecutor,
                onCommandListener,
                new Semaphore(0),
                new BoundedExponentialBackoff(
                        INITIAL_RETRY_INTERVAL_MS,
                        MAX_RETRY_INTERVAL_MS,
                        MAX_RETRY_JITTER_MS),
                new AtomicBoolean(false));


    }

    @VisibleForTesting
    IotCoreClient(
            @NonNull ConnectionParams configuration,
            @NonNull MqttClient mqttClient,
            @NonNull JwtGenerator jwtGenerator,
            @NonNull AtomicBoolean runBackgroundThread,
            @NonNull AtomicReference<byte[]> unsentDeviceState,
            @NonNull Queue<TopicEvent> telemetryQueue,
            @NonNull Queue<TopicEvent> pubSubTopicQueue,
            @Nullable Executor connectionCallbackExecutor,
            @Nullable ConnectionCallback connectionCallback,
            @Nullable Executor onConfigurationExecutor,
            @Nullable OnConfigurationListener onConfigurationListener,
            @Nullable Executor onCommandExecutor,
            @Nullable OnCommandListener onCommandListener,
            @NonNull Semaphore semaphore,
            @NonNull BoundedExponentialBackoff backoff,
            @NonNull AtomicBoolean clientConnectionState) {
        checkNotNull(configuration, "ConnectionParams");
        checkNotNull(mqttClient, "MqttClient");
        checkNotNull(jwtGenerator, "JwtGenerator");
        checkNotNull(runBackgroundThread, "RunBackgroundThread");
        checkNotNull(unsentDeviceState, "unsentDeviceState");
        checkNotNull(telemetryQueue, "TelemetryQueue");
        checkNotNull(pubSubTopicQueue, "PubSubTopicQueue");
        checkNotNull(semaphore, "Semaphore");
        checkNotNull(backoff, "BoundedExponentialBackoff");
        checkNotNull(clientConnectionState, "ClientConnectionState");

        if (connectionCallback != null && connectionCallbackExecutor == null) {
            throw new IllegalArgumentException("No executor provided for connection callback");
        }
        if (onConfigurationListener != null && onConfigurationExecutor == null) {
            throw new IllegalArgumentException("No executor provided for configuration listener");
        }
        if (onCommandListener!= null && onCommandExecutor == null) {
            throw new IllegalArgumentException("No executor provided for command listener");
        }

        mConnectionParams = configuration;
        mMqttClient = mqttClient;
        mJwtGenerator = jwtGenerator;
        mRunBackgroundThread = runBackgroundThread;
        mUnsentDeviceState = unsentDeviceState;
        mTelemetryQueue = telemetryQueue;
        mPubSubTopicQueue = pubSubTopicQueue;
        mConnectionCallbackExecutor = connectionCallbackExecutor;
        mConnectionCallback = connectionCallback;
        mSemaphore = semaphore;
        mBackoff = backoff;
        mClientConnectionState = clientConnectionState;
        mCommandsTopicPrefixRegex = String.format(Locale.US, "^%s/?", mConnectionParams.getCommandsTopic());

        mMqttClient.setCallback(
                createMqttCallback(onConfigurationExecutor, onConfigurationListener, onCommandExecutor, onCommandListener));

        if (onConfigurationListener != null) {
            mSubscriptionTopics.add(mConnectionParams.getConfigurationTopic());
        }
        if (onCommandListener != null) {
            mSubscriptionTopics.add(String.format(Locale.US, "%s/#", mConnectionParams.getCommandsTopic()));
        }
    }

    /**
     * Constructs IotCoreClient instances.
     */
    public static class Builder {
        private ConnectionParams mConnectionParams;
        private KeyPair mKeyPair;
        private Queue<TopicEvent> mTelemetryQueue;
        private Queue<TopicEvent> mPubSubTopicQueue;
        private Executor mOnConfigurationExecutor;
        private OnConfigurationListener mOnConfigurationListener;
        private Executor mOnCommandExecutor;
        private OnCommandListener mOnCommandListener;
        private Executor mConnectionCallbackExecutor;
        private ConnectionCallback mConnectionCallback;

        /**
         * Set ConnectionParams the client should use to connect to Cloud IoT Core.
         *
         * <p>This parameter is required.
         *
         * @param connectionParams the connection parameters the client should use
         * @return this builder
         */
        public Builder setConnectionParams(
                @NonNull ConnectionParams connectionParams) {
            checkNotNull(connectionParams, "ConnectionParams");
            mConnectionParams = connectionParams;
            return this;
        }

        /**
         * Set the key pair used to register this device with Cloud IoT Core.
         *
         * <p>Supports RSA and EC key algorithms. See the Cloud IoT Core <a
         * href="https://cloud.google.com/iot/docs/concepts/device-security#security_standards">
         * security documentation</a> for more information.
         *
         * <p>This parameter is required.
         *
         * @param keyPair the key pair used to register the device in this configuration
         * @return this builder
         */
        public Builder setKeyPair(@NonNull KeyPair keyPair) {
            checkNotNull(keyPair, "Key pair");
            mKeyPair = keyPair;
            return this;
        }

        /**
         * Set the queue the client should use for storing telemetry events when the client is
         * disconnected from Cloud IoT Core.
         *
         * <p>This parameter is optional. If the telemetry queue is unspecified, the default queue
         * implementation is a queue that stores up to 1000 telemetry events and drops events from
         * the head of the queue when messages are inserted beyond the maximum capacity.
         *
         * <p>Users with more complicated requirements can provide their own telemetry queue
         * implementation to control IotCoreClient's behavior when they are trying to publish
         * events faster than IotCoreClient can send them to Cloud IoT Core (this could
         * happen under adverse network conditions).
         *
         * <p>Users who implement their own telemetry queue do not need to worry about concurrency
         * or thread safety, but IotCoreClient takes ownership over its telemetry queue, so users
         * should not attempt to add or remove events from the telemetry queue once the
         * IotCoreClient is constructed.
         *
         * @param telemetryQueue the queue this client should use for storing unpublished telemetry
         *                       events
         * @return this builder
         */
        public Builder setTelemetryQueue(
                @NonNull Queue<TopicEvent> telemetryQueue) {
            checkNotNull(telemetryQueue, "Telemetry queue");
            mTelemetryQueue = telemetryQueue;
            return this;
        }

        public Builder setPubSubTopicQueue(@NonNull Queue<TopicEvent> pubSubTopicQueue) {
            checkNotNull(pubSubTopicQueue, "Telemetry queue");
            mPubSubTopicQueue = pubSubTopicQueue;
            return this;
        }

        /**
         * Add a callback to receive updates when the connection to Cloud IoT Core changes.
         *
         * <p>This parameter is optional.
         *
         * @param executor the thread the callback should be executed on
         * @param callback the callback to add
         * @return this builder
         */
        public Builder setConnectionCallback(
                @NonNull Executor executor, @NonNull ConnectionCallback callback) {
            checkNotNull(executor, "Connection callback executor");
            checkNotNull(callback, "Connection callback");

            mConnectionCallbackExecutor = executor;
            mConnectionCallback = callback;
            return this;
        }

        /**
         * Add a callback to receive updates when the connection to Cloud IoT Core changes.
         *
         * <p>This parameter is optional.
         *
         * @param callback the callback to add
         * @return this builder
         */
        public Builder setConnectionCallback(@NonNull ConnectionCallback callback) {
            checkNotNull(callback, "Connection callback");
            mConnectionCallback = callback;
            return this;
        }

        /**
         * Add a listener to receive configuration changes sent to the device from Cloud IoT
         * Core.
         *
         * <p>Cloud IoT Core resends device configuration every time the device connects to
         * Cloud IoT Core, so clients should expect to receive the same configuration
         * multiple times.
         *
         * <p>This parameter is optional.
         *
         * @param executor the thread the callback should be executed on
         * @param listener the listener to add
         * @return this builder
         */
        public Builder setOnConfigurationListener(
                @NonNull Executor executor, @NonNull OnConfigurationListener listener) {
            checkNotNull(executor, "Executor for OnConfigurationListener");
            checkNotNull(listener, "OnConfiguration listener");

            mOnConfigurationExecutor = executor;
            mOnConfigurationListener = listener;
            return this;
        }

        /**
         * Add a listener to receive configuration changes sent to the device from Cloud IoT
         * Core.
         *
         * <p>Cloud IoT Core resends device configuration every time the device connects to
         * Cloud IoT Core, so clients should expect to receive the same configuration
         * multiple times.
         *
         * <p>This parameter is optional.
         *
         * @param listener the listener to add
         * @return this builder
         */
        public Builder setOnConfigurationListener(
                @NonNull OnConfigurationListener listener) {
            checkNotNull(listener, "OnConfiguration listener");
            mOnConfigurationListener = listener;
            return this;
        }

        /**
         * Add a listener to receive commands sent to the device from Cloud IoT
         * Core.
         *
         * <p>This parameter is optional.
         *
         * @param executor the thread the callback should be executed on
         * @param listener the listener to add
         * @return this builder
         */
        public Builder setOnCommandListener(
                @NonNull Executor executor, @NonNull OnCommandListener listener) {
            checkNotNull(executor, "Executor for OnCommandListener");
            checkNotNull(listener, "OnCommand listener");

            mOnCommandExecutor = executor;
            mOnCommandListener = listener;
            return this;
        }

        /**
         * Add a listener to receive commands sent to the device from Cloud IoT
         * Core.
         *
         * <p>This parameter is optional.
         *
         * @param listener the listener to add
         * @return this builder
         */
        public Builder setOnCommandListener(
                @NonNull OnCommandListener listener) {
            checkNotNull(listener, "OnCommand listener");
            mOnCommandListener = listener;
            return this;
        }

        /**
         * Construct a new IotCoreClient with the Builder's parameters.
         *
         * @return a new IotCoreClient instance
         * @throws IllegalArgumentException if the Builder's parameters are invalid
         */
        public IotCoreClient build() {
            checkNotNull(mConnectionParams, "ConnectionParams");
            checkNotNull(mKeyPair, "KeyPair");
            if (mTelemetryQueue == null) {
                mTelemetryQueue =
                        new CapacityQueue<>(DEFAULT_QUEUE_CAPACITY, CapacityQueue.DROP_POLICY_HEAD);
            }
            if (mPubSubTopicQueue == null) {
                mPubSubTopicQueue =
                        new CapacityQueue<>(DEFAULT_QUEUE_CAPACITY, CapacityQueue.DROP_POLICY_HEAD);
            }
            if (mOnConfigurationListener != null && mOnConfigurationExecutor == null) {
                mOnConfigurationExecutor = createDefaultExecutor();
            }
            if (mOnCommandListener != null && mOnCommandExecutor == null) {
                mOnCommandExecutor = createDefaultExecutor();
            }
            if (mConnectionCallback != null && mConnectionCallbackExecutor == null) {
                mConnectionCallbackExecutor = createDefaultExecutor();
            }

            MqttClient mqttClient;
            try {
                mqttClient = new MqttClient(
                        mConnectionParams.getBrokerUrl(),
                        mConnectionParams.getClientId(),
                        new MemoryPersistence());
            } catch (MqttException e) {
                // According to the Paho documentation, this exception happens when the arguments to
                // the method are valid, but "other problems" occur. Since that isn't particularly
                // helpful, rethrow as an IllegalStateException so public API doesn't depend on Paho
                // library.
                //
                // Paho docs for this method are available at
                // http://www.eclipse.org/paho/files/javadoc/org/eclipse/paho/client/mqttv3/MqttClient.html#MqttClient-java.lang.String-java.lang.String-org.eclipse.paho.client.mqttv3.MqttClientPersistence-
                //
                // Based on the Paho source (https://github.com/eclipse/paho.mqtt.java), it looks
                // like this exception should never happen. The MqttClient constructor throws an
                // MqttException if
                //   1. MemoryPersistence.open throws an exception. This cannot happen.
                //      MemoryPersistence.open says it throws an MqttPersistenceException because it
                //      implements an interface that requires that definition.
                //   2. If there's an exception when sending unsent messages stored in the
                //      MemoryPersistence object. This should never happen because we make a new
                //      MemoryPersistence instance every time we call the MqttClient constructor.
                throw new IllegalStateException(e);
            }

            return new IotCoreClient(
                    mConnectionParams,
                    mKeyPair,
                    mqttClient,
                    mTelemetryQueue,
                    mPubSubTopicQueue,
                    mConnectionCallbackExecutor,
                    mConnectionCallback,
                    mOnConfigurationExecutor,
                    mOnConfigurationListener,
                    mOnCommandExecutor,
                    mOnCommandListener);
        }
    }

    private static Executor createDefaultExecutor() {
        return Executors.newCachedThreadPool();
    }

    private MqttCallback createMqttCallback(
            @Nullable final Executor onConfigurationExecutor,
            @Nullable final OnConfigurationListener onConfigurationListener,
            @Nullable final Executor onCommandExecutor,
            @Nullable final OnCommandListener onCommandListener) {
        return new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                // Release the semaphore blocking the background thread so it reconnects to IoT
                // Core.
                mSemaphore.release();

                int reason = ConnectionCallback.REASON_UNKNOWN;
                if (cause instanceof MqttException) {
                    reason = getDisconnectionReason((MqttException) cause);
                }
                onDisconnect(reason);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                if (mConnectionParams.getConfigurationTopic().equals(topic) && onConfigurationListener != null && onConfigurationExecutor != null) {
                    // Call the client's OnConfigurationListener

                    final byte[] payload = message.getPayload();
                    onConfigurationExecutor.execute(
                            new Runnable() {
                                @Override
                                public void run() {
                                    onConfigurationListener.onConfigurationReceived(payload);
                                }
                            });
                } else if (topic.startsWith(mConnectionParams.getCommandsTopic()) && onCommandListener != null && onCommandExecutor != null) {
                    // Call the client's OnCommandListener

                    final byte[] payload = message.getPayload();

                    final String subFolder = topic.replaceFirst(mCommandsTopicPrefixRegex, "");
                    onCommandExecutor.execute(
                            new Runnable() {
                                @Override
                                public void run() {
                                    onCommandListener.onCommandReceived(subFolder, payload);
                                }
                            });
                }
            }


            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        };
    }

    // Thread for handling blocking network operations.
    private class BackgroundThread implements Runnable {
        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

            // Run as long as the thread is enabled.
            while (mRunBackgroundThread.get()) {
                reconnectLoop();
            }

            // Shut down the thread
            try {
                mMqttClient.disconnectForcibly();
            } catch (MqttException mqttException) {
                Log.e(TAG, "Error disconnecting from Cloud IoT Core", mqttException);
            }
            onDisconnect(ConnectionCallback.REASON_CLIENT_CLOSED);
            mBackgroundThread = null;
        }
    }

    @VisibleForTesting
    void reconnectLoop() {
        Log.d(TAG, "in reconnect loop");
        try {
            connectMqttClient();

            // Successfully connected, so we can reset the backoff time.
            mBackoff.reset();

            // Perform tasks that require a connection
            doConnectedTasks();
        } catch (MqttException mqttException) {
            if (isRetryableError(mqttException)) {
                sleepUntil(Instant.now().plusMillis(mBackoff.nextBackoff()));
            } else {
                // Error isn't recoverable. I.e. the error has to do with the way the client is
                // configured. Stop the thread to avoid spamming GCP.
                mRunBackgroundThread.set(false);
                Log.e(TAG, "Disconnected from Cloud IoT Core and cannot reconnect", mqttException);
            }
            onDisconnect(getDisconnectionReason(mqttException));
        } catch (JoseException joseException) {
            // Error signing the JWT. Not a retryable error.
            mRunBackgroundThread.set(false);
            Log.e(TAG, "Disconnected from Cloud IoT Core and cannot reconnect", joseException);
        }
    }

    // Block thread until future instant has passed.
    private void sleepUntil(Instant futureInstant) {
        long futureInstantMillis = futureInstant.toEpochMilli();
        long sleepTime = futureInstantMillis - Instant.now().toEpochMilli();

        while (sleepTime > 0) {
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException interruptedException) {
                // Nothing to do here. Keep sleeping until past futureInstant.
            }
            sleepTime = futureInstantMillis - Instant.now().toEpochMilli();
        }
    }

    @VisibleForTesting
    void doConnectedTasks() throws MqttException {
        while (isConnected()) {
            // Block until there is something to do
            mSemaphore.acquireUninterruptibly();

            // Semaphore released, so there must be a task to do. If there are multiple things to do
            // prioritize as follows:
            //   1. Stop the thread if instructed to do so
            //   2. Publish device state
            //   3. Publish telemetry events

            // Check whether the thread should continue running.
            if (!mRunBackgroundThread.get()) {
                return;
            }

            // Check whether there is device state to send
            byte[] state = mUnsentDeviceState.get();
            if (state != null) {
                // Send device state
                publish(mConnectionParams.getDeviceStateTopic(), state,
                        QOS_FOR_DEVICE_STATE_MESSAGES);
                Log.d(TAG, "Published state: " + new String(state));

                // It's possible the device state changed while we were sending the original device
                // state, so only clear the unsent device state if it didn't change.
                mUnsentDeviceState.compareAndSet(state, null);
                continue;
            }

            handleTelemetry();

            handlePubSubTopic();
        }
    }

    private void handleTelemetry() throws MqttException {
        // Only send events from the client's telemetry queue is there is not an unsent event
        // already.
        if (mUnsentTelemetryTopicEvent == null) {
            synchronized (mQueueLock) {
                mUnsentTelemetryTopicEvent = mTelemetryQueue.poll();
            }
            if (mUnsentTelemetryTopicEvent == null) {
                // Nothing to do
                return;
            }
        }

        // Send the event. Could throw MqttException on error.
        publish(
                mConnectionParams.getTelemetryTopic() + mUnsentTelemetryTopicEvent.getTopicSubpath(),
                mUnsentTelemetryTopicEvent.getData(),
                mUnsentTelemetryTopicEvent.getQos());
        Log.d(TAG, "Published telemetry event: " + new String(mUnsentTelemetryTopicEvent.getData()));

        // Event sent successfully. Clear the cached event.
        mUnsentTelemetryTopicEvent = null;
    }

    private void handlePubSubTopic() throws MqttException {
        if (mUnsentPubSubTopicEvent == null) {
            synchronized (mPubSubTopicQueueLock) {
                mUnsentPubSubTopicEvent = mPubSubTopicQueue.poll();
            }
            if (mUnsentPubSubTopicEvent == null) {
                // Nothing to do
                return;
            }
        }

        // Send the event. Could throw MqttException on error.
        publish(
                mUnsentPubSubTopicEvent.getTopicName() + mUnsentPubSubTopicEvent.getTopicSubpath(),
                mUnsentPubSubTopicEvent.getData(),
                mUnsentPubSubTopicEvent.getQos());
        Log.d(TAG, "Published Topic event: " + new String(mUnsentPubSubTopicEvent.getData()));

        // Event sent successfully. Clear the cached event.
        mUnsentPubSubTopicEvent = null;
    }

    // Publish data to topic.
    private void publish(@NonNull String topic, byte[] data, int qos) throws MqttException {
        try {
            mMqttClient.publish(topic, data, qos, false /* do not send as "retained" message */);
        } catch (MqttException mqttException) {
            // If there was an error publishing the message, it was either because of an issue with
            // the way the message itself was formatted or an error with the network.
            //
            // In the network error case, the message can be resent when the network is working
            // again. Rethrow the exception so higher level functions can take care of reconnecting
            // and resending the message.
            //
            // If the message itself was the problem, don't propagate the error since there's
            // nothing we can do about it except log the error to the client.
            if (isRetryableError(mqttException)) {
                // Rethrow and add a permit to the semaphore that controls the background
                // thread since the background thread removed a permit when trying to publish this
                // message originally.
                mSemaphore.release();
                throw mqttException;
            }

            // Return success and don't try to resend the message that caused the exception. Log
            // the error so the user has some indication that something went wrong.
            Log.w(TAG, "Error publishing message to " + topic, mqttException);
        }
    }

    /**
     * Determine whether the mqttException is an error that may be resolved by retrying or whether
     * the error cannot be resolved. Determined according to guidance from Cloud IoT Core's
     * <a href="https://cloud.google.com/iot/docs/how-tos/errors">documentation</a>.
     *
     * @return Returns true if the MQTT client should resend the message. Returns false otherwise.
     */
    private boolean isRetryableError(MqttException mqttException) {
        // Retry using exponential backoff in cases where appropriate. Otherwise, log
        // the failure.
        switch (mqttException.getReasonCode()) {
            // Retry cases:
            case MqttException.REASON_CODE_SERVER_CONNECT_ERROR:
            case MqttException.REASON_CODE_WRITE_TIMEOUT:
            case MqttException.REASON_CODE_CLIENT_NOT_CONNECTED:
            case MqttException.REASON_CODE_CLIENT_TIMEOUT:
                return true;
            case MqttException.REASON_CODE_CLIENT_EXCEPTION:
                // This case happens when there is no internet connection. Unfortunately, Paho
                // doesn't provide a better way to get that information.
                if (mqttException.getCause() instanceof UnknownHostException) {
                    return true;
                }
            case MqttException.REASON_CODE_CONNECTION_LOST:
                // If the MqttException's cause is an EOFException, then the client or Cloud IoT
                // Core closed the connection. If mRunBackgroundThread is true, then we know the
                // client didn't close the connection and the connection was likely closed because
                // the client was publishing device state updates too quickly. Mark this as a
                // "retryable" error so the message that caused the exception isn't discarded.
                if (mqttException.getCause() instanceof EOFException
                        && mRunBackgroundThread.get()) {
                    return true;
                }
            default:
                return false;
        }
    }

    /**
     * Determine appropriate error to return to client based on MqttException.
     */
    private @ConnectionCallback.DisconnectReason int getDisconnectionReason(
            MqttException mqttException) {
        switch (mqttException.getReasonCode()) {
            case MqttException.REASON_CODE_FAILED_AUTHENTICATION:
            case MqttException.REASON_CODE_NOT_AUTHORIZED:
                // These cases happen if the client uses an invalid IoT Core registry ID, invalid
                // Iot Core device ID, invalid GCP cloud region, or unregistered signing key.
                return ConnectionCallback.REASON_NOT_AUTHORIZED;
            case MqttException.REASON_CODE_CONNECTION_LOST:
                if (mqttException.getCause() instanceof EOFException) {
                    // This case happens when Paho or Cloud IoT Core closes the connection.
                    if (mRunBackgroundThread.get()) {
                        // If mRunBackgroundThread is true, then Cloud IoT Core closed the
                        // connection. For example, this could happen if the client used an invalid
                        // GCP project ID, the client exceeds a rate limit set by Cloud IoT Core, or
                        // if the MQTT broker address is invalid.
                        return ConnectionCallback.REASON_CONNECTION_LOST;
                    } else {
                        // If mRunBackgroundThread is false, then the client closed the connection.
                        return ConnectionCallback.REASON_CLIENT_CLOSED;
                    }
                }

                if (mqttException.getCause() instanceof SSLException) {
                    // This case happens when something goes wrong in the network that ends an
                    // existing connection to Cloud IoT Core (e.g. the wifi driver resets).
                    return ConnectionCallback.REASON_CONNECTION_LOST;
                }
                return ConnectionCallback.REASON_UNKNOWN;
            case MqttException.REASON_CODE_CLIENT_EXCEPTION:
                // Paho uses this reason code for several distinct error cases
                if (mqttException.getCause() instanceof SocketTimeoutException) {
                    // This case could happen if the MQTT bridge port number is wrong or of there
                    // is some other error with the MQTT bridge that keeps it from responding.
                    return ConnectionCallback.REASON_CONNECTION_TIMEOUT;
                }
                if (mqttException.getCause() instanceof UnknownHostException) {
                    // This case happens if the client is disconnected from the internet or if they
                    // use an invalid hostname for the MQTT bridge. Unfortunately, Paho doesn't
                    // provide a way to get more information.
                    return ConnectionCallback.REASON_CONNECTION_LOST;
                }
                return ConnectionCallback.REASON_UNKNOWN;
            case MqttException.REASON_CODE_CLIENT_TIMEOUT:
            case MqttException.REASON_CODE_WRITE_TIMEOUT:
                return ConnectionCallback.REASON_CONNECTION_TIMEOUT;
            default:
                return ConnectionCallback.REASON_UNKNOWN;
        }
    }

    // Blocking
    private void connectMqttClient() throws JoseException, MqttException {
        if (mMqttClient.isConnected()) {
            return;
        }
        mMqttClient.connect(configureConnectionOptions());

        for (final String topic : mSubscriptionTopics) {
            mMqttClient.subscribe(topic);
        }
        onConnection();
    }

    private MqttConnectOptions configureConnectionOptions() throws JoseException {
        MqttConnectOptions options = new MqttConnectOptions();

        // Note that the Cloud IoT only supports MQTT 3.1.1, and Paho requires that we
        // explicitly set this. If you don't set MQTT version, the server will immediately close its
        // connection to your device.
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);

        // Cloud IoT Core ignores the user name field, but Paho requires a user name in order
        // to send the password field. We set the user name because we need the password to send a
        // JWT to authorize the device.
        options.setUserName("unused");

        // generate the jwt password
        options.setPassword(mJwtGenerator.createJwt().toCharArray());

        return options;
    }

    // Call client's connection callbacks
    private void onConnection() {
        if (mConnectionCallback == null) {
            return;
        }

        mConnectionCallbackExecutor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        if (!mClientConnectionState.getAndSet(true)) {
                            mConnectionCallback.onConnected();
                        }
                    }
                });
    }

    // Call client's connection callbacks
    private void onDisconnect(@ConnectionCallback.DisconnectReason final int reason) {
        if (mConnectionCallback == null) {
            return;
        }

        mConnectionCallbackExecutor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        if (reason == ConnectionCallback.REASON_NOT_AUTHORIZED) {
                            // Always notify on NOT_AUTHORIZED errors since they mean the client is
                            // misconfigured and needs to do something to fix the problem.
                            mClientConnectionState.set(false);
                            mConnectionCallback.onDisconnected(reason);
                        } else if (mClientConnectionState.getAndSet(false)) {
                            // Otherwise, only notify the client if they have not been notified
                            // about the change in connection yet.
                            mConnectionCallback.onDisconnected(reason);
                        }
                    }
                });
    }

    /**
     * Connect to Cloud IoT Core and perform any other required set up.
     *
     * <p>If the client registered a {@link ConnectionCallback},
     * {@link ConnectionCallback#onConnected()} will be called when the connection with
     * Cloud IoT Core is established. If the IotCoreClient ever disconnects from Cloud
     * IoT Core after this method is called, it will automatically reestablish the connection unless
     * {@link IotCoreClient#disconnect()} is called.
     *
     * <p>This method is non-blocking.
     */
    public void connect() {
        mRunBackgroundThread.set(true);
        if (mBackgroundThread == null || !mBackgroundThread.isAlive()) {
            mBackgroundThread = spawnBackgroundThread();
        }
    }

    private Thread spawnBackgroundThread() {
        Thread backgroundThread = new Thread(new BackgroundThread());
        backgroundThread.start();
        return backgroundThread;
    }


    /**
     * Returns true if connected to Cloud IoT Core, and returns false otherwise.
     *
     * @return whether the client is connection to Cloud IoT Core
     */
    public boolean isConnected() {
        return mMqttClient.isConnected();
    }

    /**
     * Disconnect the client from Cloud IoT Core.
     *
     * <p>This method is non-blocking.
     */
    public void disconnect() {
        if (mBackgroundThread == null || !mBackgroundThread.isAlive()) {
            return;
        }

        mRunBackgroundThread.set(false);
        mSemaphore.release();
    }

    /**
     * Add a telemetry event to this client's telemetry queue, if it is possible to do so without
     * violating the telemetry queue's capacity restrictions, and publish the event to
     * Cloud IoT Core as soon as possible.
     *
     * <p>This method is non-blocking.
     *
     * @param event the telemetry event to publish
     * @return Returns true if the event was queued to send, or return false if the event could
     * not be queued
     */
    public boolean publishTelemetry(@NonNull TopicEvent event) {
        synchronized (mQueueLock) {
            int preOfferSize = mTelemetryQueue.size();
            if (!mTelemetryQueue.offer(event) || mTelemetryQueue.size() == preOfferSize) {
                // Don't increase the number of permits in the semaphore because the event wasn't
                // added to the queue.
                return false;
            }
        }

        mSemaphore.release();
        return true;
    }

    public boolean publishTopicEvent(@NonNull TopicEvent event) {
        synchronized (mPubSubTopicQueueLock) {
            int preOfferSize = mPubSubTopicQueue.size();
            if (!mPubSubTopicQueue.offer(event) || mPubSubTopicQueue.size() == preOfferSize) {
                // Don't increase the number of permits in the semaphore because the event wasn't
                // added to the queue.
                return false;
            }
        }

        mSemaphore.release();
        return true;
    }

    /**
     * Publishes state data to Cloud IoT Core.
     *
     * <p>If the connection to Cloud IoT Core is lost and messages cannot be published to
     * Cloud IoT Core, device state is published to Cloud IoT Core before any
     * unpublished telemetry events when the connection is reestablished.
     *
     * <p>If there are multiple attempts to publish device state while disconnected from
     * Cloud IoT Core, only the newest device state will be published when the connection is
     * reestablished.
     *
     * <p>This method is non-blocking, and state is published using "at least once" semantics.
     *
     * <p>Cloud IoT Core limits the number of device state updates per device to 1 per
     * second. If clients of this library attempt to publish device state faster than that, some
     * device state data may be lost when Cloud IoT Core resets the connection. The
     * Cloud IoT Core <a href="https://cloud.google.com/iot/quotas">documentation</a> has more
     * information about quotas and usage restrictions.
     *
     * @param state the device state data to publish
     */
    public void publishDeviceState(byte[] state) {
        if (mUnsentDeviceState.getAndSet(state) == null) {
            mSemaphore.release();
        }
    }
}
