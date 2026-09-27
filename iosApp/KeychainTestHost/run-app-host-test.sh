#!/bin/zsh
set -euo pipefail

otpulse_repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
otpulse_framework_dir="$otpulse_repo_root/composeApp/build/bin/iosSimulatorArm64/debugFramework"
otpulse_bridge_archive="/private/tmp/otpulse-skiko-native-bridges.a"
otpulse_derived_data="/private/tmp/otpulse-keychain-derived"
otpulse_device_id="${1:-$(xcrun simctl list devices booted | sed -nE 's/.*\(([0-9A-F-]{36})\).*/\1/p' | head -n 1)}"
if [[ -z "$otpulse_device_id" ]]; then
    print -u2 "Boot an iOS Simulator or pass its UDID as the first argument"
    exit 1
fi

cd "$otpulse_repo_root"
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
    ./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64

otpulse_skiko_klib="$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/org.jetbrains.skiko/skiko-iossimulatorarm64" -name skiko.klib | head -n 1)"
if [[ -z "$otpulse_skiko_klib" ]]; then
    print -u2 "Skiko iOS simulator klib was not found"
    exit 1
fi
unzip -p "$otpulse_skiko_klib" \
    default/targets/ios_simulator_arm64/included/skiko-native-bridges-iosSim-arm64.a \
    > "$otpulse_bridge_archive"

xcodebuild \
    -project iosApp/KeychainTestHost.xcodeproj \
    -scheme OTPulseKeychainTestHost \
    -configuration Debug \
    -sdk iphonesimulator \
    -destination "platform=iOS Simulator,id=$otpulse_device_id" \
    -derivedDataPath "$otpulse_derived_data" \
    CODE_SIGNING_ALLOWED=YES \
    CODE_SIGN_IDENTITY=- \
    ARCHS=arm64 \
    ONLY_ACTIVE_ARCH=YES \
    FRAMEWORK_SEARCH_PATHS="$otpulse_framework_dir" \
    OTHER_LDFLAGS="-framework ComposeApp $otpulse_bridge_archive" \
    build

xcrun simctl install "$otpulse_device_id" \
    "$otpulse_derived_data/Build/Products/Debug-iphonesimulator/OTPulseKeychainTestHost.app"
xcrun simctl launch --terminate-running-process "$otpulse_device_id" com.otpulse.keychain-test-host
sleep 2
otpulse_container="$(xcrun simctl get_app_container "$otpulse_device_id" com.otpulse.keychain-test-host data)"
otpulse_result="$(sed -n '1p' "$otpulse_container/Documents/keychain-self-test.txt")"
print "OTPulse iOS Keychain app-host: $otpulse_result"
[[ "$otpulse_result" == "PASS" ]]
