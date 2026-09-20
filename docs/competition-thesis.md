# Competition thesis

## One-line promise

Murphy turns a group of ordinary phones into a local lifeline when the network disappears.

## Why this can stand out

The project is not just a Bluetooth chat. Its central demo is a failure scenario:

1. three devices form a local mesh;
2. one device sends an emergency message through a relay;
3. the relay disappears;
4. the mesh reroutes or clearly reports that the destination is unreachable;
5. the event history explains every hop and failure without a cloud service.

That story makes the technical choices visible to a judge in under two minutes.

## Technical pillars

- KMP shared domain and deterministic state transitions;
- native BLE adapters for Android and iOS;
- explicit packet TTL and duplicate protection to prevent loops;
- offline-first event history;
- deterministic simulation for demos and regression tests;
- failure injection instead of a happy-path-only prototype.

## Definition of a competition-ready release

- A complete offline demo can be run without accounts, internet or a backend.
- The same domain tests run for JVM and the native targets.
- A relay failure is visible, understandable and recoverable.
- The README includes architecture, threat model, battery trade-offs and a short demo script.
- The UI is accessible, calm and legible in an emergency rather than looking like a generic chat app.

## Current phase

The repository is in the shared-core foundation phase. The first increment contains the state machine, packet router and a deterministic topology simulator. Platform BLE adapters and the polished demo UI come after the core behavior is stable.

