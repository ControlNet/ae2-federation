# Task 23 Chain Sharing

## Design

- Compile direct storage dependencies into effective `(consumer, native origin, capability)` relationships with a bounded monotone frontier.
- Intersect operation/filter authority along each chain and union independent alternatives without storing complete paths.
- Carry policy revisions, Fabric references/topology revision, source generation, and compilation revision into each effective relationship.
- Keep direct policy activation unchanged. Derived authority mounts native origins but never synthesizes or activates a direct rule.
- Reject any relaxation that returns a native origin to itself and deduplicate diamond paths by effective origin key.

## Native fixture lifecycle

- Four colored AE2 networks form a diamond through four Bridge parts.
- Bridge coordinates/faces are AB west, AC east, BD east, and CD west.
- AE2 may complete cable-bus attachment after `addToWorld`; place Bridges serially and invoke the public shape-update lifecycle while polling each membership candidate.
- Do not require simultaneous `isActive()`/`hasGridBooted()` across all four rebuilding networks. Confirm four distinct settled identities before Bridge insertion.

## Verification

- Focused compiler and mount contract tests pass.
- All five native cases pass serially: diamond deduplication, filter union/intersection, re-export toggle, direct activation separation, and cycle rejection.
- Task 23 manifest entries and persisted evidence semantics are wired into both producer and consumer verification paths.

## Final verification (2026-09-20)

- Authority must preserve operation/filter correlation. Store one effective filter per operation; unioning operation and
  filter sets independently creates an invalid cross-product across alternative routes.
- Build each colored native network from its storage-adjacent cable outward and add one cable only after all four current
  identities settle. Bulk same-tick topology creation can produce `AMBIGUOUS_MERGE` from independently initialized nodes.
- The canonical five-case aggregate passed at
  `/tmp/opencode/task23-evidence-8/attempt-20260919T164317377Z/result.json`; persisted consumption and the Task 23
  adversarial self-test also passed, followed by the repository build and Java diagnostics.
