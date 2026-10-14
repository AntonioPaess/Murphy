# Murphy — seven delivery tracks

Requested 2026-10-14. Each completed track gets its own commit. A blocked track
does not get an empty or misleading completion commit. README and the development
vault accompany every delivered increment. All app-owned copy is English.

| # | Track | Current boundary |
| --- | --- | --- |
| 1 | Editable Figma screens | Blocked: metadata reads succeeded, but the first write hit the Starter MCP quota. The web editor canvas did not finish loading. No screens were published or paid plan changed. |
| 2 | Android/iPhone apps, premium English UI | Native demo hosts built: Compose + SwiftUI, both using KMP `PreviewSession`. 35 shared tests passed on JVM and iOS simulator; two UI tests passed per platform. Android APK and unsigned iPhone app built. No live radio, invented peer count or delivery claim. |
| 3 | Native Bluetooth, peer/group discovery, wire protocol | Not implemented. Needs scan/advertise, GATT central/peripheral roles, bounded fragmentation/reassembly and radio state recovery on both platforms. |
| 4 | Destination acknowledgement | Not implemented. Must distinguish local adapter acceptance from authenticated destination receipt, including retries, duplicates and timeout. |
| 5 | Durable history/pending queue | Not implemented. Demo drafts and current core history/retry state are in memory. Requires schema, restart recovery, age/clock handling and migration tests. |
| 6 | Security/background/battery | Not implemented. Requires threat model, group authentication, encryption/key management and platform-specific lifecycle/energy measurements. |
| 7 | SOS/accessibility/physical acceptance | SOS UI preview only; no alert sent. Native text scaling and labelled controls are a baseline, not accessibility certification. Physical Android↔iPhone and controlled three-device relay tests remain mandatory. |

## UI implementation boundary

The local premium design boards remain the visual reference while Figma is
blocked. This is disclosed, not a claim that an editable Figma review happened.
Native system fonts preserve text scaling; project color tokens preserve the
graphite/white/cobalt identity. The native navigation shell adapts to each OS.

The demo creates only a local group, stores unsent drafts in process memory,
shows local activity and opens an explicitly non-transmitting SOS preview.
No Bluetooth/location/Internet permission is requested. No sample remote peers
or invented acknowledgements are shown. Restarting the process loses demo state.
`PreviewSession` is deliberately separate from `MeshTransportCoordinator` so UI
exploration cannot accidentally masquerade as a working emergency messenger.

## Release gate

Do not describe Murphy as an emergency-ready app. Before that claim is even
considered, tracks 3–7 need implementation and documented physical evidence.
Simulator/emulator checks only validate logic/UI, not Bluetooth interoperability,
range, background reliability, battery life, rescue or guaranteed delivery.
