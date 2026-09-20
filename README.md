# Murphy

Murphy is a peer-to-peer BLE mesh network for outdoor activities such as hiking and surfing. It is designed for communication when there is no internet or cellular coverage, with shared networking logic across Android and iOS through Kotlin Multiplatform.

The project is intentionally starting with the shared domain core before platform-specific Bluetooth integrations. The first increment establishes:

- a connection state machine;
- a small, testable mesh packet router with TTL and duplicate protection;
- a deterministic topology simulator for relay failures and rerouting;
- an event history that can later feed the Android and iOS UIs.

## Project direction

- Kotlin Multiplatform shared module;
- native Bluetooth adapters per platform;
- offline-first operation;
- no dependency on a central server for peer-to-peer messaging;
- explicit state and event history for debugging and emergency scenarios.

The project is currently in the conception/foundation phase and is planned as a candidate for the JetBrains KMP Contest 2027.

The competition thesis and release bar are documented in [docs/competition-thesis.md](docs/competition-thesis.md).

## Layout

```text
shared/
  src/commonMain/   platform-independent domain and routing logic
  src/commonTest/   deterministic unit tests
```

## Local verification

The repository contains the Gradle project definition, but the Gradle CLI/wrapper is not available in the current workspace yet. Once Gradle is available, run:

```bash
./gradlew :shared:allTests
```
