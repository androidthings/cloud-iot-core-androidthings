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
import android.text.TextUtils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Stores information necessary to connect a single device to Google Cloud IoT Core: the device's
 * <a href="https://cloud.google.com/iot/docs/how-tos/mqtt-bridge#device_authentication">client ID
 * </a> and some MQTT configuration settings.
 *
 * <p>Specifying the device's client ID requires the full specification for the Cloud IoT Core
 * registry in which the device is registered, which includes the device's Google Cloud project ID,
 * the device's Cloud IoT Core registry ID, the registry's cloud region, and the device's ID within
 * the registry. These parameters are set in the {@link ConnectionParams.Builder}.
 *
 * <p>MQTT configuration settings are not required since ConnectionParams can use sensible
 * defaults, but users can control these settings using
 * {@link ConnectionParams.Builder#setBridgeHostname(String)},
 * {@link ConnectionParams.Builder#setBridgePort(int)}, and
 * {@link ConnectionParams.Builder#setAuthTokenLifetime(long, TimeUnit)}.
 */
public class ConnectionParams {
    private static final String DEFAULT_BRIDGE_HOSTNAME = "mqtt.googleapis.com";
    private static final int DEFAULT_BRIDGE_PORT = 8883;
    private static final int MAX_TCP_PORT = 65535;
    private static final long DEFAULT_AUTH_TOKEN_LIFETIME_MILLIS = Duration.ofHours(1).toMillis();

    // GCP cloud project name.
    private final String mProjectId;

    // Cloud IoT registry id.
    private final String mRegistryId;

    // Cloud IoT device id.
    private final String mDeviceId;

    // GCP cloud region.
    private final String mCloudRegion;

    // MQTT bridge hostname.
    private final String mBridgeHostname;

    // MQTT bridge port.
    private final int mBridgePort;

    // The duration that JWT authentication tokens for connecting to Cloud IoT Core should
    // be valid.
    private final long mAuthTokenLifetimeMillis;

    // Cached Cloud IoT Core client id.
    private final String mClientId;

    // Cached Cloud IoT Core telemetry topic.
    private final String mTelemetryTopic;

    // Cached Cloud IoT Core device state topic.
    private final String mDeviceStateTopic;

    // Cached Cloud IoT Core device configuration topic.
    private final String mConfigurationTopic;

    // Cached Cloud IoT Core device commands topic.
    private final String mCommandsTopicPrefix;

    // Cached broker URL.
    private final String mBrokerUrl;

    /** Builder for ConnectionParams instances. */
    public static class Builder {
        private String mProjectId;
        private String mRegistryId;
        private String mDeviceId;
        private String mCloudRegion;
        private String mBridgeHostname = DEFAULT_BRIDGE_HOSTNAME;
        private int mBridgePort = DEFAULT_BRIDGE_PORT;
        private long mAuthTokenLifetimeMillis = DEFAULT_AUTH_TOKEN_LIFETIME_MILLIS;

        /**
         * Set the Google Cloud project ID.
         *
         * <p>This parameter is required.
         *
         * @param projectId the Google Cloud project ID for these connection parameters
         * @return this builder
         */
        public Builder setProjectId(@NonNull String projectId) {
            if (TextUtils.isEmpty(projectId)) {
                throw new IllegalArgumentException("Project ID cannot be empty");
            }
            mProjectId = projectId;
            return this;
        }

        /**
         * Set the Cloud IoT Core registry.
         *
         * <p>This parameter is required.
         *
         * @param registryId the registry's ID
         * @param cloudRegion the registry's cloud region
         * @return this builder
         */
        public Builder setRegistry(@NonNull String registryId, @NonNull String cloudRegion) {
            if (TextUtils.isEmpty(registryId)) {
                throw new IllegalArgumentException("Registry ID cannot be empty");
            }
            if (TextUtils.isEmpty(cloudRegion)) {
                throw new IllegalArgumentException("Cloud Region cannot be empty");
            }

            mRegistryId = registryId;
            mCloudRegion = cloudRegion;
            return this;
        }

        /**
         * Set the device ID used to register this device in Cloud IoT Core.
         *
         * <p>This parameter is required.
         *
         * @param deviceId the Cloud IoT Core device ID to use for these connection parameters
         * @return this builder
         */
        public Builder setDeviceId(@NonNull String deviceId) {
            if (TextUtils.isEmpty(deviceId)) {
                throw new IllegalArgumentException("Device ID cannot be empty");
            }
            mDeviceId = deviceId;
            return this;
        }

        /**
         * Set the MQTT bridge hostname to use when connecting to Cloud IoT Core.
         *
         * <p>This parameter is optional. If no MQTT bridge hostname is specified, the Google MQTT
         * bridge, mqtt.googleapis.com, is used by default.
         *
         * @param bridgeHostname the MQTT bridge hostname to use for these connection parameters
         * @return this builder
         */
        public Builder setBridgeHostname(@NonNull String bridgeHostname) {
            if (TextUtils.isEmpty(bridgeHostname)) {
                throw new IllegalArgumentException("Bridge hostname cannot be empty");
            }
            mBridgeHostname = bridgeHostname;
            return this;
        }

        /**
         * Set the MQTT bridge port number to use when connecting to Cloud IoT Core.
         *
         * <p>This parameter is optional. If no MQTT bridge port number is specified, port 8883 is
         * used by default.
         *
         * <p>If port 8883 is blocked by your firewall, you also can use port 443 to
         * connect to the default MQTT bridge, mqtt.googleapis.com.
         *
         * @param bridgePort the MQTT bridge port number to use in these connection parameters
         * @return this builder
         */
        public Builder setBridgePort(int bridgePort) {
            if (bridgePort <= 0) {
                throw new IllegalArgumentException("Port must be > 0");
            }
            if (bridgePort > MAX_TCP_PORT) {
                throw new IllegalArgumentException("Port must be < " + (MAX_TCP_PORT + 1));
            }

            mBridgePort = bridgePort;
            return this;
        }

        /**
         * Set the amount of time authentication tokens used for connecting to Cloud IoT
         * Core remain valid.
         *
         * <p>Cloud IoT Core authenticates devices using JSON web tokens (JWTs) that devices
         * send when they initiate a connection with Cloud IoT Core. Those tokens are
         * valid for a limited period of time before they expire. Cloud IoT Core allows
         * a maximum lifetime of 24 hours. The Cloud IoT Core <a
         * href="https://cloud.google.com/iot/docs/how-tos/credentials/jwts">documentation</a> has
         * more information.
         *
         * <p>This parameter is optional. If no authentication token lifetime is specified, the
         * default lifetime is one hour.
         *
         * @param duration duration before explicitly refreshing authorization credential
         * @param unit the time units for the duration
         * @return this builder
         */
        public Builder setAuthTokenLifetime(long duration, @NonNull TimeUnit unit) {
            if (duration <= 0) {
                throw new IllegalArgumentException("Auth token lifetime must be > 0");
            }
            if (unit == null) {
                throw new NullPointerException("Time unit cannot be null");
            }

            long durationMillis = TimeUnit.MILLISECONDS.convert(duration, unit);
            if (durationMillis > Duration.ofHours(24).toMillis()) {
                throw new IllegalArgumentException("Token lifetime cannot exceed 24 hours");
            }

            mAuthTokenLifetimeMillis = durationMillis;
            return this;
        }

        /**
         * Construct a new ConnectionParams instance with the parameters given to this Builder.
         *
         * @return a new ConnectionParams instance
         * @throws IllegalStateException if this builder's parameters are invalid
         */
        public ConnectionParams build() {
            ConnectionParams connectionParams =
                    new ConnectionParams(
                            mProjectId,
                            mRegistryId,
                            mDeviceId,
                            mCloudRegion,
                            mBridgeHostname,
                            mBridgePort,
                            mAuthTokenLifetimeMillis);

            if (!connectionParams.isValid()) {
                throw new IllegalStateException("Invalid ConnectionParams parameters.");
            }
            return connectionParams;
        }
    }

    /**
     * Construct a ConnectionParams instance. Can only be called from a
     * ConnectionParams.Builder instance.
     *
     * @param projectId Google Cloud Project ID
     * @param registryId IoT Core device registry ID
     * @param deviceId IoT Core device ID
     * @param cloudRegion the device registry's cloud region
     * @param bridgeHostname MQTT bridge hostname for the MQTT broker
     * @param bridgePort MQTT bridge port number
     * @param authTokenLifetimeMillis duration in milliseconds before explicitly refreshing
     *         authorization credential
     */
    private ConnectionParams(
            @NonNull String projectId,
            @NonNull String registryId,
            @NonNull String deviceId,
            @NonNull String cloudRegion,
            @NonNull String bridgeHostname,
            int bridgePort,
            long authTokenLifetimeMillis) {
        mProjectId = projectId;
        mRegistryId = registryId;
        mDeviceId = deviceId;
        mCloudRegion = cloudRegion;
        mBridgeHostname = bridgeHostname;
        mBridgePort = bridgePort;
        mAuthTokenLifetimeMillis = authTokenLifetimeMillis;

        mBrokerUrl = "ssl://" + mBridgeHostname + ":" + mBridgePort;
        mClientId = "projects/"
                + mProjectId
                + "/locations/"
                + mCloudRegion
                + "/registries/"
                + mRegistryId
                + "/devices/"
                + mDeviceId;
        mTelemetryTopic = "/devices/" + mDeviceId + "/events";
        mDeviceStateTopic = "/devices/" + mDeviceId + "/state";
        mConfigurationTopic = "/devices/" + mDeviceId + "/config";
        mCommandsTopicPrefix = "/devices/" + mDeviceId + "/commands";
    }

    private boolean isValid() {
        return !TextUtils.isEmpty(mProjectId)
                && !TextUtils.isEmpty(mRegistryId)
                && !TextUtils.isEmpty(mCloudRegion)
                && !TextUtils.isEmpty(mDeviceId)
                && !TextUtils.isEmpty(mCloudRegion)
                && !TextUtils.isEmpty(mBridgeHostname);
    }

    /**
     * Returns the project ID specified by these connection parameters.
     *
     * @return the project ID
     */
    public String getProjectId() {
        return mProjectId;
    }

    /**
     * Returns the registry ID specified by these connection parameters.
     *
     * @return the registry ID
     */
    public String getRegistryId() {
        return mRegistryId;
    }

    /**
     * Returns the device ID specified by these connection parameters.
     *
     * @return the device ID
     */
    public String getDeviceId() {
        return mDeviceId;
    }

    /**
     * Returns the Cloud Region specified by these connection parameters.
     *
     * @return the cloud region
     */
    public String getCloudRegion() {
        return mCloudRegion;
    }

    /**
     * Returns the MQTT bridge hostname specified by these connection parameters.
     *
     * @return the bridge hostname
     */
    public String getBridgeHostname() {
        return mBridgeHostname;
    }

    /**
     * Returns the MQTT bridge port number specified by these connection parameters.
     *
     * @return the bridge port number
     */
    public int getBridgePort() {
        return mBridgePort;
    }

    /**
     * Returns the MQTT broker URL formatted as "ssl://&lt;hostname&gt;:&lt;port&gt;".
     *
     * @return the broker URL
     */
    public String getBrokerUrl() {
        return mBrokerUrl;
    }

    /**
     * Returns the authentication token lifetime in milliseconds specified by these connection
     * parameters.
     *
     * @return the authentication token lifetime in milliseconds
     */
    public long getAuthTokenLifetimeMillis() {
        return mAuthTokenLifetimeMillis;
    }

    /**
     * Returns the full path used to identify this device as defined in <a
     * href="https://cloud.google.com/iot/docs/concepts/devices#devices">the device documentation
     * </a>.
     *
     * @return the Cloud IoT Core client ID specified by these connection parameters
     */
    public String getClientId() {
        return mClientId;
    }

    /**
     * Returns the telemetry topic for this device formatted according to <a
     * href="https://cloud.google.com/iot/docs/protocol_bridge_guide#telemetry_events">the telemetry
     * documentation</a>.
     *
     * <p>Strings returned from this method do not end with a trailing slash.
     *
     * @return the telemetry topic specified by these connection parameters
     */
    public String getTelemetryTopic() {
        return mTelemetryTopic;
    }

    /**
     * Returns the device state topic for this device formatted according to <a
     * href="https://cloud.google.com/iot/docs/how-tos/config/getting-state#reporting_device_state">
     * the device state documentation</a>.
     *
     * <p>Strings returned from this method do not end with a trailing slash.
     *
     * @return the device state topic specified by these connection parameters
     */
    public String getDeviceStateTopic() {
        return mDeviceStateTopic;
    }

    /**
     * Returns the device configuration topic for this device formatted according to <a
     * href="https://cloud.google.com/iot/docs/how-tos/config/configuring-devices#protocol_differences">
     * the configuration documentation</a>.
     *
     * <p>Strings returned from this method do not end with a trailing slash.
     *
     * @return the device configuration topic specified by these connection parameters
     */
    public String getConfigurationTopic() {
        return mConfigurationTopic;
    }

    /**
     * Returns the commands topic for this device.
     *
     * <p>Strings returned from this method do not end with a trailing slash.
     *
     * @return the device commands topic specified by these connection parameters
     */
    public String getCommandsTopicPrefix() {
        return mCommandsTopicPrefix;
    }
}
