# Murphy — implementation status and testing

Updated 2026-10-14. App-owned UI language: **English**.

## Implementation boundary

| Area | Exists | Limitation |
| --- | --- | --- |
| KMP core | Routing, TTL/deduplication, relay simulator, transport/retry/60-second expiry | Fake adapters; serialized caller-driven scheduler; memory only |
| Shared preview | One Kotlin `PreviewSession` consumed by Android and Swift | Local group, 50 unsent drafts, 100 events, SOS preview; no radio or durable storage |
| Android app | Compose, ViewModel, premium English UI, instrumentation tests | Demo host, API 26+ |
| iPhone app | SwiftUI, native tabs/navigation/forms, KMP framework build phase, UI tests | Demo host, iOS 16+; Apple Silicon simulator and arm64 device |
| Design | Graphite/white/cobalt; native fonts/text scaling; English SVG/PNG references | Native subset of 16 static concepts, not a completed Figma implementation |
| Figma | Empty workspace; metadata/font/library discovery succeeded | First write hit Starter quota; web canvas did not load. No publication or paid upgrade |
| Real messenger | **Not implemented** | BLE, discovery/groups, wire protocol, ACK, persistence, security/background and transmitted SOS pending |

## Evidence

On 2026-10-14, **35 shared tests passed on JVM and 35 on the iOS arm64 simulator**,
zero failures/errors/skips. Five new preview-state tests cover name validation,
bounded immutable drafts, user text preservation, explicitly non-transmitting
SOS and exit/history bounds. The shared iPhone-device target also compiled.

The iPhone simulator app compiled with KMP; **two XCUITests passed** on iPhone
17/iOS 26.1. Group/history/SOS screenshots were exported and inspected. The
debug Android APK/test APK were generated; **two Compose tests passed** on the
Medium Phone Android 16/API 36.1 emulator after updating the test harness.
These are virtual-device tests, not physical BLE validation.

English design guard: **710 approved text nodes across nine SVG boards**.
`plutil` accepted project/Info.plist; `xcodebuild -list` recognized Murphy.
The complete iPhone-device arm64 app also built with signing disabled. This is
an unsigned build, not installation on the user's iPhone or an IPA/TestFlight release.

## Build prerequisites

