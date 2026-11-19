# Taito NFC

Taito NFC is an app by Taito United Oy that writes to NFC stickers, in English and Finnish: a
link, an email address or a phone number, which a phone held on the sticker opens or offers to
call, or any other text. What you type stays on the phone, and the app shows what a phone will do
with it and how many bytes it takes. Made for the Taito United Oy stand at Kumpulan Potentiaali on
26 November 2026, where the stickers carry the Signal and Telegram sticker pack links.

You write the tag and can protect it with a passphrase so only that passphrase rewrites it; the
second button removes the protection from any tag. Phones still read a protected tag; only rewriting
it needs the passphrase. The protection is the write password of NXP's NTAG213, 215 and 216 chips,
derived from the tag's UID and the passphrase, the same on Android and iOS, so a tag protected on
one unlocks on the other.

## Screenshots

The iOS app's App Store screenshots, in English and Finnish:

<p>
  <img src="docs/store/app-store-en-1-start.png" width="128" alt="1, start">
  <img src="docs/store/app-store-en-2-hold.png" width="128" alt="2, hold the phone on the tag">
  <img src="docs/store/app-store-en-3-written.png" width="128" alt="3, written">
  <img src="docs/store/app-store-en-4-confirm.png" width="128" alt="4, confirm the protection">
  <img src="docs/store/app-store-en-5-protected.png" width="128" alt="5, protected">
  <img src="docs/store/app-store-en-6-wrong-passphrase.png" width="128" alt="6, wrong passphrase">
</p>
<p>
  <img src="docs/store/app-store-fi-1-start.png" width="128" alt="1, start, in Finnish">
  <img src="docs/store/app-store-fi-2-hold.png" width="128" alt="2, hold the phone on the tag, in Finnish">
  <img src="docs/store/app-store-fi-3-written.png" width="128" alt="3, written, in Finnish">
  <img src="docs/store/app-store-fi-4-confirm.png" width="128" alt="4, confirm the protection, in Finnish">
  <img src="docs/store/app-store-fi-5-protected.png" width="128" alt="5, protected, in Finnish">
  <img src="docs/store/app-store-fi-6-wrong-passphrase.png" width="128" alt="6, wrong passphrase, in Finnish">
</p>

The Android app in demo mode after writing the Signal link:

<img src="docs/screenshots/android-demo.png" width="128" alt="The Android app in demo mode">

## Icon and store images

<p>
  <img src="ios/Sources/Assets.xcassets/AppIcon.appiconset/icon-1024.png" width="128" alt="The iOS icon">
  <img src="ios/Sources/Assets.xcassets/AppIcon.appiconset/icon-1024-dark.png" width="128" alt="The dark iOS icon">
  <img src="ios/Sources/Assets.xcassets/AppIcon.appiconset/icon-1024-tinted.png" width="128" alt="The tinted iOS icon">
  <img src="docs/store/google-play-icon-512.png" width="128" alt="The Google Play icon">
</p>

The iOS icon in its default, dark and tinted appearances, then the Google Play icon: the Taito
United Oy mark, the O and U of the text logo on taitounited.fi, in white on the site's green
#009A48, with three NFC waves. The Android launcher draws the same shapes, and
[icon.svg](docs/store/icon.svg) is the icon as a vector. The Google Play feature graphic is a
placeholder:

<img src="docs/store/google-play-feature-graphic.jpg" width="512" alt="The Google Play feature graphic">

### For designers

These images are first versions drawn in code. Comments, edits and new versions are welcome, as an
issue on this repository or in Taito United Oy's Slack. Questions to start from:

- Does the icon read as Taito United Oy and as NFC at Home Screen size, and do the dark and tinted
  versions hold up?
- What should the feature graphic show? Google advises against repeating the app icon in it, which
  the placeholder does.
- Do the screenshots need captions or a device frame, or do plain captures show the app well
  enough?

A new icon reaches the stores only in a new build of both apps. What each image has to be:

| Image | Size and format | Also |
|---|---|---|
| iOS icon | 1024x1024 px PNG, or a layered Icon Composer file | Default, dark and tinted versions |
| Android launcher icon | Foreground and monochrome layers, 108x108 dp | The logo 48 to 66 dp across: only the middle 66x66 dp always shows |
| Google Play icon | 512x512 px, 32-bit PNG with alpha, at most 1024 KB | |
| Google Play feature graphic | 1024x500 px, JPEG or 24-bit PNG without alpha | Key elements near the centre, since some layouts cut the edges |
| App Store screenshots | 1206x2622 px portrait, PNG or JPEG, up to 10 per language | The six above, in English and Finnish |
| Google Play phone screenshots | 2 to 8, PNG or JPEG, 9:16 portrait, each side 320 to 3,840 px | Not made yet |

Sources: Apple's app icon guidelines,
[https://developer.apple.com/design/human-interface-guidelines/app-icons](https://developer.apple.com/design/human-interface-guidelines/app-icons),
App Store Connect's screenshot specifications,
[https://developer.apple.com/help/app-store-connect/reference/app-information/screenshot-specifications/](https://developer.apple.com/help/app-store-connect/reference/app-information/screenshot-specifications/),
Google Play's preview asset guidelines,
[https://support.google.com/googleplay/android-developer/answer/9866151](https://support.google.com/googleplay/android-developer/answer/9866151),
and Android's adaptive icon guide,
[https://developer.android.com/develop/ui/views/launch/icon_design_adaptive](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive).

## Parts

- `android/`: the Android app, Kotlin and Jetpack Compose, application id `fi.taitounited.nfc`.
- `ios/`: the iOS app, SwiftUI with Core NFC, bundle id `fi.taitounited.nfc`. Its Xcode project is
  generated from `ios/project.yml` with xcodegen.

## Tags

Use NTAG215 or NTAG216 stickers for a long link or text. An NTAG213 holds 144 bytes, enough for a
short link such as a Telegram pack but not a longer one such as the Signal pack link; the app names
the smallest tag that fits. MIFARE Classic tags do not work. A phone shows text only in an app that
reads NFC tags: an iPhone acts on its own only on links, and Android does nothing with text unless
such an app is installed.

## Try it

On an Android phone with NFC, install the debug build and open Taito NFC. Write a link, leave the
app and hold the phone on the tag to test it, then protect the tag.

An emulator has no NFC. Started with the `demo` extra, the app plays each tap on a pretend tag that
follows the same rules:

```bash
adb shell am start -n fi.taitounited.nfc/.MainActivity --ez demo true
```

## Build

The Android app needs JDK 17 or newer and the Android SDK with platform 37.1 (`ANDROID_HOME`):

```bash
(cd ~/Projects/nfc-tool && android/gradlew -p android testDebugUnitTest assembleDebug bundleRelease)
```

The iOS app needs Xcode and xcodegen. This builds it for the simulator:

```bash
(cd ~/Projects/nfc-tool/ios && xcodegen generate && xcodebuild -project TaitoNFC.xcodeproj \
  -scheme TaitoNFC -destination 'generic/platform=iOS Simulator' -derivedDataPath build \
  CODE_SIGNING_ALLOWED=NO build)
```

In the simulator, the `-demo` launch argument plays the taps on a pretend tag.

## Start with Claude Code

```text
clone github.com/TaitoUnited/nfc-tool into ~/Projects/nfc-tool, read its CLAUDE.md, build the android and ios apps and run the unit tests, then tell me how to try them on my phone
```

## Privacy

The app collects nothing and has no network access: [PRIVACY.md](PRIVACY.md), in English and
Finnish, is the privacy policy the app stores link to.

## Licence

The code is under the GNU Affero General Public License, version 3 or later, in
[LICENSE](LICENSE). The README, the docs and the images are under Creative Commons
Attribution-ShareAlike 4.0, in [LICENSE-PROSE](LICENSE-PROSE).
