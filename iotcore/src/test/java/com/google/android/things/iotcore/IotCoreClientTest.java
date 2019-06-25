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

import android.support.annotation.NonNull;

import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.jose4j.lang.JoseException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;

import java.io.EOFException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLException;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** IotCoreClient unit tests. */
@RunWith(RobolectricTestRunner.class)
public class IotCoreClientTest {
    private static final String TOPIC = "topic";
    private static final String COMMAND = "command";
    private static final byte[] DATA = "Hello world".getBytes();
    private static final int QOS = TopicEvent.QOS_AT_LEAST_ONCE;

    private final ConnectionParams mMockConnectionParams = mock(ConnectionParams.class);
    private final MqttClient mMockMqttClient = mock(MqttClient.class);
    private final JwtGenerator mMockJwtGenerator = mock(JwtGenerator.class);
    private final Executor mMockConnectionCallbackExecutor = mock(Executor.class);
    private final ConnectionCallback mMockConnectionCallback = mock(ConnectionCallback.class);
    private final Executor mMockOnConfigurationExecutor = mock(Executor.class);
    private final OnConfigurationListener mMockOnConfigurationListener =
            mock(OnConfigurationListener.class);
    private final Executor mMockOnCommandExecutor = mock(Executor.class);
    private final OnCommandListener mMockOnCommandListener =
            mock(OnCommandListener.class);
    private final Semaphore mMockSemaphore = mock(Semaphore.class);
    private final BoundedExponentialBackoff mMockBackoff = mock(BoundedExponentialBackoff.class);
    private final TopicEvent mMockTopicEvent = mock(TopicEvent.class);
    private final PrivateKey mMockPrivateKey = mock(PrivateKey.class);
    private final PublicKey mMockPublicKey = mock(PublicKey.class);

    @SuppressWarnings("unchecked")
    private final Queue<TopicEvent> mMockTelemetryQueue = mock(Queue.class);

    @SuppressWarnings("unchecked")
    private final Queue<TopicEvent> mMockPubSubTopicQueue = mock(Queue.class);

    // Cant mock methods in AtomicBoolean
    private AtomicBoolean mClientConnectionStateSpy;
    private AtomicBoolean mRunBackgroundThreadSpy;
    private AtomicReference<byte[]> mUnsentDeviceStateSpy;

    // Can't mock KeyPair
    private final KeyPair mKeyPair = new KeyPair(mMockPublicKey, mMockPrivateKey);

    private IotCoreClient mTestIotCoreClient;
    private MqttCallback mClientMqttCallback;

    @Before
    public void setUp() throws JoseException {
        mClientConnectionStateSpy = spy(new AtomicBoolean(false));
        mRunBackgroundThreadSpy = spy(new AtomicBoolean(false));
        mUnsentDeviceStateSpy = spy(new AtomicReference<byte[]>());

        mTestIotCoreClient = new IotCoreClient(
                mMockConnectionParams,
                mMockMqttClient,
                mMockJwtGenerator,
                mRunBackgroundThreadSpy,
                mUnsentDeviceStateSpy,
                mMockTelemetryQueue,
                mMockPubSubTopicQueue,
                mMockConnectionCallbackExecutor,
                mMockConnectionCallback,
                mMockOnConfigurationExecutor,
                mMockOnConfigurationListener,
                mMockOnCommandExecutor,
                mMockOnCommandListener,
                mMockSemaphore,
                mMockBackoff,
                mClientConnectionStateSpy);

        // Get the MqttCallback created during initialization.
        ArgumentCaptor<MqttCallback> argument = ArgumentCaptor.forClass(MqttCallback.class);
        verify(mMockMqttClient).setCallback(argument.capture());
        mClientMqttCallback = argument.getValue();

        // KeyPair mocks
        when(mMockPrivateKey.getAlgorithm()).thenReturn("RSA");
        when(mMockPublicKey.getAlgorithm()).thenReturn("RSA");

        // JwtGenerator mock
        when(mMockJwtGenerator.createJwt()).thenReturn("JWT");

        // TopicEvent mock
        when(mMockTopicEvent.getTopicSubpath()).thenReturn(TOPIC);
        when(mMockTopicEvent.getData()).thenReturn(DATA);
        when(mMockTopicEvent.getQos()).thenReturn(QOS);

        // ConnectionParams mock
        when(mMockConnectionParams.getTelemetryTopic()).thenReturn(TOPIC);
        when(mMockConnectionParams.getDeviceStateTopic()).thenReturn(TOPIC);
        when(mMockConnectionParams.getConfigurationTopic()).thenReturn(TOPIC);
        when(mMockConnectionParams.getCommandsTopic()).thenReturn(COMMAND);
        when(mMockConnectionParams.getBrokerUrl()).thenReturn("ssl://abc:123");
        when(mMockConnectionParams.getClientId()).thenReturn("id");
        when(mMockConnectionParams.getProjectId()).thenReturn("id");
        when(mMockConnectionParams.getAuthTokenLifetimeMillis()).thenReturn(0L);
    }

