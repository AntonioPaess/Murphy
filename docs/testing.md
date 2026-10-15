# Murphy — implementation status and testing

Updated 2026-10-15. App-owned UI language: **English**.

## Implementation boundary

| Area | Exists | Limitation |
| --- | --- | --- |
| KMP core | Routing, bounded TTL/dedup cache, shared binary wire/framing, real callback `RadioSession` | Memory only; separate coordinator retry scheduler is not connected to native callbacks |
| Native radio | BluetoothGatt Android + CoreBluetooth iPhone, central/peripheral, group discovery/handshake, group broadcast/relay | Physical interoperability/range/throughput not validated; foreground only; public groups |
| Android app | Compose, ViewModel, premium English UI, real radio state, UI tests | API 26+; permission approval required |
| iPhone app | SwiftUI, native tabs/navigation/forms, KMP framework, real radio state, UI tests | iOS 16+; Apple Silicon simulator (no radio) and arm64 device |
| Design | Graphite/white/cobalt; native fonts/text scaling; English SVG/PNG references | Native subset of 16 static concepts, not a completed Figma implementation |
| Figma | Empty workspace; metadata/font/library discovery succeeded | First write hit Starter quota; web canvas did not load. No publication or paid upgrade |
| Reliable/safe messenger | **Not complete** | Destination ACK, durable history/retry, authentication/encryption, background/battery, SOS and physical acceptance pending |

## Evidence

**50 shared tests passed on JVM and 50 on the iOS arm64 simulator**, zero
failures/errors/skips. New tests cover strict codec rejection/UTF-8, bounded and
expired framing, real callback group scoping, in-memory three-node packet relay,
TTL boundary, source exclusion/deduplication, bounded cache and no false ACK.
Legacy preview tests remain but normal apps no longer use `PreviewSession`.

The iPhone simulator app with the real adapter compiled with KMP; **two current
XCUITests passed** on iPhone 17/iOS 26.1: no-peer send/history, unavailable SOS and
leave flow. The debug Android APK/test APK built; **two current Compose tests
passed** on Medium Phone/Android 16/API 36.1: no-peer send/rotation, unavailable
SOS and leave. The first SOS assertion needed lazy-list scrolling rather than
looking for a not-yet-composed offscreen node; corrected and repeated. These are virtual-device
tests, not physical BLE validation. Capture provenance: [screenshots](screenshots/README.md).

English design guard: **710 approved text nodes across nine SVG boards**.
`plutil` accepted project/Info.plist; `xcodebuild -list` recognized Murphy.
The current native-radio iPhone-device arm64 app also built successfully with
signing disabled. No installation on the user's iPhone, IPA or TestFlight release
is claimed.

The file provider supplied stale/dataless duplicate generated class files during
the repeat build. The affected generated runtime-class directory and Android
build folder (including duplicate ` 2.dex` files) were preserved under ignored
`build/file-recovery/` and regenerated; the clean final build succeeded.
No global sync settings or user files were deleted. The old copy checker had
accepted a partial read with only 299 text nodes; it now rejects empty/incomplete
SVGs before checking or rewriting. Verified the unchanged **710 nodes/nine boards**
from a clean Git export in a temporary directory; a complete-board fixture
passed and an incomplete SVG fixture correctly failed. Native English UI is
checked separately from those static design boards.

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
device serials and reads only this app's two known screenshot files.

