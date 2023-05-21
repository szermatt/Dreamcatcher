# Dreamcatcher

[![test](https://github.com/szermatt/Dreamcatcher/actions/workflows/generate-apk-aab-debug-release.yml/badge.svg)](https://github.com/szermatt/Dreamcatcher/actions/workflows/generate-apk-aab-debug-release.yml)

Dreamcatcher is an Android TV app for powering off a media device
using a Logitech Harmony Hub when the media device is left unused.

When you use a Logitech Harmony Hub to turn everything on and off
again, you get into a situation where an Android TV media device left
unused might very well turn itself off, but other devices might not,
such as a TV or a loudspeaker system, or they might turn themselves
off on a different schedule. 

## COMPATIBILITY

This app requires an Android TV device running Android 11 (API level
30)

## PROTOCOL

Communication with the Logitech Harmony Hub uses the XMPP interface
protocol, described on
https://github.com/hdurdle/harmony/blob/master/PROTOCOL.md using code
adapted from https://github.com/tuck182/harmony-java-client.

## INSTALLATION

You'll find the APK to install on [the releases page](https://github.com/szermatt/Dreamcatcher/releases). 
Download `app-release-unsigned.apk` from the assets of the last release on that page.

## USAGE 

1. Enable XMPP or your Harmony Hub. On the Harmony app, go to Menu >
   Harmony Setup > Add/Edit Devices & Activities > Remote & Hub >
   Enable XMPP.
2. Start Dreamcatcher on your Android TV device
3. Choose the power off delay
4. Choose the Harmony Hub you want to connect to. If no Hub is
   available, make sure that your hub is on and on the same local
   network as your Android TV device
5. Select `Test Connection` to try and connect to the hub. If
   connection doesn't work, make sure you've enabled XMPP on your hub.
6. Select `Enabled` 

While enabled, Dreamcatcher keeps a service running on your Android TV
device that detects when the device enters the Daydream and sends a
power off command to the Harmony Hub after the power off delay you've
chosen.

## COMPILATION

1. Install Java JDK 11
2. Checkout the repository
3. Launch gradle

```bash
git clone https://github.com/szermatt/Dreamcatcher.git
cd Dreamcatcher
./gradlew build
```

## CONTRIBUTING

To report bugs, features or even to ask questions, please open an [issue](https://github.com/szermatt/Dreamcatcher/issues). To contribute code or documentation, please open a [pull request](https://github.com/szermatt/Dreamcatcher/pulls). 

See [CONTRIBUTING.md](CONTRIBUTING.md) for more details. 
