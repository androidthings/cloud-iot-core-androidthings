# Cloud IoT Core Client for Android Things

The Cloud IoT Core Client makes it simple to integrate Android Things with Cloud IoT Core by
providing abstractions for all of Cloud IoT Core's
[device functions](https://cloud.google.com/iot/docs/concepts/devices):

* Publishing telemetry events
* Publishing device state
* Receiving device configuration from Cloud IoT Core

The library handles all concurrency so clients can use it in the main thread. It also manages
interactions with the Cloud IoT Core
[MQTT bridge](https://cloud.google.com/iot/docs/how-tos/mqtt-bridge) so users don't have to worry
about low-level networking protocols.

> **Note:** The Android Things Console will be turned down for non-commercial
> use on January 5, 2022. For more details, see the
> [FAQ page](https://developer.android.com/things/faq).

## Getting started

There are three steps to using this library: set up Cloud IoT Core, download the library, and then
start using the library to communicate with Cloud IoT Core.

### Set up Cloud IoT Core

Follow the instructions on the Cloud IoT Core website to
[enable the Cloud IoT Core API](https://cloud.google.com/iot/docs/how-tos/getting-started),
[create a device registry](https://cloud.google.com/iot/docs/how-tos/devices#creating_a_device_registry),
and
[register your device](https://cloud.google.com/iot/docs/how-tos/devices#creating_device_key_pairs).

### Add the library as a dependency

Add the following lines to your app's build.gradle:

```groovy
dependencies {
    implementation 'com.google.android.things:cloud-iot-core:1.0.0'
}
```

### Communicate with Cloud IoT Core

After Cloud IoT Core is configured, getting your device connected takes only a few lines of code:

```java
// Load the keys used to register this device
KeyPair keys =  ...

// Configure Cloud IoT Core project information
ConnectionParams connectionParams = new ConnectionParams.Builder()
    .setProjectId("<your Google Cloud project ID>")
    .setRegistry("<your Cloud IoT Core registry ID>", "<your registry's cloud region>")
    .setDeviceId("<the device's ID in the registry>")
    .build();

// Initialize the IoT Core client
IotCoreClient client = new IotCoreClient.Builder()
    .setConnectionParams(connectionParams)
    .setKeyPair(keys)
    .build();

// Connect to Cloud IoT Core
client.connect();

// Start sending data!
client.publishDeviceState("Hello world!\n".getBytes());
```

Check out the Android Things
[Sensorhub sample app][sensorhub]
for an example. The library's full documentation is available on the
[Cloud IoT Core website][javadocs].

## Learn more

* [Android Things](https://developer.android.com/things/)
* [Cloud IoT Core](https://cloud.google.com/iot-core/)
* [Sensorhub Example][sensorhub]
* [Javadocs][javadocs]

## License

Copyright 2018 Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

[sensorhub]: https://github.com/androidthings/sensorhub-cloud-iot
[javadocs]: https://cloud.google.com/iot/docs/reference/android-things/javadoc