    private void setUpWithSerialExecutor() throws JoseException {
        reset(mMockMqttClient);
        SerialExecutor serialExecutor = new SerialExecutor();
        mTestIotCoreClient = new IotCoreClient(
                mMockConnectionParams,
                mMockMqttClient,
                mMockJwtGenerator,
                mRunBackgroundThreadSpy,
                mUnsentDeviceStateSpy,
                mMockTelemetryQueue,
                mMockPubSubTopicQueue,
                serialExecutor,
                mMockConnectionCallback,
                serialExecutor,
                mMockOnConfigurationListener,
                serialExecutor,
                mMockOnCommandListener,
                mMockSemaphore,
                mMockBackoff,
                mClientConnectionStateSpy);

        // Get the MqttCallback created during initialization.
        ArgumentCaptor<MqttCallback> argument = ArgumentCaptor.forClass(MqttCallback.class);
        verify(mMockMqttClient).setCallback(argument.capture());
        mClientMqttCallback = argument.getValue();

        when(mMockJwtGenerator.createJwt()).thenReturn("JWT");
    }

    @SuppressWarnings("unchecked")
    @After
    public void tearDown() {
        mTestIotCoreClient = null;
        mClientMqttCallback = null;

        reset(mMockConnectionParams);
        reset(mMockMqttClient);
        reset(mMockJwtGenerator);
        reset(mMockConnectionCallbackExecutor);
        reset(mMockConnectionCallback);
        reset(mMockOnConfigurationExecutor);
        reset(mMockOnConfigurationListener);
        reset(mMockSemaphore);
        reset(mMockTopicEvent);
        reset(mMockTelemetryQueue);
        reset(mClientConnectionStateSpy);
        reset(mMockPrivateKey);
        reset(mMockPublicKey);
    }

    private class SerialExecutor implements Executor {
        @Override
        public void execute(@NonNull Runnable r) {
            r.run();
        }
    }

    @Test
    public void testIsConnectedFalse() {
        when(mMockMqttClient.isConnected()).thenReturn(false);
        assertThat(mTestIotCoreClient.isConnected()).isFalse();
        verify(mMockMqttClient).isConnected();
    }

    @Test
    public void testIsConnectedTrue() {
        when(mMockMqttClient.isConnected()).thenReturn(true);
        assertThat(mTestIotCoreClient.isConnected()).isTrue();
        verify(mMockMqttClient).isConnected();
    }

    @Test
    public void testConnectSuccessful() {
        when(mMockMqttClient.isConnected()).thenReturn(false);

        mTestIotCoreClient.connect();
        assertThat(mRunBackgroundThreadSpy.get()).isTrue();

        // Stop background thread
        mRunBackgroundThreadSpy.set(false);
    }

    @Test
    public void testDisconnect() {
        when(mMockMqttClient.isConnected()).thenReturn(false);

        mTestIotCoreClient.connect();
        mTestIotCoreClient.disconnect();

        assertThat(mRunBackgroundThreadSpy.get()).isFalse();
        verify(mMockSemaphore).release();
    }

    @Test
    public void testPublishTelemetryEmptyQueue() {
        when(mMockTelemetryQueue.offer(mMockTopicEvent)).thenReturn(true);
        when(mMockTelemetryQueue.size()).thenReturn(0).thenReturn(1);

        assertThat(mTestIotCoreClient.publishTelemetry(mMockTopicEvent)).isTrue();

        verify(mMockSemaphore).release();
    }

    @Test
    public void testPublishTelemetryFullQueue() {
        when(mMockTelemetryQueue.offer(mMockTopicEvent)).thenReturn(false);
        when(mMockTelemetryQueue.size()).thenReturn(1);

        assertThat(mTestIotCoreClient.publishTelemetry(mMockTopicEvent)).isFalse();

        verify(mMockSemaphore, never()).release();
    }

