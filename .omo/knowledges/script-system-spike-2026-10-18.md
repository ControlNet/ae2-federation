# Script system spike: lessons (2026-10-18)

Plan and results: [script-system plan](../plans/script-system.md). Code: `common/.../script/`, branch
`feature/function-system`.

- Keep IDs out of the script. Re-parsing gives fresh IDs; `Reconciler` maps them back by structure. When testing that
  "unchanged nodes keep their IDs", compare expression labels only: a statement's label is its printed text, which
  changes whenever any expression inside it changes.
- An `if` branch needs an ID of its own, distinct from the `if` statement, or graph ports and reconciliation collide.
- No loops and no recursion make `Cost.compute` (node count plus callees per call site) an upper bound on any run's
  steps; a test runs the samples in every world with exactly that budget. Keep this property when adding constructs:
  a loop would need its own bound.
- Purity is structural: a query's run gets `effects == null`, and an action passes none to the queries it calls. The
  Checker's "a query cannot call the action" is the friendly message; the runtime null is the guarantee.
- `unavailable` must never collapse to false: conditions and effect arguments that meet it stop the run (query answers
  unavailable, action stops), operators propagate it with Kleene `and`/`or`.
- Budget tests that pin an exact step count are brittle; the action test documents its count (30 steps with 10 iron).
