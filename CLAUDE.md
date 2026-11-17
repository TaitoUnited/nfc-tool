# Tag Pen

Tag Pen, NFC Kynä in Finnish, writes a link, email address, phone number or text, typed in the
app, to NTAG213, 215 and 216 tags and protects them with the tag's write password. The Android app is in `android/`, the iOS app in
`ios/`.

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
- Build for the simulator: `xcodebuild -project ios/TagPen.xcodeproj -scheme TagPen
  -destination 'generic/platform=iOS Simulator' -derivedDataPath ios/build CODE_SIGNING_ALLOWED=NO build`

The `-demo` launch argument plays the taps on a pretend tag, as the Android `demo` extra does.

## Try it without a tag

An emulator has no NFC. The `demo` intent extra plays each tap on a pretend tag that follows the
same rules: `adb shell am start -n app.tagpen/.MainActivity --ez demo true`.

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
- The app's name is Tag Pen, and NFC Kynä on a phone set to Finnish. Every sentence on screen has
  an English and a Finnish version: a new one goes into `ios/Sources/fi.lproj/Localizable.strings`
  and `android/app/src/main/res/values-fi/strings.xml` too, and lint fails on a missing Android
  translation. The Finnish keeps the English word tag, inflected as tagin and tagiin, and uses
  salasana for passphrase. The demo UI test finds its controls by accessibility id, so it runs in
  both languages: add `-testLanguage fi` to the test command for the Finnish one. The Android id is `app.tagpen`. The
  iOS bundle id stays `app.tarrat.nfc`, the one the development profile on this Mac covers, until
  the app gets an App ID in the team that publishes it.
- NXP's NTAG213/215/216 data sheet, rev. 3.2, is the reference for every command and page.
