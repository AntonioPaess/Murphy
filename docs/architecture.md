# Murphy architecture

## Decision

Murphy uses a decentralized peer-to-peer data plane, not a leader-follower
topology. Every device owns its local session, applies the shared routing rules
and can forward messages for the group.

The first transport implementation uses bounded flooding: a node sends a
forwardable envelope to its currently attached peers except the peer that sent
it. TTL limits the distance and message identifiers prevent loops and duplicate
delivery. The forwarding policy can later evolve into a route cache or a
link-quality strategy without changing the shared envelope or event model.

## Why there is no required leader

- A leader would be a single point of failure in the exact emergency scenario
  Murphy is meant to demonstrate.
- Relay loss should degrade the route, not stop the entire session.
- Local decisions keep Android and iOS behavior aligned through the KMP core.
- The event history can explain each device's decision without a server.

## Optional control-plane coordinator

The product may elect a temporary session coordinator for non-critical tasks,
such as naming a group, proposing a route policy or synchronizing presentation
state. That role must remain replaceable and must never be required to deliver
an emergency message.

## Current trade-off

Flooding is intentionally simple and deterministic for the first implementation, but it
uses more radio activity than a learned route. Battery-aware forwarding,
connection quality and route expiry are later optimization layers; they are not
allowed to compromise delivery when the topology changes.

## Transport outcome semantics

### Live native callback path

Android BluetoothGatt and iPhone CoreBluetooth feed a shared `RadioSession` on
their main queue. Native GATT discovery reads a public identity, verifies group
UUID scope, subscribes to notifications and exchanges identity frames. Only
real links count as peers. Group broadcasts deliver locally and relay using
`MeshSession`/`MeshRouter`, excluding the incoming peer. The session dedup cache
is capped at 4,096 IDs and is not authenticated replay protection.

Native transfer queues are bounded to eight packets per link with a total
60-second deadline; a stalled transfer closes/resets its link rather than
misassigning a late callback to another packet. Reassembly also has a fixed
60-second deadline and an 8-KiB cap. This is not durable retry/recovery.

Both platforms scan in bounded 10-second foreground windows. Background closes
links; there is no background service/state restoration promise yet. Open group
UUIDs prevent accidental same-name mixing, not impersonation. The wire is
unencrypted/unauthenticated; sensitive use and emergency reliance are forbidden
for this build. [Wire details](radio-protocol.md).

### Separate coordinator contract

The suspend-based coordinator below is tested independently. Its retry policy
and scheduling API are **not integrated into the live native callback path**.
Do not infer automatic retry, destination receipt or durable state from these
core tests.

The coordinator attempts each eligible peer in deterministic order. An expected
radio error is reported as `BleSendException`; it is recorded for that peer and
does not abort the remaining sends. Cancellation and unexpected exceptions
propagate. Calls to the coordinator and its session must be serialized by the
platform owner; their mutable state is not thread-safe.

`Forwarded.peerIds` contains sends accepted by adapters. `Forwarded.failures`
contains failed attempts; the accepted list can be empty if every attempt failed.
`NoRoute` means there were no eligible attached connections, including when only
the source peer remains. It is a local observation, not proof of global network
reachability.

`MessageForwarded` records the router's forwarding decision. Separate
`MessageSendAccepted` and `MessageSendFailed` events record adapter outcomes;
`MessageNoRoute` records the absence of an eligible neighbour. Timestamps belong
to the caller-supplied processing batch. Accepted sends are not destination
acknowledgements. Delivery acknowledgements and durable storage remain future
work. A repeated incoming envelope is still deduplicated, even after a failed
send; transport retries use a separate pending-send path.

## Bounded transport retries

An expected failed send schedules the already-routed envelope for that message
and peer. Defaults are three total attempts (including the initial attempt),
one second between failures and 128 entries across the coordinator. On queue
overflow, the new entry is rejected without evicting existing work.
`MessageRetryScheduled` records the attempt count and deadline;
`MessageRetryStopped` explains exhaustion, overflow or local age expiry.

The platform calls `retryPending(atMillis)` serially with a consistent clock.
Only due entries for attached peers are attempted. Disconnection retains an
entry without spending the attempt budget until its expiry; attaching a replacement
connection for the same peer enables recovery only before expiry. Success removes
the entry. Cancellation or
unexpected exceptions propagate while preserving the current queued entry.

Retries bypass the router because the TTL was already reduced for that hop.
They neither resend to peers that succeeded nor disable incoming deduplication.
Failures and accepted sends in the retry result include both message ID and
peer ID, allowing several pending messages for the same peer to be distinguished.

The queue is in memory. Each entry expires 60 seconds after its first local
failed send by default (`maxPendingAgeMillis`). The deadline never extends after
another failure and does not use the remote `createdAtMillis`. Hop TTL and local
queue lifetime are separate limits. Deadline addition saturates at `Long.MAX_VALUE`.

`expirePending(atMillis)` removes entries at or past the deadline even for
detached peers, records `EXPIRED` once per removed entry and returns them.
`retryPending` expires all work before considering send deadlines; its `expired`
list is separate from exhausted attempts. Scheduling a failed send also reclaims
expired capacity before checking queue fullness. Cleanup is caller-driven, not
an autonomous timer. `pendingSends()` remains an inspection snapshot; it does
not sweep stale work. Use one consistent monotonic local clock for processing,
retries and expiry, and serialize all calls. A backward clock can delay cleanup.

Expiry drops stale queued attempts, not an in-flight send, and does not mark a
destination delivered or erase session deduplication/history. Messages
with no eligible peers are not queued, and the initial forwarding batch does
not preserve unattempted sends if interrupted. Accepted sends do not await
destination acknowledgements. These limits must be addressed before presenting
the prototype as a reliable emergency messenger.
