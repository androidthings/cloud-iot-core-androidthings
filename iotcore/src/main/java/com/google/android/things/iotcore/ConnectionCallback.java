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

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Callback interface to receive information about the status of the Cloud IoT Core connection.
 */
public abstract class ConnectionCallback {

    /** Disconnect reason codes. */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            REASON_UNKNOWN,
            REASON_NOT_AUTHORIZED,
            REASON_CONNECTION_LOST,
            REASON_CONNECTION_TIMEOUT,
            REASON_CLIENT_CLOSED,
    })
    public @interface DisconnectReason {}

    /** Could not determine the source of the error. */
    public static final int REASON_UNKNOWN = 0;

    /** The parameters used to connect to Cloud IoT Core were invalid. */
    public static final int REASON_NOT_AUTHORIZED = 1;

    /** The device lost connection to Cloud IoT Core. */
    public static final int REASON_CONNECTION_LOST = 2;

    /** Timeout occurred while connecting to the MQTT bridge. */
    public static final int REASON_CONNECTION_TIMEOUT = 3;

    /** The client closed the connection. */
    public static final int REASON_CLIENT_CLOSED = 4;

    /** Invoked when the Cloud IoT Core connection is established. */
    public abstract void onConnected();

    /**
     * Invoked when the Cloud IoT Core connection is lost.
     *
     * @param reason the reason the connection was lost
     */
    public abstract void onDisconnected(@DisconnectReason int reason);
}