    @Test
    public void testPublishDeviceState() {
        mTestIotCoreClient.publishDeviceState(DATA);
        assertThat(mUnsentDeviceStateSpy.get()).isEqualTo(DATA);
        verify(mMockSemaphore).release();
    }

    @Test
    public void testPublishDeviceStateExistingData() {
        mUnsentDeviceStateSpy.set("existing".getBytes());

        mTestIotCoreClient.publishDeviceState(DATA);

        assertThat(mUnsentDeviceStateSpy.get()).isEqualTo(DATA);

        verify(mMockSemaphore, never()).release();
    }

    @Test
    public void testOnConfigurationCallbackValidTopic() throws Exception {
        MqttMessage mockMessage = mock(MqttMessage.class);
        when(mockMessage.getPayload()).thenReturn(DATA);

        mClientMqttCallback.messageArrived(TOPIC, mockMessage);

        verify(mMockOnConfigurationExecutor).execute(any(Runnable.class));
    }

    @Test
    public void testOnConfigurationCallbackValidTopicSerialExecutor() throws Exception {
        setUpWithSerialExecutor();
        MqttMessage mockMessage = mock(MqttMessage.class);
        when(mockMessage.getPayload()).thenReturn(DATA);

        mClientMqttCallback.messageArrived(TOPIC, mockMessage);

        verify(mMockOnConfigurationListener).onConfigurationReceived(DATA);
    }

    @Test
    public void testOnCommandCallbackValidTopic() throws Exception {
        MqttMessage mockMessage = mock(MqttMessage.class);
        when(mockMessage.getPayload()).thenReturn(DATA);

        mClientMqttCallback.messageArrived(COMMAND + "/subFolder", mockMessage);

        verify(mMockOnCommandExecutor).execute(any(Runnable.class));
    }

    @Test
    public void testOnCommandCallbackValidTopicSerialExecutor() throws Exception {
        setUpWithSerialExecutor();
        MqttMessage mockMessage = mock(MqttMessage.class);
        when(mockMessage.getPayload()).thenReturn(DATA);

        mClientMqttCallback.messageArrived(COMMAND + "/subFolder", mockMessage);

        verify(mMockOnCommandListener).onCommandReceived("subFolder", DATA);
    }

    @Test
    public void testOnCommandCallbackValidTopicNoSubFolderSerialExecutor() throws Exception {
        setUpWithSerialExecutor();
        MqttMessage mockMessage = mock(MqttMessage.class);
        when(mockMessage.getPayload()).thenReturn(DATA);

        mClientMqttCallback.messageArrived(COMMAND, mockMessage);

        verify(mMockOnCommandListener).onCommandReceived("", DATA);
    }

    @Test
    public void testOnCallbackInvalidTopic() throws Exception {
        MqttMessage mockMessage = mock(MqttMessage.class);
        when(mockMessage.getPayload()).thenReturn(DATA);

        mClientMqttCallback.messageArrived("BAD_TOPIC", mockMessage);

        verify(mMockOnConfigurationExecutor, never()).execute(any(Runnable.class));
        verify(mMockOnCommandExecutor, never()).execute(any(Runnable.class));
    }

    @Test
    public void testOnDisconnectCallbackInvokedNetworkDown() {
        Throwable mockThrowable = mock(Throwable.class);
        when(mockThrowable.getCause()).thenReturn(new SSLException("Fake disconnect"));

        mClientConnectionStateSpy.set(true);
        mClientMqttCallback.connectionLost(mockThrowable);

        verify(mMockConnectionCallbackExecutor).execute(any(Runnable.class));
    }

    @Test
    public void testOnDisconnectCallbackInvokedNetworkDownSerialExecutor() throws JoseException {
        setUpWithSerialExecutor();
        MqttException mockMqttException = mock(MqttException.class);
        when(mockMqttException.getReasonCode())
                .thenReturn((int) MqttException.REASON_CODE_CONNECTION_LOST);
        when(mockMqttException.getCause()).thenReturn(new SSLException("Fake disconnect"));

        mClientConnectionStateSpy.set(true);
        mClientMqttCallback.connectionLost(mockMqttException);

        verify(mMockConnectionCallback).onDisconnected(ConnectionCallback.REASON_CONNECTION_LOST);
    }

