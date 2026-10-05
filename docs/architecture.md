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

Flooding is intentionally simple and deterministic for the first demo, but it
uses more radio activity than a learned route. Battery-aware forwarding,
connection quality and route expiry are later optimization layers; they are not
allowed to compromise delivery when the topology changes.

## Transport outcome semantics

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
acknowledgements. Delivery acknowledgements, durable storage and retry queues
remain future work. A repeated envelope is still deduplicated, even after a
failed send; retry support must use a separate pending-send path.
