# Tarrat NFC

An app that writes the Taito United Oy sticker pack links, for Signal and for Telegram, to NFC
stickers, so a phone held on a sticker opens the pack. Made for the stand at Kumpulan Potentiaali on
26 November 2026.

Per pack it has a button that writes the link and a button that protects the tag with a passphrase,
and one button removes the protection from any tag. Phones still read a protected tag; only
rewriting it needs the passphrase. The protection is the write password of NXP's NTAG213, 215 and
216 chips, derived from the tag's UID and the passphrase, the same on Android and iOS, so a tag
protected on one unlocks on the other.

![The Android app in demo mode after writing the Signal link](docs/screenshots/android-demo.png)
![The iOS app in demo mode](docs/screenshots/ios-demo.png)

## Parts

- `android/`: the Android app, Kotlin and Jetpack Compose, application id `app.tarrat.nfc`.
- `ios/`: the iOS app, SwiftUI with Core NFC, bundle id `app.tarrat.nfc`. Its Xcode project is
  generated from `ios/project.yml` with xcodegen.

## Tags

Use NTAG215 or NTAG216 stickers. The Signal link takes 146 bytes, and an NTAG213 holds 144, so an
NTAG213 fits only the Telegram link. MIFARE Classic tags do not work.

## Try it

On an Android phone with NFC, install the debug build and open Tarrat NFC. Write a link, leave the
app and hold the phone on the tag to test it, then protect the tag.

An emulator has no NFC. Started with the `demo` extra, the app plays each tap on a pretend tag that
follows the same rules:

```bash
adb shell am start -n app.tarrat.nfc/.MainActivity --ez demo true
```

## Build

The Android app needs JDK 17 or newer and the Android SDK with platform 37.1 (`ANDROID_HOME`):

```bash
(cd ~/Projects/tarrat-nfc && android/gradlew -p android testDebugUnitTest assembleDebug bundleRelease)
```

The iOS app needs Xcode and xcodegen. This builds it for the simulator:

```bash
(cd ~/Projects/tarrat-nfc/ios && xcodegen generate && xcodebuild -project TarratNFC.xcodeproj \
  -scheme TarratNFC -destination 'generic/platform=iOS Simulator' -derivedDataPath build \
  CODE_SIGNING_ALLOWED=NO build)
```

In the simulator, the `-demo` launch argument plays the taps on a pretend tag.

## Start with Claude Code

```text
in ~/Projects/tarrat-nfc read CLAUDE.md, build the android and ios apps and run the unit tests, then tell me how to try them on my phone
```
