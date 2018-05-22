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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.security.KeyPair;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** IotCoreConfiguration unit tests. */
@RunWith(RobolectricTestRunner.class)
public class IotCoreConfigurationTest {

    private static final String PROJECT_ID = "project";
    private static final String REGISTRY_ID = "registry";
    private static final String DEVICE_ID = "device";
    private static final String CLOUD_REGION = "cloud_region";
    private static final String BRIDGE_HOSTNAME = "hostname";
    private static final short BRIDGE_PORT = 1;
    private static final long AUTH_TOKEN_LIFETIME_DURATION = 1L;
    private static final TimeUnit AUTH_TOKEN_LIFETIME_UNITS = TimeUnit.HOURS;

    private IotCoreConfiguration buildAllParameters() {
        return new IotCoreConfiguration.Builder()
                .setProjectId(PROJECT_ID)
                .setRegistry(REGISTRY_ID, CLOUD_REGION)
                .setDeviceId(DEVICE_ID)
                .setBridgeHostname(BRIDGE_HOSTNAME)
                .setBridgePort(BRIDGE_PORT)
                .setAuthTokenLifetime(AUTH_TOKEN_LIFETIME_DURATION, AUTH_TOKEN_LIFETIME_UNITS)
                .build();
    }

    private IotCoreConfiguration buildRequiredParameters() {
        return new IotCoreConfiguration.Builder()
                .setProjectId(PROJECT_ID)
                .setRegistry(REGISTRY_ID, CLOUD_REGION)
                .setDeviceId(DEVICE_ID)
                .build();
    }

    @Test
    public void testBuilderAllParameters() {
        assertThat(buildAllParameters()).isNotNull();
    }

    @Test
    public void testBuilderRequiredParameters() {
        assertThat(buildRequiredParameters()).isNotNull();
    }

    @Test
    public void testBuilderMissingProjectId() {
        try {
            new IotCoreConfiguration.Builder()
                    .setRegistry(REGISTRY_ID, CLOUD_REGION)
                    .setDeviceId(DEVICE_ID)
                    .build();
            fail("IotCoreConfiguration constructed without Project ID");
        } catch (IllegalStateException expected) {
            assertThat(expected).hasMessageThat().contains("IotCoreConfiguration");
        }
    }

    @Test
    public void testBuilderMissingRegistryId() {
        try {
            new IotCoreConfiguration.Builder()
                    .setProjectId(PROJECT_ID)
                    .setDeviceId(DEVICE_ID)
                    .build();
            fail("IotCoreConfiguration constructed without Registry ID");
        } catch (IllegalStateException expected) {
            assertThat(expected).hasMessageThat().contains("IotCoreConfiguration");
        }
    }

    @Test
    public void testBuilderMissingDeviceId() {
        try {
            new IotCoreConfiguration.Builder()
                    .setProjectId(PROJECT_ID)
                    .setRegistry(REGISTRY_ID, CLOUD_REGION)
                    .build();
            fail("IotCoreConfiguration constructed without Device ID");
        } catch (IllegalStateException expected) {
            assertThat(expected).hasMessageThat().contains("IotCoreConfiguration");
        }
    }

    @Test
    public void testBuilderNegativeBridgePort() {
        try {
            new IotCoreConfiguration.Builder()
                    .setProjectId(PROJECT_ID)
                    .setRegistry(REGISTRY_ID, CLOUD_REGION)
                    .setDeviceId(DEVICE_ID)
                    .setBridgePort((short) -100)
                    .build();
            fail("IotCoreConfiguration constructed with negative MQTT bridge port");
        } catch (IllegalArgumentException expected) {
            assertThat(expected).hasMessageThat().contains("Port");
        }
    }

    @Test
    public void testBuilderInvalidAuthTokenLifetime() {
        try {
            new IotCoreConfiguration.Builder()
                    .setProjectId(PROJECT_ID)
                    .setRegistry(REGISTRY_ID, CLOUD_REGION)
                    .setDeviceId(DEVICE_ID)
                    .setAuthTokenLifetime(Duration.ofDays(1).toMillis() + 1, TimeUnit.MILLISECONDS)
                    .build();
            fail("IotCoreConfiguration constructed with auth token lifetime greater than 24 hours");
        } catch (IllegalArgumentException expected) {
            assertThat(expected).hasMessageThat().contains("Token");
        }
    }

