# Tarrat NFC

Writes the Taito United Oy Signal and Telegram sticker pack links to NTAG213, 215 and 216 tags and
protects them with the tag's write password. The Android app is in `android/`, the iOS app in `ios/`.

## Build and test Android

Run from the repository root:

- Unit tests: `android/gradlew -p android testDebugUnitTest`
- Debug APK: `android/gradlew -p android assembleDebug`, out at
  `android/app/build/outputs/apk/debug/app-debug.apk`
- Release bundle: `android/gradlew -p android bundleRelease`, out at
  `android/app/build/outputs/bundle/release/app-release.aab`. It is unsigned unless
  `android/keystore.properties` (never in git) names an upload key with `storeFile`,
  `storePassword`, `keyAlias` and `keyPassword`.
- Lint: `android/gradlew -p android lintDebug`

The toolchain is Temurin 21, the Homebrew Android command-line tools (`ANDROID_HOME`) with
platform 37.1, and the Gradle wrapper.

## Build iOS

`ios/project.yml` is the source of the Xcode project, which git ignores. From the repository root:

- Generate the project: `(cd ios && xcodegen generate)`
- Build for the simulator: `xcodebuild -project ios/TarratNFC.xcodeproj -scheme TarratNFC
  -destination 'generic/platform=iOS Simulator' -derivedDataPath ios/build CODE_SIGNING_ALLOWED=NO build`

The `-demo` launch argument plays the taps on a pretend tag, as the Android `demo` extra does.

## Try it without a tag

An emulator has no NFC. The `demo` intent extra plays each tap on a pretend tag that follows the
same rules: `adb shell am start -n app.tarrat.nfc/.MainActivity --ez demo true`.

## Rules the code follows

- The protection must stay byte for byte what the iOS app writes, so a tag protected on one
  platform unlocks on the other: PWD is bytes 0 to 3 and PACK bytes 4 and 5 of SHA-256 over the
  7-byte UID followed by the passphrase in UTF-8; PROT 0, AUTHLIM 0, AUTH0 00h, written PWD, PACK,
  ACCESS, then AUTH0. `KeyTest` holds values computed with the iOS code; change them only together
  with the iOS app.
- Every tag command goes over one `NfcA` connection. Android's `Ndef` class reconnects the tag on
  many phones, which drops the authentication a protected tag's rewrite needs.
- The pack links are the iOS app's. Protect refuses a tag that does not hold that pack's link.
- NXP's NTAG213/215/216 data sheet, rev. 3.2, is the reference for every command and page.
