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

import java.util.Random;

/**
 * Calculates bounded exponential backoff with jitter.
 */
class BoundedExponentialBackoff {

    private final long mInitialBackoffMillis;
    private final long mMaxBackoffMillis;
    private final int mJitterMillis;
    private final Random mRandom;
    private long mCurrentBackoffDurationMillis;

    /**
     * Construct BoundedExponentialBackoff instance with backoff starting value, the maximum time
     * to backoff, and the maximum amount of jitter, or randomness, to include in the backoff time.
     *
     * @param initialBackoffMillis minimum backoff time in milliseconds
     * @param maxBackoffMillis maximum backoff time in milliseconds
     * @param jitterMillis maximum variation in backoff time in milliseconds.
     */
    BoundedExponentialBackoff(long initialBackoffMillis, long maxBackoffMillis, int jitterMillis) {
        if (initialBackoffMillis <= 0) {
            throw new IllegalArgumentException("Initial backoff time must be > 0");
        }
        if (maxBackoffMillis <= 0) {
            throw new IllegalArgumentException("Maximum backoff time must be > 0");
        }
        if (jitterMillis < 0) {
            throw new IllegalArgumentException("Jitter time must be >= 0");
        }
        if (maxBackoffMillis < initialBackoffMillis) {
            throw new IllegalArgumentException(
                    "Maximum backoff time must be >= initial backoff time");
        }

        mInitialBackoffMillis = initialBackoffMillis;
        mCurrentBackoffDurationMillis = initialBackoffMillis;
        mMaxBackoffMillis = maxBackoffMillis;
        mJitterMillis = jitterMillis;
        mRandom = new Random();
    }

    /** Reset the backoff interval. */
    void reset() {
        mCurrentBackoffDurationMillis = mInitialBackoffMillis;
    }


    /** Return a backoff exponentially larger than the last. */
    long nextBackoff() {
        int jitter = mJitterMillis == 0 ? 0 : mRandom.nextInt(mJitterMillis);
        long backoff = mCurrentBackoffDurationMillis + (long) jitter;

        mCurrentBackoffDurationMillis <<= 1;
        if (mCurrentBackoffDurationMillis > mMaxBackoffMillis) {
            mCurrentBackoffDurationMillis = mMaxBackoffMillis;
        }

        return backoff;
    }
}
