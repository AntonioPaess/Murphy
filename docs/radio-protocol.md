# Murphy development radio protocol v1

Native code path: Android BluetoothGatt + iPhone CoreBluetooth → KMP
`RadioSession` → `MeshSession`/`MeshRouter`. Both native adapters serialize
callbacks on the main queue. No server, Internet permission or fabricated peers.

This wire format is **unencrypted and unauthenticated**. Do not use sensitive
data or depend on it for safety. A public group UUID is scope, not a secret,
authorization, identity proof or resistance to malicious injection/replay.
Security is a separate release gate; physical interoperability is still unverified.

## GATT contract

| UUID | Role |
| --- | --- |
| `725be150-3d62-4e2a-a26f-3edca38d4001` | Advertised primary service |
| `725be150-3d62-4e2a-a26f-3edca38d4002` | Readable identity packet; supports read offsets |
| `725be150-3d62-4e2a-a26f-3edca38d4003` | Central → peripheral, write **with response** |
| `725be150-3d62-4e2a-a26f-3edca38d4004` | Peripheral → central notifications; standard CCCD |

Each phone can be central and peripheral. Discovery scans only this service for
10 seconds, with at most 64 observed device handles and eight clients/eight
servers. Handshake expires after 15 seconds if incomplete. These development
caps are not resistance to deliberate denial of service.

1. Creator generates a random group UUID and advertises the service.
2. Scanner connects and reads the unfragmented identity (under 512 bytes).
   Without a group, it records a discoverable group and disconnects. A discovered
   group is not counted as a connected peer.
3. User joins that UUID. Same-named groups remain separate.
4. In a matching group, central subscribes to notifications and queues its own
   identity on the write characteristic before any messages. Peripheral attaches
   the peer only after subscription plus identity. Native connected/handshake
   callbacks establish peer state; no peer is invented by the UI.
5. Packets are fragmented over the write/notify characteristics. When both roles
   coexist for a peer, lexicographic node UUID order selects the sending role.
   This is a per-link tie-break, **not leader-follower architecture**.

## Packet encoding

All integers are big-endian. Fields in exact order:

| Field | Representation |
| --- | --- |
| Magic/version | Bytes `4d 55 52 01` (`MUR`, version 1) |
| Kind | Unsigned byte: 0 identity, 1 message |
| TTL | Unsigned byte; identity 0, message 0…8 |
| Group name, origin node ID, display name, group ID, message ID, destination, body | Seven strings, each u16 byte length followed by strict UTF-8 |
| Creation time | Signed 64-bit local monotonic milliseconds; informational, not comparable between devices |

Node/group/message IDs use canonical UUID-shaped strings (native code generates
lowercase). Destination `*` broadcasts to the group; a UUID is reserved for
direct destination routing. Normal UI currently sends broadcasts only.
Identity has empty message ID/destination/body and TTL 0.

Names: nonblank, ≤40 UTF-16 units, no C0/DEL. Native entry normalizes names to NFC.
Message: nonblank, ≤1,000 UTF-16 units, no C0 except newline/CR/tab. User-authored
content is not translated. Packet limit: 8,192 bytes. Decode rejects unknown
version/kind, overlong fields, invalid UTF-8, truncation and trailing bytes.
Malformed input returns rejection, not an exception across native callbacks.

## Fragments and resource limits

Every ATT value is at most **20 bytes**, usable with the default 23-byte MTU:
one flag byte (START=1, END=2), u16 sequence (starts at 0), then 1…17 payload
bytes. START resets the per-connection assembler. Frames must arrive in order;
bad flags, length, sequence or an 8-KiB overflow reset/reject the assembly.
Reassembly expires **60 seconds from START**, not renewed per fragment; a
backward local clock also resets it.

Each ordered link has a serial outgoing queue capped at eight packets. Its
60-second total deadline starts at enqueue, including queue wait. Android advances
central writes only from native write callbacks; server notifications are
globally serialized and rotate between queued peers, waiting for each native
callback as required by the [Android GATT contract](https://developer.android.com/reference/android/bluetooth/BluetoothGattServerCallback#onNotificationSent(android.bluetooth.BluetoothDevice,%20int)).
A disconnect while a notification is in flight resets the server epoch rather
than reusing an ambiguous delayed completion. iPhone central waits for
write callbacks; peripheral pauses when `updateValue` returns false and resumes
when CoreBluetooth signals readiness. Stalled transfers fail the link; iPhone
peripheral resets radio because it cannot explicitly disconnect a subscribing
central through CoreBluetooth. These are bounded development policies, not
measured throughput or battery guarantees.

## Routing and outcome semantics

Messages from unattached peers/foreign groups or malformed packets are ignored.
Broadcast is delivered locally, then optionally relayed to attached peers except
the incoming peer. Initial UI TTL 4 is decremented before the first hop; TTL 0
still delivers locally but does not relay. Deduplication caches 4,096 IDs per
session; eviction/restart permits old IDs again. This is not replay security.

`Bluetooth accepted · receipt unconfirmed` means only that the native transport
accepted the fragments. Notifications in particular are not destination receipts.
`Received on this device` describes the receiver's local observation, not an ACK
sent to the origin. The sender never claims Delivered/ACK. No-peer sends show
`Not sent · no connected peers` and are not automatically retried later.

Current callback-based native queues do **not** use the separately tested
`MeshTransportCoordinator` retry scheduler. Native reconnect/retry durability,
destination ACK, authentication/key exchange and storage are subsequent tracks.
History/messages (100 each), nearby groups (32), and event trace (256) are
memory-only. The foreground lifecycle closes links on background. SOS is
unavailable, not simulated as a successful emergency alert.

## Acceptance still required

Builds/unit/UI tests do not validate physical BLE. Use the user's iPhone 16 Pro
and Android phones for both direct directions, large UTF-8 packets, permission
denial, radio toggles, disconnects and controlled three-node relay. A developer
link-exclusion harness is not implemented yet: do not infer relay proof from
distance alone. See [testing](testing.md).
