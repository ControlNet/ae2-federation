# Follow-up audit: unpaired P2P and actual global provider counts

Baseline: `2f0f99fc590a8e99e1e6b78d67db3aac812a345f`, branch/upstream `dev` / `origin/dev`.
User invoked the repository audit-and-fix skill for continued repair and delivery. User-owned edits under
`.codex/skills/`, `docs/ideas/`, and unrelated knowledge files remain outside audit commits.

## P2P-ZERO (reproduced, fixed)

Coalesced dirty groups use Grid/frequency keys. Multiple active unpaired tunnels on one carrier all have frequency
zero. The old drain grouped their changed callbacks and published that shared list; each input interpreted itself as
an input and advertised virtual links to other members. No memory-card pairing was needed. This could connect
otherwise independent Federation fronts merely because their unconfigured tunnels shared a carrier.

The repair keeps physical front publication but supplies no virtual links for frequency zero. Both previous and
current groups remain dirty on configuration changes. Three real active unpaired tunnels reproduce the old failure
in both a batch and an isolated run. The repair passes that case, the independent paired groups case, and native
projection request (3/3). Another agent independently reviewed code, tests, and counterexamples.

## GLOBAL-FIXTURE (CI assertion disproved)

Remote Compatibility run 38106378723 on the baseline failed MegaCells remoteCrafting: expected two providers,
actual three; addons-all had four. The assertion counted all registered providers, not the two installed fixture
providers. Actual MegaCells 4.11.0 DecompressionService bytecode implements ICraftingProvider and its constructor
unconditionally calls ICraftingService.addGlobalCraftingProvider(this), even before modules create any patterns.
Discovery of that legitimate global service is intended behavior.

The regression now checks exactly one native consumer medium for each actual fixture provider pattern, and exactly
two distinct medium identities across those patterns. It still executes a real native craft and retains exact
material/output/debt assertions. It does not accept a weakened >=2 provider count. Native baseline passes. The
real MegaCells profile is separately rerun; final combined outcomes belong in the integration receipt.

Evidence: `.omo/evidence/audit-and-fix-2026-10-11/`, including remote failed log, P2P red/green and MegaCells bytecode.
Commands: `python3 tools/dev_gametests.py p2punpairedstayseparate p2prefreshindependentgroups projectionrequest`;
`python3 tools/compat_run.py --accept-eula megacells`. No new save format or chunk-loading behavior.
