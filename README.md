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

## Project direction

- Kotlin Multiplatform shared module;
- native Bluetooth adapters per platform;
- offline-first operation;
- no dependency on a central server for peer-to-peer messaging;
- explicit state and event history for debugging and emergency scenarios;
- a storage seam that lets each platform persist the same event model offline.

The project is currently in the conception/foundation phase and is planned as a candidate for the JetBrains KMP Contest 2027.

The competition thesis and release bar are documented in [docs/competition-thesis.md](docs/competition-thesis.md).
The decentralized topology decision is documented in [docs/architecture.md](docs/architecture.md).

## Layout

```text
shared/
  src/commonMain/   platform-independent domain and routing logic
  src/commonTest/   deterministic unit tests
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
is globally unreachable. Cancellation propagates to the caller. Retries and
destination acknowledgements are not implemented yet.

## Local verification

Use JDK 21 and the included Gradle 9.3.1 wrapper. The first run downloads the
distribution and dependencies. Run the shared core tests on JVM with:

```bash
./gradlew :shared:jvmTest
```

On 2026-10-05, all 18 JVM tests passed with no failures or skipped tests.
This verifies shared logic with fake connections, not physical BLE operation.
Android and iOS builds have not yet been verified. On a machine configured for
the native targets, the broader test entry point is:

```bash
./gradlew :shared:allTests
```
