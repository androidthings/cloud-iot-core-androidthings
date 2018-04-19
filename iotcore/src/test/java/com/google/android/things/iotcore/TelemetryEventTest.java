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

/** TelemetryEvent unit tests. */
@RunWith(RobolectricTestRunner.class)
public class TelemetryEventTest {

    @Test
    public void testGetData() {
        byte[] data = new byte[] {1};
        TelemetryEvent event = new TelemetryEvent(data, null, TelemetryEvent.QOS_AT_MOST_ONCE);
        byte[] outData = event.getData();

        assertThat(outData.length).isEqualTo(data.length);
        assertThat(outData[0]).isEqualTo(data[0]);
    }

    @Test
    public void testNullTopicSubpath() {
        TelemetryEvent event = new TelemetryEvent(new byte[1], null,
                TelemetryEvent.QOS_AT_MOST_ONCE);
        assertThat(event.getTopicSubpath()).isEmpty();
    }

    @Test
    public void testNonNullTopicSubpathWithLeadingSlash() {
        String subpath = "/a/b/c";
        TelemetryEvent event = new TelemetryEvent(new byte[1], subpath,
                TelemetryEvent.QOS_AT_MOST_ONCE);
        assertThat(event.getTopicSubpath()).isEqualTo(subpath);
    }

    @Test
    public void testNonNullTopicSubpathWithNoLeadingSlash() {
        String subpath = "a/b/c";
        TelemetryEvent event = new TelemetryEvent(new byte[1], subpath,
                TelemetryEvent.QOS_AT_MOST_ONCE);
        assertThat(event.getTopicSubpath()).isEqualTo("/" + subpath);
    }

    @Test
    public void testGetQosAtMostOnce() {
        TelemetryEvent event = new TelemetryEvent(new byte[1], null,
                TelemetryEvent.QOS_AT_MOST_ONCE);
        assertThat(event.getQos()).isEqualTo(TelemetryEvent.QOS_AT_MOST_ONCE);
    }

    @Test
    public void testGetQosAtLeastOnce() {
        TelemetryEvent event = new TelemetryEvent(new byte[1], null,
                TelemetryEvent.QOS_AT_LEAST_ONCE);
        assertThat(event.getQos()).isEqualTo(TelemetryEvent.QOS_AT_LEAST_ONCE);
    }

    @Test
    public void testNegativeQosFails() {
        try {
            new TelemetryEvent(new byte[1], null, -1);
            fail("Constructed telemetry event with negative QOS");
        } catch (IllegalArgumentException expected) {
            // Success
        }
    }

    @Test
    public void testHighQosFails() {
        try {
            new TelemetryEvent(new byte[1], null, 100);
            fail("Constructed telemetry event with invalid QOS");
        } catch (IllegalArgumentException expected) {
            // Success
        }
    }
}
