#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
readonly DEFAULT_STORE_FILE="/Users/sviat/Documents/OTPulse-signing/otpulse-upload.jks"
readonly DEFAULT_JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
readonly DEFAULT_APKSIGNER="/Users/sviat/Library/Android/sdk/build-tools/35.0.0/apksigner"

store_file="${OTPULSE_UPLOAD_STORE_FILE:-$DEFAULT_STORE_FILE}"
key_alias="${OTPULSE_UPLOAD_KEY_ALIAS:-otpulse-upload}"
java_home="${JAVA_HOME:-$DEFAULT_JAVA_HOME}"
bundle_file="$PROJECT_DIR/composeApp/build/outputs/bundle/release/composeApp-release.aab"
apk_file="$PROJECT_DIR/composeApp/build/outputs/apk/release/composeApp-release.apk"
apksigner="${ANDROID_APKSIGNER:-$DEFAULT_APKSIGNER}"

cleanup() {
    unset OTPULSE_UPLOAD_STORE_PASSWORD OTPULSE_UPLOAD_KEY_PASSWORD
}
trap cleanup EXIT

fail() {
    printf 'Error: %s\n' "$1" >&2
    exit 1
}

[[ -t 0 ]] || fail "run this script in an interactive terminal so passwords cannot be piped or logged"
[[ -f "$store_file" ]] || fail "upload keystore not found: $store_file"
[[ -x "$java_home/bin/jarsigner" ]] || fail "jarsigner not found under JAVA_HOME: $java_home"
[[ -x "$apksigner" ]] || fail "apksigner not found: $apksigner"
[[ -x "$PROJECT_DIR/gradlew" ]] || fail "Gradle wrapper not found: $PROJECT_DIR/gradlew"

if [[ "$(uname -s)" == "Darwin" ]]; then
    store_mode="$(stat -f '%Lp' "$store_file")"
else
    store_mode="$(stat -c '%a' "$store_file")"
fi
[[ "$store_mode" == "600" ]] || fail "keystore permissions must be 600, found $store_mode"

printf 'Keystore: %s\n' "$store_file"
printf 'Alias: %s\n' "$key_alias"
printf 'Release bundle: %s\n' "$bundle_file"
printf 'Sideload APK: %s\n' "$apk_file"

read -r -s -p 'Keystore password: ' OTPULSE_UPLOAD_STORE_PASSWORD
printf '\n'
[[ -n "$OTPULSE_UPLOAD_STORE_PASSWORD" ]] || fail "keystore password cannot be empty"
export OTPULSE_UPLOAD_STORE_PASSWORD

read -r -p 'Use the same password for the key? [Y/n] ' same_password
case "$same_password" in
    '' | [Yy] | [Yy][Ee][Ss])
        OTPULSE_UPLOAD_KEY_PASSWORD="$OTPULSE_UPLOAD_STORE_PASSWORD"
        ;;
    *)
        read -r -s -p 'Key password: ' OTPULSE_UPLOAD_KEY_PASSWORD
        printf '\n'
        [[ -n "$OTPULSE_UPLOAD_KEY_PASSWORD" ]] || fail "key password cannot be empty"
        ;;
esac
export OTPULSE_UPLOAD_KEY_PASSWORD
export OTPULSE_UPLOAD_STORE_FILE="$store_file"
export OTPULSE_UPLOAD_KEY_ALIAS="$key_alias"
export JAVA_HOME="$java_home"

cd "$PROJECT_DIR"
./gradlew \
    :composeApp:allTests \
    :composeApp:lintRelease \
    :composeApp:assembleRelease \
    :composeApp:bundleRelease

[[ -f "$bundle_file" ]] || fail "Gradle completed without producing the release bundle"
[[ -f "$apk_file" ]] || fail "Gradle completed without producing the release APK"

"$java_home/bin/jarsigner" -verify "$bundle_file"
"$apksigner" verify --verbose --print-certs "$apk_file"

printf '\nSigned Google Play artifacts are ready:\n%s\n%s\n' "$bundle_file" "$apk_file"
