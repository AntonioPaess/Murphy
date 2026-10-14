# Murphy — implementation status and testing

Updated 2026-10-14. UI language: **English**.

## What exists today

| Area | Implemented | Evidence / limitation |
| --- | --- | --- |
| KMP foundation | Android library, JVM, iOS device/simulator targets; Gradle wrapper | JVM compiled; native targets not validated |
| Domain | Peers, message IDs/envelopes, connection states and local session events | Unit tests, no real radio |
| Routing | Hop TTL, deduplication, destination delivery in a local session | Not a remote delivery ACK |
| Mesh simulation | Deterministic topology/relay loss/alternative paths | Simulation, not route discovery on devices |
| Transport coordinator | Bounded flooding; excludes incoming peer; isolated expected send failures | Fake adapters; calls must be serialized |
| Retry queue | Per message/peer, 3 total attempts, 1-second delay, capacity 128 | In memory; platform scheduler required |
| Queue age | 60-second local lifetime, no extension on retry, detached-peer expiry, capacity reclamation | Explicit caller-driven cleanup; not wire-message age |
| History | Replaceable event store, default in memory | Not durable storage |
| Design | 16 premium screen concepts; original proposal retained; English SVG/PNG boards | Static assets, not executable screens; Figma page currently empty |
| App packages | **Not implemented** | No Android app host/APK or iOS host/IPA |

## Run the checks that are available now

From the repository root, with JDK 21:

```sh
./gradlew :shared:jvmTest
node scripts/design-copy.mjs
```

Use `--offline --no-daemon` on Gradle only when dependencies are already cached.
The first online run downloads the wrapper/dependencies. Node uses no packages
for copy verification. JVM results: `shared/build/reports/tests/jvmTest/index.html`.

On 2026-10-14: **30 JVM tests passed**, zero failures/errors/skips; **710 English
text nodes** verified across nine SVG boards. New tests cover exact expiry,
detached peers, retry interval longer than lifetime, reconnection at expiry,
non-extending deadlines, reclaimed capacity, untrusted remote creation time and
overflow-safe deadlines. Existing tests cover TTL, duplicates, relay recovery,
partial failures, cancellation and attempt/capacity limits.

The approved copy catalog covers app-owned design text. It is not an automatic
English detector, and it does not translate user names or user-written messages.
Future Android/iOS accessibility labels, permission rationale and notifications
must also use English; native OS permission dialogs may follow the OS language.

## Android: library checks, not installation

1. Open the repository in Android Studio and select JDK 21 for Gradle.
2. In SDK Manager, install **Android SDK Platform 35**, matching `compileSdk`.
   Allow the required build tools/dependencies to resolve on the first run.
3. Set `ANDROID_HOME` to your actual SDK directory, or create untracked
   `local.properties` with `sdk.dir=/absolute/path/to/Android/sdk`.
4. Run:

```sh
./gradlew :shared:assembleDebug :shared:testDebugUnitTest
```

This produces/tests a library. An AAR cannot be installed on a phone like an APK.
The local attempt failed with **SDK location not found**. API 35 is also absent
from this environment's installed SDK platforms. Neither native check is claimed
as passed. These setup issues were recorded, not solved by silently upgrading
the project's SDK or installing system packages.

### Physical Android flow — after the app host exists

