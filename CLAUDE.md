# Taito NFC

Taito NFC writes a link, email address, phone number or text, typed in the app, to NTAG213, 215
and 216 tags and protects them with the tag's write password. The Android app is in `android/`,
the iOS app in `ios/`.

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
- Build for the simulator: `xcodebuild -project ios/TaitoNFC.xcodeproj -scheme TaitoNFC
  -destination 'generic/platform=iOS Simulator' -derivedDataPath ios/build CODE_SIGNING_ALLOWED=NO build`
- Archive for a device: `xcodebuild -project ios/TaitoNFC.xcodeproj -scheme TaitoNFC -configuration
  Release -destination 'generic/platform=iOS' -archivePath ios/build/TaitoNFC.xcarchive
  -allowProvisioningUpdates archive`
- Build for the user's iPhone: `xcodebuild -project ios/TaitoNFC.xcodeproj -scheme TaitoNFC
  -configuration Debug -destination 'id=<udid>' -derivedDataPath ios/build -allowProvisioningUpdates
  -allowProvisioningDeviceRegistration build`, then install it with `xcrun devicectl device install
  app --device <udid> ios/build/Build/Products/Debug-iphoneos/TaitoNFC.app`, with the UDID of the
  physical iPhone that `xcrun devicectl list devices` lists. The install fails while the phone is
  locked.
- App Store build: `xcodebuild -exportArchive -archivePath ios/build/TaitoNFC.xcarchive -exportPath
  ios/build/export -exportOptionsPlist ios/export-options.plist`, out at
  `ios/build/export/TaitoNFC.ipa`. It signs manually with the certificate "Apple Distribution:
  Taito United Oy" and the App Store profile "Taito NFC App Store", both installed on the Mac,
  because an App Manager of Taito United Oy's team has no access to cloud-managed signing. Raise
  `CFBundleVersion` in `ios/Sources/Info.plist` before each upload. To upload it to App Store
  Connect, export with `destination` set to `upload` in a copy of `ios/export-options.plist` and
  `-allowProvisioningUpdates`; it signs in with the Apple Account in Xcode's settings.
- App Store screenshots: boot the simulator `iPhone 18 Pro`, whose 1206x2622 is the size App Store
  Connect requires, set `xcrun simctl status_bar booted override --time 9:41 --batteryState charged
  --batteryLevel 100`, then run `TEST_RUNNER_DEMO_OUT=<dir> TEST_RUNNER_STORE_SCREENSHOTS=1
  xcodebuild test -project ios/TaitoNFC.xcodeproj -scheme TaitoNFC -destination 'name=iPhone 18 Pro'
  -derivedDataPath ios/build CODE_SIGNING_ALLOWED=NO -testLanguage en`, and again with
  `-testLanguage fi -testRegion FI` for the Finnish ones.

The `-demo` launch argument plays the taps on a pretend tag, as the Android `demo` extra does.
`-store-screenshots` beside it hides the notes that say the tag is pretend.

## Try it without a tag

An emulator has no NFC. The `demo` intent extra plays each tap on a pretend tag that follows the
same rules: `adb shell am start -n fi.taitounited.nfc/.MainActivity --ez demo true`.

## Rules the code follows

- The protection must stay byte for byte what the iOS app writes, so a tag protected on one
  platform unlocks on the other: PWD is bytes 0 to 3 and PACK bytes 4 and 5 of SHA-256 over the
  7-byte UID followed by the passphrase in UTF-8; PROT 0, AUTHLIM 0, AUTH0 00h, written PWD, PACK,
  ACCESS, then AUTH0. `KeyTest` holds values computed with the iOS code; change them only together
  with the iOS app.
- Every tag command goes over one `NfcA` connection. Android's `Ndef` class reconnects the tag on
  many phones, which drops the authentication a protected tag's rewrite needs.
- Both apps write whatever the user types and keep it on the device: a web address with or without
  https://, an email address and a phone number as a URI record, anything else as a UTF-8 text
  record in the phone's language. `Content.kt` and `Content.swift` read the typing the same way, and
  `ContentTest` holds the cases. Protect refuses a tag that does not already hold what is typed.
  A fresh install starts with taitounited.fi in the field, and an x on the right clears the
  content and the passphrase.
- The app's name is Taito NFC in every language. Every sentence on screen has an English and a
  Finnish version: a new one goes into `ios/Sources/fi.lproj/Localizable.strings` and
  `android/app/src/main/res/values-fi/strings.xml` too, and lint fails on a missing Android
  translation. The Finnish keeps the English word tag, inflected as tagin and tagiin, and uses
  salasana for passphrase. The demo UI test finds its controls by accessibility id, so it runs in
  both languages: add `-testLanguage fi` to the test command for the Finnish one.
- Both apps are `fi.taitounited.nfc`, the Android application id and package and the iOS bundle
  id. Taito United Oy publishes them, so the iOS app is signed only with Taito United Oy's Apple
  team, EPATC4S9N2, which `ios/project.yml` sets: an App ID is unique across all teams, and
  signing it with another team, such as a personal one for a device build, registers it there and
  keeps it from Taito United Oy's.
- NXP's NTAG213/215/216 data sheet, rev. 3.2, is the reference for every command and page.
