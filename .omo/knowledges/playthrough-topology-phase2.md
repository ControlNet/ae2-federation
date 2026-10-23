# Partial Phase 2 topology harness

- `PlaythroughTopologyGameTests` calls `PlaythroughCraftingGameTests.runTopology`; it does not duplicate the serial
  crafting driver. `PlaythroughFactory` retains its Phase 1 constructor and adds a seeded layout constructor.
- Apply the small default item filter before parsing quest task SNBT. Parsing every task across all chapters can
  encounter component SNBT that vanilla TagParser cannot read, even if that task would later be discarded.
- The three cross-chapter controls passed Phase 1 in `nast-phase2-targets`: blank pattern (93 federated ticks),
  Functional Storage oak drawer (97), crafting table (95). They contain no container remainder recipes.
- Factory topology uses a central Router with cable/Switch arms. Native two-network geometry joins material and
  requester cells physically, preserving separate exact cell accounting. Bundles need whole formed Router/Switch
  end contacts. A P2P carrier can hold opposing Federation tunnels on one cable bus, paired with a real memory card;
  its grid is additional transport infrastructure, not a Federation member.
- A nonempty owed ledger snapshot while the native CPU is busy is an observed accepted projection push. Restore
  after 40 ticks and wait for powered networks, CPU visibility and a further 60 ticks before completion checks.
  A transiently invisible CPU must not be mistaken for completed work.
- Cable capacity counts real Endpoints in addition to member networks. Fixed 3-network multi-hop boundary fixtures
  add five/six Endpoints for demand 8/9. The refusal fixture powers its networks locally and installs real patterns,
  so absence of projection is not explained merely by an unpowered or empty factory.
- JSON explicitly lists missing Phase 2 dimensions/perturbations. Do not describe the implementation as complete.
  Validation, unresolved defects and exact commands belong in `build/playthrough/phase2-handoff.md`.
- Build once before starting parallel `compat_run.py --no-build` processes. Two independent Gradle builds of the
  same source set raced over compat class outputs; one failed with transient missing `.class` files. A serialized
  package build succeeded. Parallel server processes themselves remain supported.
- The default matrix passed 108 complete crafting cases and three clean capacity refusals in 115 seconds.
  Report snapshots can still contain a watched job tagged RUNNING after its debt has reached zero and CPU is idle;
  the existing Phase 1 teardown's delayed verification proves these references retire. Do not require the initial
  job list itself to be empty when validating Phase 1-compatible evidence.
- Final serialized-build verification repeated the default 111-case matrix in 113 seconds. The final 3x3 bundle
  supplement passed 18 cases. The deliberately one-tick planning-budget control exercised two fresh attempts and
  recorded `reproduced-invariant` / `pattern-visible`; it is not a mod defect.
- Cake remains layout-sensitive: the original Phase 1 Switch scene again stranded three buckets with owed=held=3,
  while seeded 3-network Router/Switch seed 0 passed with power pinned either EAST or WEST. Both seeded results
  held the exact buckets/cake/egg balance at requester with no debt/transit. Do not claim the known bug is fixed,
  or that power placement alone explains it. All reports are preserved under `build/playthrough/phase2-evidence`.
