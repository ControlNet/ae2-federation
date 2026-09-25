# Native attachment boundary

## Attachment qualification

AE2 assigns a ready standalone node to an `IGrid`, so node existence and `getGrid()` are not attachment evidence.
Federation accepts an ME boundary only when all of these native facts hold:

1. The boundary node owns exactly one connection.
2. That connection is an in-world edge on the boundary's configured face.
3. `GridHelper.getExposedNode` still resolves the connected opposite node at the adjacent position and side.
4. The boundary and opposite nodes currently report the same native `IGrid` object.

This rule is independent of the neighbor owner's class. A native cable part and an ME block device pass through the
same exposed-node and edge checks. Air, ordinary blocks, floating nodes, replaced nodes, and multi-edge ambiguous nodes
fail closed.

The replacement proof first resolves a real ME chest attachment, removes that exposed native host, waits for AE2's edge
state to settle, and then resolves the original boundary node again. The stale boundary must return no attachment and
records `replacedAccepted=false` in the existing `ports.reject-floating-node` case.

## Router isolation

A Router candidate owns six distinct managed in-world boundary nodes, each exposed only on its corresponding face. The
nodes are not connected to one another or to a shared internal native node. Each valid face record retains its own
direction, boundary node, neighbor node, and current `IGrid` identity.

Distinct native neighbors remain distinct Grids. If two faces reach a network already joined outside the Router, both face
records remain present while membership views group them by native `IGrid` object identity. This deduplicates one native
network without collapsing physical face ownership.

`GridHelper.createConnection` is not a Federation transport primitive. AE2 uses it to merge the connected node graphs,
so the Router boundary rejects requests to connect face nodes. Bridge and Router product implementations in Tasks 11 and 12
must adopt this boundary rather than creating native cross-boundary edges.

## Rendering boundary

Native cable-extension rendering can continue to use AE2's host cable connection type and attachment connection length.
Task 5 adds no visual implementation; rendering remains deferred to Task 33.

## Evidence provenance

Each Task 5 GameTest emits deterministic `AE2F_PORT_TRACE` facts to its isolated positive runtime log before writing the
native properties artifact. Persisted consumption requires exactly one hash-bound positive log for every native artifact
and correlates every persisted Grid identity against the independently parsed trace. Structural distinctness alone is not
identity provenance; a fully rebound mutation that changes one property identity while leaving its runtime trace intact
must fail.
