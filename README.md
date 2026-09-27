# OTPulse

OTPulse is an offline-first TOTP authenticator built with Kotlin and Compose Multiplatform for Android and iOS.

## Features

- RFC 6238 TOTP codes with SHA-1, SHA-256, and SHA-512 support;
- QR-code scanning and manual account setup;
- secrets protected by Android Keystore or iOS Keychain;
- Android Bluetooth HID keyboard support for sending a selected code to a paired computer;
- local JSON backup import and export;
- light, dark, and system themes;
- 13 interface languages;
- manual TimeShift adjustment;
- no OTPulse account, advertising, or application backend.

Android requires API 28 or later because Bluetooth keyboard transport uses the public `BluetoothHidDevice` API. iOS 17.2 or later is the current deployment target. Public iOS APIs do not provide an App-Store-compatible way for an iPhone app to advertise itself as a generic Bluetooth keyboard.

## Build

The Android build requires JDK 21 and a configured Android SDK through `ANDROID_HOME` or `local.properties`.

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :composeApp:desktopTest

JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :composeApp:assembleDebug
```

For iOS, open `iosApp/OTPulseApp.xcodeproj` in Xcode. To run the app in an already booted Simulator:

```sh
iosApp/run-ios-simulator.sh
```

## Tests

Common and desktop tests:

```sh
./gradlew :composeApp:desktopTest
```

Android platform-security tests require a connected device:

```sh
./gradlew :composeApp:connectedDebugAndroidTest
```

iOS Keychain tests require a booted Simulator:

```sh
iosApp/KeychainTestHost/run-app-host-test.sh
```

## Android variants

Google Play uses application ID `com.otpulse.app`. Debug builds use `com.otpulse.app.debug` and can be installed alongside the release build.

## Release bundle

The release build reads upload-signing credentials from environment variables. The keystore and passwords must remain outside the repository.

```sh
export OTPULSE_UPLOAD_STORE_FILE=/absolute/path/to/otpulse-upload.jks
export OTPULSE_UPLOAD_STORE_PASSWORD='...'
export OTPULSE_UPLOAD_KEY_ALIAS='otpulse-upload'
export OTPULSE_UPLOAD_KEY_PASSWORD='...'

./scripts/build-google-play-release.sh
```

## Privacy

- [Privacy policy](https://millersvt.github.io/otpulse/privacy-policy.html)
