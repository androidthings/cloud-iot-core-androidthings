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

import org.junit.Test;

import java.util.Iterator;

/** CapacityQueue unit tests. */
public class CapacityQueueTest {

    private static final int DEFAULT_CAPACITY = 10;
    private static final int DEFAULT_DROP_POLICY = CapacityQueue.DROP_POLICY_HEAD;

    @Test
    public void testSizeEmptyQueue() {
        CapacityQueue<Integer> queue = new CapacityQueue<>(DEFAULT_CAPACITY, DEFAULT_DROP_POLICY);
        assertThat(queue.size()).isEqualTo(0);
    }

    @Test
    public void testSizeAfterOffer() {
        CapacityQueue<Integer> queue = new CapacityQueue<>(DEFAULT_CAPACITY, DEFAULT_DROP_POLICY);
        queue.offer(0);
        assertThat(queue.size()).isEqualTo(1);
    }

    @Test
    public void testSizeAtCapacity() {
        CapacityQueue<Integer> queue = new CapacityQueue<>(DEFAULT_CAPACITY, DEFAULT_DROP_POLICY);
        for (int i = 0; i < DEFAULT_CAPACITY; i++) {
            queue.offer(i);
        }

        assertThat(queue.size()).isEqualTo(DEFAULT_CAPACITY);
    }

    @Test
    public void testOfferOne() {
        CapacityQueue<Integer> queue = new CapacityQueue<>(DEFAULT_CAPACITY, DEFAULT_DROP_POLICY);
        assertThat(queue.offer(0)).isTrue();
    }

    @Test
    public void testOfferToCapacityHeadDrop() {
        CapacityQueue<Integer> queue = new CapacityQueue<>(
                DEFAULT_CAPACITY, CapacityQueue.DROP_POLICY_HEAD);
        for (int i = 0; i < DEFAULT_CAPACITY; i++) {
            assertThat(queue.offer(i)).isTrue();
        }
    }

    @Test
    public void testOfferAtCapacityHeadDrop() {
        CapacityQueue<Integer> queue = new CapacityQueue<>(
                DEFAULT_CAPACITY, CapacityQueue.DROP_POLICY_HEAD);
        for (int i = 0; i < DEFAULT_CAPACITY; i++) {
            queue.offer(i);
        }

        // Go over capacity
        assertThat(queue.offer(10)).isTrue();

        // Make sure the head was dropped
        assertThat(queue.peek()).isEqualTo(1);
    }

    @Test
    public void testOfferTailDropToCapacity() {
        CapacityQueue<Integer> queue = new CapacityQueue<>(
                DEFAULT_CAPACITY, CapacityQueue.DROP_POLICY_TAIL);
        for (int i = 0; i < DEFAULT_CAPACITY; i++) {
            assertThat(queue.offer(i)).isTrue();
        }
    }

    @Test
    public void testOfferTailDropAtCapacity() {
        CapacityQueue<Integer> queue = new CapacityQueue<>(
                DEFAULT_CAPACITY, CapacityQueue.DROP_POLICY_TAIL);
        for (int i = 0; i < DEFAULT_CAPACITY; i++) {
            queue.offer(i);
        }

        // Go over capacity
        assertThat(queue.offer(10)).isFalse();
    }

    @Test
    public void testPeekEmptyQueue() {
        CapacityQueue<Integer> queue = new CapacityQueue<>(DEFAULT_CAPACITY, DEFAULT_DROP_POLICY);
        assertThat(queue.peek()).isNull();
    }

    @Test
    public void testPeekAfterOffer() {
        CapacityQueue<Integer> queue = new CapacityQueue<>(DEFAULT_CAPACITY, DEFAULT_DROP_POLICY);
        queue.offer(0);
        assertThat(queue.peek()).isEqualTo(0);
        assertThat(queue.size()).isEqualTo(1);
    }

    @Test
    public void testPollEmptyQueue() {
        CapacityQueue<Integer> queue = new CapacityQueue<>(DEFAULT_CAPACITY, DEFAULT_DROP_POLICY);
        assertThat(queue.poll()).isNull();
    }

    @Test
    public void testPollAfterOffer() {
        CapacityQueue<Integer> queue = new CapacityQueue<>(DEFAULT_CAPACITY, DEFAULT_DROP_POLICY);
        queue.offer(0);
        assertThat(queue.poll()).isEqualTo(0);
        assertThat(queue.size()).isEqualTo(0);
    }

    @Test
    public void testPollAtCapacity() {
        CapacityQueue<Integer> queue = new CapacityQueue<>(DEFAULT_CAPACITY, DEFAULT_DROP_POLICY);
        for (int i = 0; i < DEFAULT_CAPACITY; i++) {
            queue.offer(i);
        }

        for (int i = 0; i < DEFAULT_CAPACITY; i++) {
            assertThat(queue.poll()).isEqualTo(i);
        }

        assertThat(queue.poll()).isNull();
    }

    @Test
    public void testIteratorEmptyQueue() {
        CapacityQueue<Integer> queue = new CapacityQueue<>(DEFAULT_CAPACITY, DEFAULT_DROP_POLICY);
        Iterator<Integer> iterator = queue.iterator();
        assertThat(iterator.hasNext()).isFalse();
    }

    @Test
    public void testIteratorAtCapacity() {
        CapacityQueue<Integer> queue = new CapacityQueue<>(DEFAULT_CAPACITY, DEFAULT_DROP_POLICY);
        for (int i = 0; i < DEFAULT_CAPACITY; i++) {
            queue.offer(i);
        }

        Iterator<Integer> iterator = queue.iterator();
        for (int i = 0; i < DEFAULT_CAPACITY; i++) {
            assertThat(iterator.hasNext()).isTrue();
            assertThat(iterator.next()).isEqualTo(i);
        }

        assertThat(iterator.hasNext()).isFalse();
    }
}