    @Test
    public void testOnDisconnectCallbackInvokedClientClosed() {
        MqttException mockMqttException = mock(MqttException.class);
        when(mockMqttException.getReasonCode())
                .thenReturn((int) MqttException.REASON_CODE_CONNECTION_LOST);
        when(mockMqttException.getCause()).thenReturn(new EOFException("Fake disconnect"));

        mClientConnectionStateSpy.set(true);
        mClientMqttCallback.connectionLost(mockMqttException);

        verify(mMockConnectionCallbackExecutor).execute(any(Runnable.class));
    }

    @Test
    public void testOnDisconnectCallbackInvokedClientClosedSerialExecutor() throws JoseException {
        setUpWithSerialExecutor();
        MqttException mockMqttException = mock(MqttException.class);
        when(mockMqttException.getReasonCode())
                .thenReturn((int) MqttException.REASON_CODE_CONNECTION_LOST);
        when(mockMqttException.getCause()).thenReturn(new EOFException("Fake disconnect"));

        mClientConnectionStateSpy.set(true);
        mClientMqttCallback.connectionLost(mockMqttException);

        verify(mMockConnectionCallback).onDisconnected(ConnectionCallback.REASON_CLIENT_CLOSED);
    }

    @Test
    public void testOnDisconnectCallbackInvokedOtherError() {
        Throwable mockThrowable = mock(Throwable.class);
        when(mockThrowable.getCause()).thenReturn(new Exception("Fake disconnect"));

        mClientConnectionStateSpy.set(true);
        mClientMqttCallback.connectionLost(mockThrowable);

        verify(mMockConnectionCallbackExecutor).execute(any(Runnable.class));
    }

    @Test
    public void testOnDisconnectCallbackInvokedOtherErrorSerialExecutor() throws JoseException {
        setUpWithSerialExecutor();
        Throwable mockThrowable = mock(Throwable.class);
        when(mockThrowable.getCause()).thenReturn(new Exception("Fake disconnect"));

        mClientConnectionStateSpy.set(true);
        mClientMqttCallback.connectionLost(mockThrowable);

        verify(mMockConnectionCallback).onDisconnected(ConnectionCallback.REASON_UNKNOWN);
    }

    @Test
    public void testDoConnectedTasksDoesNothingWhenDisconnected() throws MqttException {
        when(mMockMqttClient.isConnected()).thenReturn(false);

        mTestIotCoreClient.doConnectedTasks();

        verify(mMockMqttClient).isConnected();
        verify(mMockTelemetryQueue, never()).poll();
    }

    @Test
    public void testPublishTelemetrySuccess() throws MqttException {
        when(mMockMqttClient.isConnected()).thenReturn(true).thenReturn(true).thenReturn(false);
        when(mMockTelemetryQueue.poll()).thenReturn(mMockTopicEvent);
        mRunBackgroundThreadSpy.set(true);

        mTestIotCoreClient.reconnectLoop();

        verify(mMockMqttClient).publish(eq(TOPIC + TOPIC), eq(DATA), eq(QOS), any(Boolean.class));
    }

    @Test
    public void testTelemetryEventSentAfterFailure() throws MqttException {
        when(mMockTelemetryQueue.poll()).thenReturn(mMockTopicEvent).thenReturn(null);
        when(mMockBackoff.nextBackoff()).thenReturn(0L);
        mRunBackgroundThreadSpy.set(true);

        when(mMockMqttClient.isConnected())
                .thenReturn(true)   // First attempt to connect
                .thenReturn(true)   // doConnect loop first run (ends with exception)
                .thenReturn(true)   // Second attempt to connect
                .thenReturn(true)   // doConnect loop second run (succeeds, so loop continues)
                .thenReturn(false); // doConnect loop third run. Stop the loop

        // First execution gets exception
        doThrow(new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED))
                .when(mMockMqttClient)
                .publish(eq(TOPIC + TOPIC), eq(DATA), eq(QOS), any(Boolean.class));
        mTestIotCoreClient.reconnectLoop();

        // Second execution succeeds
        doNothing()
                .when(mMockMqttClient)
                .publish(eq(TOPIC + TOPIC), eq(DATA), eq(QOS), any(Boolean.class));
        mTestIotCoreClient.reconnectLoop();

