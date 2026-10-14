# Murphy

Murphy is a peer-to-peer BLE mesh network for outdoor activities such as hiking and surfing. It is designed for communication when there is no internet or cellular coverage, with shared networking logic across Android and iOS through Kotlin Multiplatform.

The project is intentionally starting with the shared domain core before platform-specific Bluetooth integrations. The first increment establishes:

- a connection state machine;
- a small, testable mesh packet router with TTL and duplicate protection;
- a deterministic topology simulator for relay failures and rerouting;
- an offline event history with a replaceable store;
- routing decision events that make forwarding, delivery and drops visible to the UI;
- a shared transport coordinator that bridges BLE connections and routing decisions;
- per-peer send outcomes, so a failed link does not stop attempts on other links.

**Current delivery status:** Android (Jetpack Compose) and iPhone (SwiftUI) demo
hosts now use the same KMP preview state. Explore a local group, network empty
state, unsent drafts, activity, settings and a non-transmitting SOS preview.
**This is a UI demo, not a working Bluetooth messenger.** Native BLE, destination
ACK, durable storage, security and background radio remain unimplemented.
See the [status and testing guide](docs/testing.md) and
[seven-track delivery plan](docs/implementation-plan.md).

**Product language:** all app-owned UI text must be English, regardless of device
language. The proposed screens are now in English, including errors and SOS
warnings. User names and user-authored messages are not translated automatically.

## Project direction

- Kotlin Multiplatform shared module;
- native Bluetooth adapters per platform;
- offline-first operation;
- no dependency on a central server for peer-to-peer messaging;
- explicit state and event history for debugging and emergency scenarios;
- a storage seam that lets each platform persist the same event model offline.

The project is in the native-demo/foundation phase and is planned as a candidate for the JetBrains KMP Contest 2027.

The competition thesis and release bar are documented in [docs/competition-thesis.md](docs/competition-thesis.md).
The decentralized topology decision is documented in [docs/architecture.md](docs/architecture.md).

## Design before implementation

Every upcoming UI increment starts with a visual proposal for review, then
implementation and verification. Update this README and the development vault
with the change, rationale, checks and remaining limitations.

The current premium proposal covers 16 mobile screens and their main
offline/failure states. See the [premium direction](docs/design/premium/README.md)
and [screen plan](docs/design/README.md). The earlier green proposal is preserved
for comparison.

![Premium overview](docs/design/premium/00-premium-overview.svg.png)

Complete review boards: [onboarding](docs/design/premium/01-entrada.svg.png),
[network](docs/design/premium/02-rede.svg.png),
[messaging and SOS](docs/design/premium/03-mensagens-sos.svg.png),
[history and settings](docs/design/premium/04-historico-ajustes.svg.png).

These boards are **static product concepts**. The native demo implements a subset
with real navigation and local preview state, not working discovery or messaging.
Native system fonts support text scaling; the palette remains graphite/white/cobalt.
Sending accepted by an adapter must never be presented as destination acknowledgement.

