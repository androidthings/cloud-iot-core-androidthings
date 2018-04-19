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
import org.mockito.junit.MockitoJUnitRunner;

/** BoundedExponentialBackoff unit tests. */
@RunWith(MockitoJUnitRunner.class)
public class BoundedExponentialBackoffTest {

    private static final long INITIAL_BACKOFF_MS = 1;
    private static final long MAX_BACKOFF_MS = 8;
    private static final int MAX_JITTER_MS = 2;
    private static final double ITERATIONS_TO_MAX = Math.log(MAX_BACKOFF_MS) / Math.log(2);

    @Test
    public void testNextBackOffNoJitterFirstCall() {
        BoundedExponentialBackoff backoff =
                new BoundedExponentialBackoff(INITIAL_BACKOFF_MS, MAX_BACKOFF_MS, 0);
        assertThat(backoff.nextBackoff()).isEqualTo(INITIAL_BACKOFF_MS);
    }

    @Test
    public void testNextBackoffNoJitterMultipleCalls() {
        BoundedExponentialBackoff backoff =
                new BoundedExponentialBackoff(INITIAL_BACKOFF_MS, MAX_BACKOFF_MS, 0);

        // Result should double every time
        assertThat(backoff.nextBackoff()).isEqualTo(1);  // INITIAL_BACKOFF_MS
        assertThat(backoff.nextBackoff()).isEqualTo(2);
        assertThat(backoff.nextBackoff()).isEqualTo(4);
        assertThat(backoff.nextBackoff()).isEqualTo(8);  // MAX_BACKOFF_MS

        // Make sure it stays at max
        assertThat(backoff.nextBackoff()).isEqualTo(MAX_BACKOFF_MS);
    }

    @Test
    public void testResetAfterFirstCall() {
        BoundedExponentialBackoff backoff =
                new BoundedExponentialBackoff(INITIAL_BACKOFF_MS, MAX_BACKOFF_MS, 0);
        backoff.nextBackoff();
        backoff.reset();
        assertThat(backoff.nextBackoff()).isEqualTo(INITIAL_BACKOFF_MS);
    }

    @Test
    public void testResetAfterMax() {
        BoundedExponentialBackoff backoff =
                new BoundedExponentialBackoff(INITIAL_BACKOFF_MS, MAX_BACKOFF_MS, 0);
        for (int i = 0; i < ITERATIONS_TO_MAX; i++) {
            backoff.nextBackoff();
        }

        backoff.reset();
        assertThat(backoff.nextBackoff()).isEqualTo(INITIAL_BACKOFF_MS);
    }

    @Test
    public void testNextBackoffWithJitterFirstCall() {
        BoundedExponentialBackoff backoff =
                new BoundedExponentialBackoff(INITIAL_BACKOFF_MS, MAX_BACKOFF_MS, MAX_JITTER_MS);

        long result = backoff.nextBackoff();
        assertThat(result).isAtLeast(INITIAL_BACKOFF_MS);
        assertThat(result).isAtMost(INITIAL_BACKOFF_MS + (long) MAX_JITTER_MS);
    }

    @Test
    public void testNextBackoffWithJitterMultipleCalls() {
        BoundedExponentialBackoff backoff =
                new BoundedExponentialBackoff(INITIAL_BACKOFF_MS, MAX_BACKOFF_MS, MAX_JITTER_MS);

        long expectedMin = INITIAL_BACKOFF_MS;
        for (int i = 0; i < ITERATIONS_TO_MAX; i++) {
            long result = backoff.nextBackoff();
            assertThat(result).isAtLeast(expectedMin);
            assertThat(result).isAtMost(expectedMin + (long) MAX_JITTER_MS);

            expectedMin <<= 1;
        }
    }

    @Test
    public void testNextBackoffWithJitterStopsAtMax() {
        BoundedExponentialBackoff backoff =
                new BoundedExponentialBackoff(INITIAL_BACKOFF_MS, MAX_BACKOFF_MS, MAX_JITTER_MS);
        for (int i = 0; i < ITERATIONS_TO_MAX; i++) {
            backoff.nextBackoff();
        }

        for (int i = 0; i < 100; i++) {
            long result = backoff.nextBackoff();
            assertThat(result).isAtLeast(MAX_BACKOFF_MS);
            assertThat(result).isAtMost(MAX_BACKOFF_MS + (long) MAX_JITTER_MS);
        }
    }

    @Test
    public void testConstructorNegativeInitialBackoff() {
        try {
            new BoundedExponentialBackoff(-1, MAX_BACKOFF_MS, MAX_JITTER_MS);
            fail("Constructed BoundedExponentialBackoff with negative initial backoff.");
        } catch (IllegalArgumentException expected) {
            // Success
        }
    }

    @Test
    public void testConstructorZeroInitialBackoff() {
        try {
            new BoundedExponentialBackoff(0, MAX_BACKOFF_MS, MAX_JITTER_MS);
        } catch (IllegalArgumentException expected) {
            return;
        }
        fail("Constructed BoundedExponentialBackoff with zero initial backoff.");
    }

    @Test
    public void testConstructorNegativeMaxBackoff() {
        try {
            new BoundedExponentialBackoff(INITIAL_BACKOFF_MS, -1, MAX_JITTER_MS);
            fail("Constructed BoundedExponentialBackoff with negative max backoff.");
        } catch (IllegalArgumentException expected) {
            // Success
        }
    }

    @Test
    public void testConstructorZeroMaxBackoff() {
        try {
            new BoundedExponentialBackoff(INITIAL_BACKOFF_MS, 0, MAX_JITTER_MS);
            fail("Constructed BoundedExponentialBackoff with zero max backoff.");
        } catch (IllegalArgumentException expected) {
            // Success
        }
    }

    @Test
    public void testConstructorNegativeJitter() {
        try {
            new BoundedExponentialBackoff(INITIAL_BACKOFF_MS, MAX_BACKOFF_MS, -1);
            fail("Constructed BoundedExponentialBackoff with negative jitter.");
        } catch (IllegalArgumentException expected) {
            // Success
        }
    }

    @Test
    public void testConstructorZeroJitter() {
        try {
            new BoundedExponentialBackoff(INITIAL_BACKOFF_MS, MAX_BACKOFF_MS, 0);
        } catch (IllegalArgumentException unexpected) {
            fail("Error constructing BoundedExponentialBackoff with zero jitter.");
        }
    }

    @Test
    public void testConstructorValidParams() {
        try {
            new BoundedExponentialBackoff(INITIAL_BACKOFF_MS, MAX_BACKOFF_MS, MAX_JITTER_MS);
        } catch (IllegalArgumentException unexpected) {
            fail("Error constructing BoundedExponentialBackoff with valid parameters.");
        }
    }

    @Test
    public void testConstructorMaxBackoffLessThanMinBackoff() {
        try {
            new BoundedExponentialBackoff(MAX_BACKOFF_MS, INITIAL_BACKOFF_MS, MAX_JITTER_MS);
            fail("Constructed BoundedExponentialBackoff with initial backoff > max backoff.");
        } catch (IllegalArgumentException expected) {
            // Success
        }
    }
}
