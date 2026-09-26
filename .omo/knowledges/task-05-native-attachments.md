# Task 5 Native Attachments

- AE2 `IGridNode.getGrid()` is not attachment evidence. A ready standalone managed node receives an allocated Grid even
  with zero graph edges.
- Qualify a native attachment through exactly one `getInWorldConnections()` face edge, then correlate its opposite node
  with `GridHelper.getExposedNode` at the adjacent position and side.
- Cable and device compatibility require no class whitelist because both expose native nodes through the same AE2 host
  capability and connection semantics.
- A six-face Hub needs six distinct in-world managed nodes, each exposed on only one face. Never connect those nodes to
  one another or to a shared internal AE2 node.
- `GridHelper.createConnection` merges endpoint graphs into one Grid. It cannot represent Federation transport across
  isolated Bridge or Hub boundaries.
- Deduplicate repeated network membership by native `IGrid` object identity while retaining every face record and its
  independently owned boundary node.
- Task-specific persisted evidence must parse exact native topology artifacts; hashes and outer result projections do
  not prove face isolation, repeated-Grid grouping, floating rejection, or cross-grid rejection.
- Canonical implementation evidence: `.omo/evidence/task-05/attempt-20260913T210820250Z/result.json`.

## 2026-09-14 repair findings

- Fixture ownership must follow managed-node creation, not face direction. Multiple independent boundaries can expose the
  same direction, so an ordered collection of every `IManagedGridNode` is required for complete, idempotent teardown.
- Replaced-neighbor behavior needs a real lifecycle proof: resolve a valid native edge, remove the exposed AE2 host, wait
  for native connection settlement, and verify the original boundary resolves empty with `replacedAccepted=false`.
- Persisted identity syntax and cardinality do not prove provenance. Emit deterministic per-case identity facts into the
  isolated positive runtime log, require exactly one hash-bound log per properties artifact, and compare every persisted
  Grid identity with the independently parsed trace.
- A fully rebound distinct-value substitution must alter only the properties identity while retaining the original trace;
  acceptance indicates missing provenance correlation, while rejection proves the intended semantic boundary.