        verify(mMockMqttClient, times(2))
                .publish(eq(TOPIC + TOPIC), eq(DATA), eq(QOS), any(Boolean.class));
        verify(mMockBackoff).nextBackoff();
    }

    @Test
    public void testPublishDeviceStateSuccess() throws MqttException {
        when(mMockMqttClient.isConnected()).thenReturn(true).thenReturn(true).thenReturn(false);
        mRunBackgroundThreadSpy.set(true);
        mUnsentDeviceStateSpy.set(DATA);

        mTestIotCoreClient.reconnectLoop();

        verify(mMockMqttClient).publish(eq(TOPIC), eq(DATA), any(Integer.class), any(Boolean.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testBuilderAllParameters() {
        // Throws exception on error
        new IotCoreClient.Builder()
                .setConnectionParams(mMockConnectionParams)
                .setKeyPair(mKeyPair)
                .setTelemetryQueue(mMockTelemetryQueue)
                .setConnectionCallback(mMockConnectionCallbackExecutor, mMockConnectionCallback)
                .setOnConfigurationListener(mMockOnConfigurationExecutor,
                        mMockOnConfigurationListener)
                .setOnCommandListener(mMockOnCommandExecutor, mMockOnCommandListener)
                .build();
    }

    @Test
    public void testBuilderRequiredParameters() {
        // Throws exception on error
        new IotCoreClient.Builder()
                .setConnectionParams(mMockConnectionParams)
                .setKeyPair(mKeyPair)
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testBuilderFailsWithoutConnectionParams() {
        try {
            new IotCoreClient.Builder()
                    .setKeyPair(mKeyPair)
                    .setTelemetryQueue(mMockTelemetryQueue)
                    .setOnConfigurationListener(mMockOnConfigurationListener)
                    .setConnectionCallback(mMockConnectionCallback)
                    .build();
            fail("Built IotCoreClient without an ConnectionParams");
        } catch (NullPointerException expected) {
            // Success
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testBuilderFailsWithoutKeyPair() {
        try {
            new IotCoreClient.Builder()
                    .setConnectionParams(mMockConnectionParams)
                    .setTelemetryQueue(mMockTelemetryQueue)
                    .setOnConfigurationListener(mMockOnConfigurationListener)
                    .setOnCommandListener(mMockOnCommandListener)
                    .setConnectionCallback(mMockConnectionCallback)
                    .build();
            fail("Built IotCoreClient without a key pair");
        } catch (NullPointerException expected) {
            // Success
        }
    }

    @Test
    public void testBuilderDefaultExecutors() {
        // Throws exception on error
        new IotCoreClient.Builder()
                .setConnectionParams(mMockConnectionParams)
                .setKeyPair(mKeyPair)
                .setConnectionCallback(mMockConnectionCallback)
                .setOnConfigurationListener(mMockOnConfigurationListener)
                .setOnCommandListener(mMockOnCommandListener)
                .build();
    }

    @Test
    public void testBackoffOnRetryableError() throws MqttException {
        when(mMockBackoff.nextBackoff()).thenReturn(0L);
        mRunBackgroundThreadSpy.set(true);

        doThrow(new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED))
                .when(mMockMqttClient)
                .connect(any(MqttConnectOptions.class));

        mTestIotCoreClient.reconnectLoop();

        verify(mMockBackoff).nextBackoff();
    }

    @Test
    public void testNoBackoffOnUnrecoverableError() throws MqttException {
        doThrow(new MqttException(MqttException.REASON_CODE_FAILED_AUTHENTICATION))
                .when(mMockMqttClient)
                .publish(eq(TOPIC + TOPIC), eq(DATA), eq(QOS), any(Boolean.class));

        mTestIotCoreClient.reconnectLoop();

        verify(mMockBackoff, never()).nextBackoff();
    }

    @Test
    public void testClientNotifiedOnUnrecoverableError() throws JoseException, MqttException {
        setUpWithSerialExecutor();
        doThrow(new MqttException(MqttException.REASON_CODE_NOT_AUTHORIZED))
                .when(mMockMqttClient)
                .connect(any(MqttConnectOptions.class));

        mTestIotCoreClient.reconnectLoop();

        verify(mMockConnectionCallback).onDisconnected(ConnectionCallback.REASON_NOT_AUTHORIZED);
    }

    @Test
    public void testTopicPathConcatenation() {
        ConnectionParams connectionParams = new ConnectionParams.Builder()
                .setProjectId("project")
                .setRegistry("registry", "region")
                .setDeviceId("device")
                .build();
        TopicEvent telemetryMessage =
                new TopicEvent(new byte[1], "abc", TopicEvent.QOS_AT_LEAST_ONCE);

        assertThat(connectionParams.getTelemetryTopic() + telemetryMessage.getTopicSubpath())
                .isEqualTo("/devices/device/events/abc");
    }
}
