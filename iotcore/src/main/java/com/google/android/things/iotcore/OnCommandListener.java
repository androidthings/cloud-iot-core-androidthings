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

/** Listener interface for receiving device commands pushed by Google Cloud IoT Core. */
public interface OnCommandListener {

    /**
     * Invoked when device command data is received from Cloud IoT Core.
     *
     * @param subFolder the subFolder the command is received on.
     * @param commandData data received from Cloud IoT Core
     */
    void onCommandReceived(String subFolder, byte[] commandData);
}
