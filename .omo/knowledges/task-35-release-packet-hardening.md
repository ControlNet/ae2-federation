# Task 35 release and packet hardening

## Production boundary

- `releaseServer` is a ModDevGradle server run bound to `sourceSets.main` and an explicit `loadedMods` list containing only
  `ae2federation`. This proves the distributable path without loading the test mod or Applied Flux compatibility source set.
- Both binary and sources archives are inspected semantically. Testmod paths, scenario resources, debug registrations,
  optional-addon classes, and test-only symbols are forbidden; production metadata and Mixin registration must be exact.
- The isolated server smoke waits for Minecraft's `Done (` receipt, records the real archive hashes, terminates the process
  tree within a bound, and removes its runtime directory.

## Packet boundary

- Mutation-bearing Fabric policy actions do not use LDLib2's replayable generic RPC IDs. A project-owned NeoForge payload
  carries a closed action ID plus the server-issued container ID, random menu nonce, monotonic sequence, exact Fabric
  reference, and expected Policy revision.
- Mutation requires the server thread, the player's current `ModularUIContainerMenu`, the exact UI holder, matching
  container/nonce/sequence/context/revision authority, and a still-valid session. The sequence advances only after accepted
  dispatch, so both a menu A packet replayed against menu B and a duplicate menu B packet fail before mutation.
- Observation clients must receive OPEN before accepting snapshots and retain closed-session tombstones until reset.
  Snapshots cannot regress topology, Policy, or data revision. Native transport flows are fully validated before meter
  window, deduplication, ordering, or revision state changes.
- Resource observation amounts are capped at `9_000_000_000_000_000`, preserving exact integer behavior while rejecting
  hostile oversized values at the state boundary.

## Evidence pattern

- Task 35 uses exactly five cases: `release.server-side-load`, `release.no-test-content`, `release.optional-absent`,
  `packets.reject-malformed`, and `packets.reject-out-of-context`.
- Schema-v3 evidence contains dedicated-server release facts, executable codec/lifecycle/meter rejection facts, and a
  production-menu GameTest receipt for menu A-to-B replay, context/revision rejection, zero mutation, server-thread
  execution, and exactly-once valid dispatch. Persisted verification requires exact field sets and correlates every runtime
  property with its execution log before checking semantics.
- The adversarial self-test fully rebinds copied evidence after mutating server load, archive separation, optional-addon
  absence, codec rejection, or both the runtime property and matching log trace. Every mutation must fail for the intended
  semantic reason.
- JavaExec probes that initialize Log4j must use and remove an evidence-local working directory. Otherwise a rollover log
  can appear after source capture and correctly make the current-source evidence stale.
- A stale request that is structurally bound to the current menu can still fail because another client advanced the
  authoritative Policy revision. That rejection must transition the server session to `STALE_REVISION`; returning only a
  payload-handler result leaves the synchronized UI editable and prevents deterministic conflict cleanup.
- Final current-source evidence is
  `.omo/evidence/task-35-final-current/attempt-20260922T015644119Z/result.json`.
