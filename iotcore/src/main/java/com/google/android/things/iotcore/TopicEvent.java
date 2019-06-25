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

import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Represents a telemetry event to publish to Cloud IoT Core. */
public class TopicEvent {

    @Nullable
    private final String mTopicName;
    private final String mTopicSubpath;
    private final byte[] mData;
    private final @Qos int mQos;

    /** Quality of service options. */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({QOS_AT_MOST_ONCE, QOS_AT_LEAST_ONCE})
    public @interface Qos {}

    /** At most once delivery. */
    public static final int QOS_AT_MOST_ONCE = 0;

    /** At least once delivery. */
    public static final int QOS_AT_LEAST_ONCE = 1;

    public TopicEvent(@NonNull byte[] data, @Nullable String topicSubpath, @Qos int qos) {
        this(data, null, topicSubpath, qos);
    }

    /**
     * Constructs a new TopicEvent with the data to publish and an
     * optional topic subpath destination.
     *
     * @param data the telemetry event data to send to Cloud IoT Core
     * @param topicName PubSub Topic name, topic name is null when Event is telemetry event
     * @param topicSubpath the subpath under "../device/../events/"
     * @param qos the quality of service to use when sending the message
     */
    public TopicEvent(@NonNull byte[] data, @Nullable String topicName, @Nullable String topicSubpath, @Qos int qos) {
        if (qos != QOS_AT_MOST_ONCE && qos != QOS_AT_LEAST_ONCE) {
            throw new IllegalArgumentException("Invalid quality of service provided.");
        }


        if (TextUtils.isEmpty(topicSubpath)) {
            topicSubpath = "";
        } else if (topicSubpath.charAt(0) != '/') {
            topicSubpath = "/" + topicSubpath;
        }

        mTopicSubpath = topicSubpath;
        mTopicName = topicName;
        mData = data;
        mQos = qos;
    }

    /**
     * Gets this event's data.
     *
     * @return this event's data
     */
    public byte[] getData() {
        return mData;
    }

    /**
     * Gets this event's topic subpath.
     *
     * <p>Non-empty strings returned by this method always begin with a slash (e.g. /foo).
     *
     * @return this event's topic subpath
     */
    public String getTopicSubpath() {
        return mTopicSubpath;
    }

    /**
     * Gets this event's topic name.
     *
     * @return this event's topic name
     */
    @Nullable
    public String getTopicName() {
        return mTopicName;
    }

    /**
     * Gets this event's quality of service settings.
     *
     * @return this event's QOS settings
     */
    public @Qos int getQos() {
        return mQos;
    }
}