For a physical Android, enable USB debugging, authorize this computer and select
the phone yourself; disable debugging afterwards if not needed. See the
[official guide](https://developer.android.com/studio/run/device).
Android 12+ requests Nearby devices scan/connect/advertise permissions when
the user creates/finds/scans. Older versions request location permission for
BLE scanning (legacy permissions are maxSdk 30). `neverForLocation` is declared;
Murphy does not derive location. No Internet permission is added. See the
[official Bluetooth permissions guide](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions).

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

## What to test in the current native apps

1. Welcome shows **Bluetooth not started**, English development/privacy warning.
2. Enter **Your name** and **Group name**; empty inputs cannot proceed. Names
   over 40 UTF-16 units return the shared English error.
3. Group shows only actual connected peers. With none, **0 direct peers**;
   Network has no invented device, position or distance.
4. No-peer Chat send: **Not sent · no connected peers**, never Delivered/ACK.
   Empty/over-1,000-unit messages are rejected; messages/activity retain at most
   100 entries each. Recent entries evict oldest; this is not durable outbox.
5. History shows actual local activity newest first, not made-up peer events.
6. **SOS not available yet** opens an explanation, not a fake alert sent flow.
7. Leaving requires confirmation, closes links and clears messages/group.
   Force-stop/relaunch loses state (track 5 pending). Android rotation retains
   ViewModel content but pauses/resumes radio through the foreground lifecycle.
8. Device language Portuguese: app-owned labels remain English; typed content
   is unchanged. Native OS components may be localized.
9. Try large text, screen readers, landscape, narrow width and keyboard. Record
   clipping/focus/tap-target/outdoor contrast issues. Native scaling and labels
   are a baseline, not accessibility certification.

## Physical development test — native adapters implemented, not yet validated

Install the development APK on authorized Androids using Android Studio. On
iPhone 16 Pro, open the Xcode project, select your own signing team, pair/trust
the phone, enable Developer Mode yourself and Run. No signing account, device
setting or remote install is changed automatically.

Keep Bluetooth enabled and both apps in foreground. On A, **Create group**;
on B, enter a display name → **Find nearby groups** → pick A's advertised group
→ **Join group**. Do not create two separate groups with the same name. Use
**Scan nearby** on both if discovery's 10-second window expired. Wait for an
actual peer on both, then send from **Chat** in both directions. Confirm body
on the receiving screen; sender acceptance is not receipt proof. First use only
non-sensitive test data. Disable cellular/Wi-Fi if checking no-server operation,
but keep Bluetooth on; no airplane-mode assumption is made.

Record model, OS, foreground state, permission status, exact body, both screens,
direction and timing. Test multiline/emoji/1,000-unit packets, duplicate body
with distinct IDs, Bluetooth off, permission denial and disconnect/rescan.
Current queued packets fail on disconnect; there is no automatic resend later.
Going to background pauses radio; this is a disclosed limit, not a passed
background/battery test. See [wire contract/limits](radio-protocol.md).

Android+iPhone suffice for direct radio; **three physical devices** for a relay.
Prevent the direct shortcut with a developer-controlled topology; distance alone
does not prove sender → relay → receiver. A link-exclusion/trace harness for
physical relay proof is still pending; the shared three-node test is in memory.

Available to the user: an iPhone 16 Pro and multiple Android phones at work
(confirmed 2026-10-14). Plan: Android ↔ iPhone direct in both directions, then
Android → iPhone relay → Android and iPhone → Android relay → Android, with
the shortcut explicitly disabled. These devices have not been connected or
tested by this build; the user still needs to pair/authorize them.

| Check | Current / future acceptance |
| --- | --- |
| Permission denied/Bluetooth off | English recovery path, no false connection |
| No internet/cellular; Bluetooth on | No server requirement |
| Direct/relay send | Current local receive/forward code; physical proof pending. Destination ACK still absent |
| Disconnect/reconnect | Current native queues fail/disconnect; rescan, no automatic message retry |
| 60-second deadline | Native packet queue/assembly timeout; separate core retry expiry is not wired into apps |
| Duplicate/TTL/queue limits | Explicit bounded outcomes, no loops/unbounded retries |
| Group/authentication mismatch | Foreign UUID ignored, but current public groups are not authenticated; security pending |
| Restart | Durable recovery only after track 5, no stale sends |
| Background/battery saver | Measure limitations/energy; no unrestricted BLE promise |
| SOS | Group-only, meaningful receipt state, no automatic rescue/location |
| Accessibility/outdoors | Usable large fonts/screen readers, warnings not color-only |

Tracks 4–7 and physical acceptance of track 3 remain pending; see
[delivery plan](implementation-plan.md). Figma publication remains blocked.
Murphy is not an emergency-ready messenger and cannot guarantee delivery.