Add the Android application module, UI and BLE adapter first. Then connect the
phone by USB, enable Developer options/USB debugging yourself, accept the
computer's debugging authorization, select the phone and the application run
configuration in Android Studio, and press Run. Disable debugging again when
you no longer need it. Follow the [official Android device guide](https://developer.android.com/studio/run/device).
This is a future procedure, not something this commit can run on a phone.

## iPhone: shared-core checks, not installation

On an Apple Silicon Mac with Xcode and a compatible Kotlin/Native toolchain:

```sh
./gradlew :shared:compileKotlinIosArm64
./gradlew :shared:iosSimulatorArm64Test
```

These task names were confirmed in this repository. They compile a Kotlin library
and run native common tests, respectively; they do not create an installable iOS
application. Kotlin/Native artifacts/dependencies need preparation, and the first
run may download them. The local Kotlin/Native cache is absent. Xcode and iPhone
simulator runtimes were detected, but **native compilation/tests were not run**.
Check compatibility with the pinned Kotlin 2.1.10 rather than assuming the newest
Xcode works with it. For an Intel Mac, inspect `:shared:tasks --all` and use the
configured `iosX64` test target instead.

The repository also lacks framework export configuration and an Xcode app host.
See [Kotlin native binaries](https://kotlinlang.org/docs/multiplatform/multiplatform-build-native-binaries.html)
and [multiplatform tests](https://kotlinlang.org/docs/multiplatform/multiplatform-run-tests.html).

### Physical iPhone flow — after the app host exists

Create/integrate the app host and shared framework, then implement UI/CoreBluetooth
and Bluetooth permission descriptions in English. In Xcode, configure the app's
Signing & Capabilities with your team, connect/pair the iPhone, complete any Trust
and Developer Mode prompts yourself, select it as the run destination and Run.
Follow [Apple's physical-device instructions](https://developer.apple.com/documentation/xcode/running-your-app-on-simulated-or-physical-devices).
Simulator unit tests are not evidence of a physical BLE link; real radio tests
must use physical devices. No signing identity or device settings were changed.

## Physical acceptance checklist — blocked until hosts/adapters exist

Use an Android and an iPhone for cross-platform direct messaging. **At least
three physical devices** are needed for a sender → relay → receiver scenario.
Use a developer-controlled topology/test transport mode to prevent a direct
sender/receiver shortcut, rather than inferring a relay solely from distance.

| Check | Expected result |
| --- | --- |
| UI language with phone set to Portuguese | App-owned labels, alerts, help, accessibility text and notifications remain English |
| Permission denied / Bluetooth off | Clear English state and a recovery path; no false connected status |
| No internet / cellular unavailable, Bluetooth still on | Peer transport still functions; no server requirement |
| Send to a direct peer | Receiver displays the message; adapter acceptance alone is never “Delivered” |
| Sender → relay → receiver | Message reaches destination and is delivered once per session |
| Relay/peer disconnects | Other eligible links are still attempted; eligible pending sends stay visible |
| Reconnect before expiry | Eligible retry sends the already-routed envelope without another TTL decrement |
| Remain disconnected past 60 seconds and tick cleanup | `EXPIRED` event, queue slot reclaimed, no stale send after reconnection |
| Duplicate packet / TTL exhausted | No duplicate delivery; exhausted forwarding is dropped |
| Queue full / attempts exhausted | Explicit outcome, no unbounded retry loop |
| SOS | Explicit group-only scope; no promise of rescue or receipt |
| App restart/background/battery saver | Record current limitations; do not claim persistence/background support until implemented |
| Large fonts / screen reader / outdoors | No clipping, usable focus/order, legible states and adequately sized controls |

## Remaining implementation, in order

1. Complete editable Figma publication and review the English design.
2. Android application and iOS app host/shared framework integration; implement
   the premium UI with English resource/accessibility/notification text.
3. Native BLE scanning/advertising/connect/send/receive adapters and scheduler.
4. Wire protocol, fragmentation/reassembly, peer identity and group discovery.
5. Destination ACK protocol (with timeout/deduplication), durable history/queue,
   restart recovery and preservation of interrupted initial work/no-route drafts.
6. Authentication/encryption, privacy model, background limits and battery work.
7. SOS workflow, meaningful delivery state, real cross-platform/three-device
   tests and accessible outdoor UX.
8. Reproducible native CI/builds, signed test packages and competition demo.

This is not yet an emergency-ready messenger. The app and radio paths remain
unimplemented even though shared logic has passing tests.