JDK 21; Android bytecode explicitly Java 17. Gradle **8.9**, official SHA-256
pinned. Gradle 9.3.1 passed JVM tests but failed native framework configuration
with pinned Kotlin 2.1.10 (`DefaultArtifactPublicationSet`). The first Android
compile also exposed inconsistent Java/Kotlin targets; both were corrected.
The first native compile also caught a missing explicit
`kotlin.jvm.JvmInline` import in the common ID models; JVM default imports had
masked it. The common source now imports the optional-expect annotation explicitly.
The first Android UI test failed in old Espresso's reflective InputManager call,
before accessing the app. Espresso 3.7.0/runner 1.7.0/JUnit extension 1.3.0 replace
that unsupported harness path; see the [official AndroidX Test changelog](https://developer.android.com/jetpack/androidx/releases/test).
Screenshot review also exposed a System UI ANR in the headless/software-GPU
emulator. Cold restart with host GPU cleared that environment issue, without
wiping the AVD. The capture helper now requires Murphy to be the active window
so a passing semantic test cannot publish a screenshot hidden by a system dialog.

Set `ANDROID_HOME` or untracked `local.properties` with
`sdk.dir=/absolute/path/to/Android/sdk`. Platform 35/Build Tools 34 were installed
by Gradle using pre-existing accepted SDK license files. No new agreement was
accepted for the user. Do not change compileSdk to hide missing SDK packages.

iPhone: Apple Silicon Mac, Xcode, JDK 21. First builds download Kotlin/Native and
dependencies. Compatibility with the installed Xcode SDK must be tested.
No signing identity/account is checked in. Use offline Gradle only after caching.

## Shared checks

```sh
./gradlew :shared:jvmTest
node scripts/design-copy.mjs
```

Report: `shared/build/reports/tests/jvmTest/index.html`. The design-copy guard
does not check all native/OS strings. Native app labels, errors and accessibility
text are English; user content stays unchanged. OS keyboard/dialogs may follow
device language.

## Android

Open the repository in Android Studio, select JDK 21, **androidApp**, and an
emulator/authorized phone, then Run. CLI build/test:

```sh
./gradlew :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest
./gradlew :androidApp:connectedDebugAndroidTest
```

Select a test-only emulator: the connected test task installs/runs on connected
targets. Debug APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.
Report: `androidApp/build/reports/androidTests/connected/debug/index.html`.
This is a test APK, not a signed store release.

The tests save group/chat PNG captures in the target app's cache. The connected
task cleans up APK/cache afterwards; captures were exported using a direct
instrumentation run of the same APKs on the test-only emulator. Versioned
captures are in `docs/screenshots/`. Do not select a physical phone unintentionally.
With the test APKs still installed and the screenshot files present, export
unchanged PNGs using `node scripts/export-android-screenshots.mjs emulator-5554`
(replace with your actual test-emulator serial). The script refuses physical
device serials and reads only this demo app's two known screenshot files.

For a physical Android, enable USB debugging, authorize this computer and select
the phone yourself; disable debugging afterwards if not needed. See the
[official guide](https://developer.android.com/studio/run/device).
The demo requests no Bluetooth, location or Internet permissions.

## iPhone

Open `iosApp/Murphy.xcodeproj`, select **Murphy**, choose an iPhone simulator,
then Run. A build phase generates static `Shared.framework` for selected SDK
and Debug/Release, using `JAVA_HOME` or locating JDK 21. Script sandboxing is
disabled for this target because Gradle needs its dependency cache.

```sh
xcodebuild -project iosApp/Murphy.xcodeproj -scheme Murphy \
  -configuration Debug -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath iosApp/DerivedData CODE_SIGNING_ALLOWED=NO build
```

List actual IDs using `xcrun simctl list devices available`; replace the
placeholder below with your selected simulator UDID:

```sh
xcodebuild -project iosApp/Murphy.xcodeproj -scheme Murphy \
  -configuration Debug -destination 'id=YOUR_SIMULATOR_UDID' \
  -derivedDataPath iosApp/DerivedData CODE_SIGNING_ALLOWED=NO test
```

UI tests keep screenshots in the result bundle. `SKIP_KOTLIN_BUILD=YES` is only
for a matching, already-built/current framework. The app host configures arm64
simulators, not Intel. No running-app network backend is used.

For a physical iPhone, choose your team in Signing & Capabilities, pair the
device and complete Trust/Developer Mode yourself. No IPA/TestFlight release is
provided. See [Apple's guide](https://developer.apple.com/documentation/xcode/running-your-app-on-simulated-or-physical-devices).
Simulator tests are not physical BLE evidence.

Optional shared native checks: `./gradlew :shared:compileKotlinIosArm64` and
`./gradlew :shared:iosSimulatorArm64Test`.

## What to test in both demos

1. Welcome shows **DEMO MODE · Bluetooth not connected** and emergency warning.
2. Enter **Your name** and **Group name**; empty inputs cannot proceed. Names
   over 40 UTF-16 units return the shared English error.
3. Group shows **0 direct peers**. Network explains no scanning/advertising,
   connection, GPS or distance measurement.
4. Save a Chat draft: **Not sent · demo only**, never Sent/Delivered/ACK.
   Empty/over-1,000-unit drafts and the 51st draft are rejected.
5. History shows local demo events newest first; iPhone rows open details.
6. Preview SOS: cancel, then confirm. No alert sent/nobody alerted; close it.
7. Settings clears drafts. Leaving requires confirmation and clears identity/
   drafts. Force-stop/relaunch loses state by design (track 5 is pending).
   Android rotation retains ViewModel state.
8. Device language Portuguese: app-owned labels remain English; typed content
   is unchanged. Native OS components may be localized.
9. Try large text, screen readers, landscape, narrow width and keyboard. Record
   clipping/focus/tap-target/outdoor contrast issues. Native scaling and labels
   are a baseline, not accessibility certification.

## Physical acceptance — blocked until radio implementation

Android+iPhone suffice for direct radio; **three physical devices** for a relay.
Prevent the direct shortcut with a developer-controlled topology; distance alone
does not prove sender → relay → receiver.

Available to the user: an iPhone 16 Pro and multiple Android phones at work
(confirmed 2026-10-14). Plan: Android ↔ iPhone direct in both directions, then
Android → iPhone relay → Android and iPhone → Android relay → Android, with
the shortcut explicitly disabled. These devices have not been connected or
tested by this build; the user still needs to pair/authorize them.

| Check | Expected result after implementation |
| --- | --- |
| Permission denied/Bluetooth off | English recovery path, no false connection |
| No internet/cellular; Bluetooth on | No server requirement |
| Direct/relay send | Display once; destination ACK, not adapter acceptance, permits receipt state |
| Disconnect/reconnect | Other links tried; eligible retries preserve TTL |
| Disconnected past 60-second age | Caller-driven expiry once; no stale send after reconnect |
| Duplicate/TTL/queue limits | Explicit bounded outcomes, no loops/unbounded retries |
| Group/authentication mismatch | Cannot inject content or spoof ACK |
| Restart | Durable recovery only after track 5, no stale sends |
| Background/battery saver | Measure limitations/energy; no unrestricted BLE promise |
| SOS | Group-only, meaningful receipt state, no automatic rescue/location |
| Accessibility/outdoors | Usable large fonts/screen readers, warnings not color-only |

Tracks 3–7 remain pending; see [delivery plan](implementation-plan.md).
Murphy Demo is not an emergency-ready messenger and cannot guarantee delivery.