    @Test
    public void testGetProjectId() {
        IotCoreConfiguration configuration = buildAllParameters();
        assertThat(configuration.getProjectId()).isEqualTo(PROJECT_ID);
    }

    @Test
    public void testGetRegistryId() {
        IotCoreConfiguration configuration = buildAllParameters();
        assertThat(configuration.getRegistryId()).isEqualTo(REGISTRY_ID);
    }

    @Test
    public void testGetDeviceId() {
        IotCoreConfiguration configuration = buildAllParameters();
        assertThat(configuration.getDeviceId()).isEqualTo(DEVICE_ID);
    }

    @Test
    public void testGetCloudRegion() {
        IotCoreConfiguration configuration = buildAllParameters();
        assertThat(configuration.getCloudRegion()).isEqualTo(CLOUD_REGION);
    }

    @Test
    public void testGetBridgeHostname() {
        IotCoreConfiguration configuration = buildAllParameters();
        assertThat(configuration.getBridgeHostname()).isEqualTo(BRIDGE_HOSTNAME);
    }

    @Test
    public void testGetBridgeHostnameDefault() {
        IotCoreConfiguration configuration = buildRequiredParameters();
        assertThat(configuration.getBridgeHostname()).isEqualTo("mqtt.googleapis.com");
    }

    @Test
    public void testGetBridgePort() {
        IotCoreConfiguration configuration = buildAllParameters();
        assertThat(configuration.getBridgePort()).isEqualTo(BRIDGE_PORT);
    }

    @Test
    public void testGetBridgePortDefault() {
        IotCoreConfiguration configuration = buildRequiredParameters();
        assertThat(configuration.getBridgePort()).isEqualTo(8883);
    }

    @Test
    public void testGetAuthTokenLifetime() {
        IotCoreConfiguration configuration = buildAllParameters();
        assertThat(configuration.getAuthTokenLifetimeMillis())
                .isEqualTo(TimeUnit.MILLISECONDS
                        .convert(AUTH_TOKEN_LIFETIME_DURATION, AUTH_TOKEN_LIFETIME_UNITS));
    }

    @Test
    public void testGetAuthTokenLifetimeDefault() {
        IotCoreConfiguration configuration = buildRequiredParameters();
        assertThat(configuration.getAuthTokenLifetimeMillis())
                .isEqualTo(Duration.ofHours(1).toMillis());
    }

    @Test
    public void testGetBrokerUrl() {
        IotCoreConfiguration configuration = buildAllParameters();

        String expectedBrokerUrl = "ssl://" + BRIDGE_HOSTNAME + ":" + BRIDGE_PORT;
        assertThat(configuration.getBrokerUrl()).isEqualTo(expectedBrokerUrl);
    }

    @Test
    public void testGetClientId() {
        IotCoreConfiguration configuration = buildAllParameters();

        String expectedClientId = "projects/"
                + PROJECT_ID
                + "/locations/"
                + CLOUD_REGION
                + "/registries/"
                + REGISTRY_ID
                + "/devices/"
                + DEVICE_ID;
        assertThat(configuration.getClientId()).isEqualTo(expectedClientId);
    }

    @Test
    public void testGetTelemetryTopic() {
        IotCoreConfiguration configuration = buildAllParameters();

        String expectedTelemetrytopic = "/devices/" + DEVICE_ID + "/events";
        assertThat(configuration.getTelemetryTopic()).isEqualTo(expectedTelemetrytopic);
    }

    @Test
    public void testGetDeviceStateTopic() {
        IotCoreConfiguration configuration = buildAllParameters();

        String expectedDeviceStateTopic = "/devices/" + DEVICE_ID + "/state";
        assertThat(configuration.getDeviceStateTopic()).isEqualTo(expectedDeviceStateTopic);
    }

    @Test
    public void testGetConfigurationTopic() {
        IotCoreConfiguration configuration = buildAllParameters();

        String expectedConfigurationTopic = "/devices/" + DEVICE_ID + "/config";
        assertThat(configuration.getConfigurationTopic()).isEqualTo(expectedConfigurationTopic);
    }
}