[Figma workspace](https://www.figma.com/design/25gjH4lJelpFg5jJEhWICm): created on
2026-10-05. The Starter MCP quota was exhausted before screen construction;
browser import attempts were unstable. The complete boards have **not** been
verified in Figma. The versioned SVG sources and PNG previews are the design
reference, not a completed Figma prototype.
On 2026-10-14, metadata access worked again, but the page was empty. Complete
Figma publication remains pending. The next write again hit the Starter quota;
the web canvas did not finish loading. No paid plan or sharing setting was changed.
The local premium boards are the disclosed implementation reference, not a claim
that editable Figma screens were delivered. Track 1 has no completion commit.

## Layout

```text
shared/
  src/commonMain/   platform-independent domain and routing logic
  src/commonTest/   deterministic unit tests
androidApp/         Android Compose demo host + instrumentation tests
iosApp/             iPhone SwiftUI host + shared Xcode scheme + UI tests
```

`MeshSession.handleIncoming` is the bridge between transport adapters and the
shared routing core. It applies TTL and duplicate protection, then records the
decision in `MeshEventStore` so an emergency trace remains inspectable offline.

`MeshTransportCoordinator` currently uses bounded flooding: it forwards a
message to every attached peer except the peer that sent it. TTL and duplicate
protection prevent loops while a route-table strategy is still being developed.

Expected adapter failures use `BleSendException`. A forwarding result lists only
peers whose adapters accepted the send, alongside failures for the other peers.
`NoRoute` means no eligible peer is attached; it does not prove the destination
is globally unreachable. Cancellation propagates to the caller.

Expected failed sends are queued per message and peer. `MeshRetryPolicy`
defaults to three total attempts, a one-second delay, 128 pending entries and
a 60-second local pending-send lifetime from the first failed attempt.
The platform calls `retryPending(atMillis)` using its session clock; there is
no background worker in the core. Retries reuse the already-routed envelope,
so TTL is unchanged and incoming-message deduplication remains active.
Detached peers wait for reconnection without consuming attempts, but expire
at the same deadline. `expirePending(atMillis)` can reclaim stale work without
sending; `retryPending` also expires it before selecting due work, and scheduling
a failed send reclaims expired capacity. Expiry emits `RetryStopReason.EXPIRED`
and is reported separately in `MeshRetryResult.expired`. There is no automatic
timer: cleanup requires a platform call using the same consistent local clock.
Remote creation timestamps do not control queue age. The queue
is in memory; no-route messages and unattempted sends interrupted by cancellation
are not queued. Destination acknowledgements are not implemented yet.

## Local verification

Use JDK 21 and the included Gradle 8.9 wrapper (official SHA-256 pinned). Gradle
9.3.1 could run the JVM tests but failed when exporting the pinned Kotlin 2.1.10
native framework. Android Java/Kotlin bytecode targets are explicitly Java 17.
The first run downloads the
distribution and dependencies. Run the shared core tests on JVM with:

```bash
./gradlew :shared:jvmTest
```

On 2026-10-14, **35 shared tests passed on JVM and 35 on the iOS arm64 simulator**,
including five preview-state tests. The shared iPhone-device target compiled,
the simulator and unsigned device apps built with KMP. **Two XCUITests and two
Compose UI tests passed**; Android debug APK generated. Build/test evidence and
the physical-device procedure are in [docs/testing.md](docs/testing.md).
Logic tests use fake connections, not physical BLE. On a machine configured for
the native targets, the broader test entry point is:

```bash
./gradlew :shared:allTests
```

Verify the approved English design copy with Node.js:

```bash
node scripts/design-copy.mjs
```

This checks 710 text nodes across nine SVG boards against
`docs/design/english-copy.json`. It is a design-copy guard, not a localization
system wired to a running application. See [docs/testing.md](docs/testing.md)
for current commands and the future physical-device acceptance checklist.

## Run the demo apps

Android: set `ANDROID_HOME`, install SDK Platform 35 and open the repository in
Android Studio. Select `androidApp` and Run on an emulator or an authorized phone.
CLI build: `./gradlew :androidApp:assembleDebug`.

iPhone: on an Apple Silicon Mac with JDK 21/Xcode, open
`iosApp/Murphy.xcodeproj`, select the **Murphy** scheme and an iPhone simulator,
then Run. A build phase generates the static KMP `Shared.framework` for the
selected SDK/configuration. For a physical phone, choose your own signing team
in Xcode and complete device pairing/Developer Mode yourself. No signing identity
is checked in. Intel iPhone simulators are not configured in the app host yet.

Both apps show **DEMO MODE · Bluetooth not connected**. A demo group is local;
it cannot be discovered by another phone. Chat saves at most 50 unsent drafts
in memory. History is capped at 100 local demo events. Closing the process loses
state; leaving a group clears drafts. SOS requires confirmation and opens only a
preview. It sends nothing, shares no location and contacts no rescue service.

## Real native demo captures

Captured in virtual-device UI tests, not generated mockups. More states and
capture provenance are in [docs/screenshots](docs/screenshots/README.md).

<img src="docs/screenshots/android-group.png" width="280" alt="Android native demo group with zero peers">
<img src="docs/screenshots/ios-group.png" width="280" alt="iPhone native demo group with zero peers">
