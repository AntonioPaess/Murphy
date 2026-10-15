# Murphy — seven delivery tracks

Requested 2026-10-14. Each completed track gets its own commit. A blocked track
does not get an empty or misleading completion commit. README and the development
vault accompany every delivered increment. All app-owned copy is English.

| # | Track | Current boundary |
| --- | --- | --- |
| 1 | Editable Figma screens | Blocked: metadata reads succeeded, but the first write hit the Starter MCP quota. The web editor canvas did not finish loading. No screens were published or paid plan changed. |
| 2 | Android/iPhone apps, premium English UI | Intermediate native hosts delivered in `ea33b88`, with disclosed local preview state. Current normal execution is now connected to native radio, not `PreviewSession`. |
| 3 | Native Bluetooth, peer/group discovery, wire protocol | Implemented development GATT central/peripheral adapters, public group UUID discovery/handshake, shared framing and broadcast relay. Virtual logic/UI checks; physical interoperability acceptance remains open. |
| 4 | Destination acknowledgement | Not implemented. Must distinguish local adapter acceptance from authenticated destination receipt, including retries, duplicates and timeout. |
| 5 | Durable history/pending queue | Not implemented. Live message/activity state and core retry state are in memory; core retries are not wired into the native callback path. Requires schema, restart recovery, age/clock handling and migration tests. |
| 6 | Security/background/battery | Not implemented. Requires threat model, group authentication, encryption/key management and platform-specific lifecycle/energy measurements. |
| 7 | SOS/accessibility/physical acceptance | SOS is unavailable, not a fake transmitting flow. Native scaling/labelled controls are a baseline, not certification. Physical Android↔iPhone and controlled three-device relay tests remain mandatory. |

## UI implementation boundary

The local premium design boards remain the visual reference while Figma is
blocked. This is disclosed, not a claim that an editable Figma review happened.
Native system fonts preserve text scaling; project color tokens preserve the
graphite/white/cobalt identity. The native navigation shell adapts to each OS.

Normal Android/iPhone execution uses `RadioSession` and native Bluetooth APIs.
`PreviewSession` remains a legacy test fixture, not a substitute for radio.
Permission approval is user-owned. Groups/peer counts come from real discovery
and handshakes, and local send acceptance never claims destination receipt.
Restart loses memory-only state; background pauses radio. See the
[wire specification](radio-protocol.md) and [testing guide](testing.md).

## Release gate

Do not describe Murphy as an emergency-ready app. Before that claim is even
considered, tracks 3–7 need implementation and documented physical evidence.
Simulator/emulator checks only validate logic/UI, not Bluetooth interoperability,
range, background reliability, battery life, rescue or guaranteed delivery.
