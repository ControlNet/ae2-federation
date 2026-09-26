# Task 32 scoped observability

## Implemented

- Fabric-scoped stable typed IDs are SHA-256-derived from the Fabric ID, entity kind, and native identity.
- Snapshots defensively copy, bound, scope-check, and canonically sort every state collection.
- Deltas require a strictly increasing data revision; clients accept only exact scope, topology revision, and contiguous base revision.
- Native transport windows deduplicate operation IDs within a bounded retention window and collapse payload events to an explicit resnapshot state on overflow.
- Observation subscriptions are keyed by player, menu session, and exact Fabric generation. Re-subscribing supersedes the previous generation; close and sweep are idempotent.
- Storage metering occurs only after an authorized native delegate accepts a positive `MODULATE` amount.
- Energy metering occurs only for positive `MODULATE` amounts accepted by an outermost demand. Recursive route hops and simulations do not meter.
- Level unload removes the level-scoped observability owner after energy shutdown.
- NeoForge snapshot and delta payloads are bounded, registered as clientbound payloads, and rejected when their scope,
  topology revision, or base revision does not match current client state.
- Menu removal, player logout, authority invalidation, and level unload all close observation ownership idempotently.
- `FabricStateProjector` reads live Provider and Endpoint registries. Provider identity includes its instance epoch;
  Endpoint identity includes its instance epoch; Lane lock, busy, pending-send, and aggregate return-buffer state comes
  directly from native AE2 logic. Aggregate return buffers are represented as non-exact task state, not fabricated flows.
- Provider observation entries are removed when the mapped provider closes and are also cleared on level unload.
- Five canonical `observe.*` GameTests and a persisted fail-closed semantic verifier cover metering, scope,
  cleanup, stale deltas, and cross-Fabric rejection.
- Snapshot and delta envelopes carry one server-issued `ObservationSession`: player, menu session, scoped subscription ID,
  subscription generation, exact Fabric reference, and nonce. Client projections are keyed by that complete identity.
- Deltas carry a canonical immutable full replacement projection, so additions, updates, and removals all apply atomically;
  rejected scope/session/topology/base-revision input leaves the previous projection intact.
- The canonical cross-Fabric case opens production menus for two real `ServerPlayer`/Fabric pairs, captures both
  server-issued sessions, then sends a validly encoded Fabric B rebound delta under Fabric A's menu identity. Rejection
  preserves byte-identical/value-identical full client projections, server projections, subscriptions, Policy, and topology.
- Payload decoding rejects total-size overflow, trailing data, negative revisions/generations/amounts, duplicate IDs, and
  cross-Fabric wire IDs before state mutation. Client application is scheduled through the NeoForge payload context.
- `LevelObservabilityService.recordAccepted` requires a caller-supplied `OperationEventId`: each real storage, energy,
  Processing send, or aggregate-return boundary allocates one unique random ID per new physical operation and reuses it
  across every Fabric scope/report for that operation. `NativeTransportMeter` rejects a repeated same ID while accepting
  distinct IDs with equal resource and amount; reallocating during repeated observation would incorrectly defeat deduplication.
  Aggregate returns use `AGGREGATE_LANE_RETURN` and can never claim exact Batch completion.
- Energy accounting uses exact checked nano-AE conversion; incompatible fractional or overflowing values throw rather than
  round or saturate.
- A server-tick sweep proactively removes invalid authority and replaces overflowed streams with a session-bound snapshot;
  successful replacement clears both subscription and meter overflow state.

## Verification checkpoint

Run:

```bash
./gradlew :neoforge-1.21.1:test --tests 'space.controlnet.ae2federation.observability.*' --tests 'space.controlnet.ae2federation.energy.EnergyExactAccountingTest' --rerun-tasks --no-configuration-cache --warning-mode=fail
```

Expected: all observability unit tests pass and Gradle reports `BUILD SUCCESSFUL`.

Canonical evidence:

`.omo/evidence/task-32-final-review/`

The strict build, exact five-case producer, persisted consumer, runtime-receipt verifier, semantic/schema mutation probes,
and Tasks 14/24/29/30/31 serial regressions pass. The canonical scope, cleanup, and cross-Fabric cases open the production LDLib2 menu
for a real GameTest `ServerPlayer`; a headless packet listener suppresses client delivery while independent receipts retain
the player, menu session, Fabric scope, projection counts, Processing acceptance, and aggregate-return attribution.
NeoForge's server-side container-close event owns menu subscription cleanup because LDLib2's `ModularUI.onRemoved()` is a
client-screen callback. Task 33 remains responsible for rendered client UI behavior.
