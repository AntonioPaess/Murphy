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
