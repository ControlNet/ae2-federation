# Remote crafting architecture: can addons be compatible automatically? (2026-10-26)

Findings and options only; no decision has been made. Context: the owner asked whether pattern projection
(`crafting-pattern-projection-design-2026-10-02.md`) is the right foundation, since every addon CPU, planner and provider
has needed its own handling (Neo ECO batching, lifecycle reflection for Neo ECO / Advanced AE / Lightning Tech, Data
Energistics counted dispatch, the Trinity pattern core). A second opinion came from Codex gpt-6-astra (read-only);
its claims below were checked against the sources.

## Verified facts

- AE2 19.2.17 `ICraftingProvider.pushPattern(details, inputs)` carries no job, CPU, return address or batch count.
  `CraftingCpuLogic` (19.2.17, lines 218-225) adds the expected outputs to `waitingFor` only after `pushPattern`
  returns true, so an output returned during the push is not yet awaited.
- Public `ICraftingCPU` exposes no waiting map, job link or block anchor; that is why `CraftingCpuLifecycle` reads
  addon CPUs by reflection.
- Data Energistics 3.3.3 `PatternProviderLogicMixin` grants counted batches only when
  `logic.getClass() == PatternProviderLogic.class` (decompiled lines 83, 105, 163, 198, 215). A wrapper or a subclass,
  including our `NativeProviderLane`, gets one run per push.
- Trinity pattern core outputs go to the Trinity's own CPUs (`insertExactIntoCpus`) or straight into its saved storage
  (`TrinityDataCoreBlockEntity.routePendingOutputs`). They show in the provider network's inventory through the
  depot's `MEStorage`, but never pass the grid `insert` where `CraftingReturnRouter` sits.
- Native control `trinityPatternCoreProviderJoined` (passes): with the consumer and the Trinity joined into one Grid,
  DE plans the order itself (a `TrinityCpuExecutablePlan`), `TrinityPlanAdmission` refuses that plan to the AE2 CPU
  (`NO_SUITABLE_CPU_FOUND`, `excluded=1`), and a Trinity CPU runs it. So "AE2 CPU + Trinity pattern core", which
  `trinityPatternCoreProvider` builds through Federation and which hangs, is a pairing the native stack never forms.
  Not tested: an AE2 plan pushed straight to a pattern core on one Grid.
  2026-10-27 (owner): the pairing never forms natively, so both Trinity pattern core tests were removed.
- The crafting rule shares whole providers; there is no per-pattern filter.

## Diagnosis

Projection cuts between C's CPU and P's provider. Addons couple planner, CPU, provider and output path, and the
AE2 contract at that cut lacks provider identity, job ownership and a guaranteed return path. Every addon that
relies on one of those needs its own code. No AE2 seam is traversed by every addon's crafting.

## Options

1. **Keep projection, narrow the promise** to the public provider contract: forwarding, plus outputs that pass P's
   grid insert. Addon code becomes optional acceleration, never needed for correctness. Pairings that native refuses
   could be gated using the native parity test below.
   Variant 1b (spike first): register the real provider object on C instead of a wrapper, so class checks and
   fast paths work natively. Returns then become demand-driven, without debt: move K into C while C's CPUs request K
   and P can make K, either at insert or by pulling P's stock. Cost: fungible settlement (C can receive P's existing
   stock early), and one object in two Grids' crafting services is untested.
2. **Machine port (Codex's main proposal):** the real provider sits on C and only machine I/O crosses networks. This
   gives native CPU/provider pairing. Costs: patterns move from P to C, and today's Provider/Endpoint does not do this
   yet, because its lane subclasses `PatternProviderLogic` and so fails DE's class check. It also cannot carry
   internal-crafting providers such as Trinity cores.
3. **Job delegation for appliances that own a CPU** (Trinity, ECO): P's native system runs the whole job. This must be
   chosen explicitly, never selected automatically from CPU presence. Inputs need escrow, because `submitJob` plans
   and extracts from P's grid storage.
4. Rejected: a real grid merge, which is symmetric and transitive and joins storage and power too; a virtual
   crafting-only grid, which pushes the matrix deeper into AE2; node-level projection, which restores neither
   identity nor the return path.

Scale: hub-and-spoke (one consumer, many providers) is linear in every option; only dense all-to-all reachability is
quadratic.

## Low-regret: native parity testing

`AddonCraftingScene.joinedBeforeOrder()` is already an oracle. Run every addon crafting scene twice, federated and
joined:

- native passes, federated fails: our bug;
- both fail or native refuses: an unsupported pairing;
- both pass but federated is slower: a performance gap.

Per-addon fixtures stay; per-addon assertions go away.

## Follow-up: real provider object plus hooks (owner's direction, 2026-10-26)

The owner prefers registering P's real provider object on C and adding the wrapper's functions by dynamic means.
Routes compared, with a second opinion from gpt-6-astra (`astra-real-provider-hook` report, checked):

- **Hook the executor, not the provider.** AE2's `CraftingCpuLogic.executeCrafting` (19.2.17 line 181) has the
  provider, the pattern and the expected outputs together. So any provider class dispatched by a native AE2 CPU is
  covered automatically.
  DE 3.3.3 has one central `CraftingDispatchCommitter`, whose `CraftingDispatchCommitRequest` carries
  provider, pattern, jobId, admission and accounting.
  Integration becomes per execution engine (AE2, DE, Neo ECO, AAE, LT), not per provider class.
- **Attribution needs a scope at real execution boundaries.** AE2 CPUs dispatch inside
  `CraftingService.onServerEndTick`. DE only registers a `GridCraftingDispatchParticipant` there, and dispatches later
  from `TrinityServerTickMetrics` (ServerTickEvent.Post, LOWEST). So DE needs its own scope.
- **Demand alone cannot replace the ledger.** Counterexamples: P/Q quantity ambiguity, local P work mixed with
  remote work, net-zero wait deltas, late output after cancel. Keep quantitative obligations, fed by executor receipts.
- **Rejected routes:**
  - all-class weaving: coremods 7 target explicit class names only, and `ILaunchPluginService` loads from BOOT, so an
    ordinary mod cannot do it;
  - runtime retransform or a javaagent: deployment is fragile;
  - decorated patterns: AE2 `AEProcessingPattern.equals` uses an exact class check, so equality becomes asymmetric;
    DE passes the batch count outside `pushInputsToExternalInventory`; the molecular assembler needs
    `IMolecularAssemblerSupportedPattern`.
- **Needed whatever route is chosen:** provenance of mirror registrations and refresh fan-out.
  `RealCraftingProviders` tells imports apart with `isProjection` today, and that breaks with real objects.
- **Not solved by any hook:** Trinity's direct return ingress.
- **Spike:** log-only probes in a separate compat-test mixin config, because `compatTestJar` excludes the testmod
  mixins. Measure context coverage, quantity coverage, protocol bypasses, duplicates and return-ingress coverage
  across baseline, DE, Neo ECO, AAE, LT and addons-all, against native/joined controls.
