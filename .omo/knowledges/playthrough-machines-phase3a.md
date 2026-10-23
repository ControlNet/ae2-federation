# Phase 3a machine playthrough

- Adapters live beside Phase 1 in the compat source set. Add a class implementing `PlaythroughMachineAdapter`
  and one constructor entry in `PlaythroughMachineAdapters.REGISTERED`; no main-mod changes are needed.
- `machines` selects adapter IDs; `none` preserves the Phase 1 frontier. Exact recipe types distinguish furnace,
  smoker and blast furnace even though Phase 0 labels them all `cooking`.
- Keep Phase 0 provided resources available. Only stand-ins lose their unlimited supply when their recipe becomes
  executable. Removing every machine-produced item from provided resources incorrectly forces natural diamonds
  through dust/hammer conversion recipes and changes the resource policy.
- Native Pattern Provider `setSide(level, pos, face)` behaves like a wrench click: starting from ALL, WEST selects
  EAST output. A one-sided provider is necessary beside a return hopper so inputs do not enter the collector.
- The machine shelf uses a dense factory backbone, native provider/interface input subnets with Quartz Fiber power,
  or Federation Provider/Endpoint input subnets. Storage Buses insert inputs; hoppers collect actual machine output.
  The baseline always uses native AE2 processing, including when the federated comparison selects the endpoint path.
- A vanilla stick burns 100 ticks in a furnace and 50 in a smoker or blast furnace. Include exact fuel in processing
  patterns and use a separate side Storage Bus. Initial coal stacks obscure extraction and buffer accounting.
- Inscriber presses are fixed equipment from the provided-resource view. Reject unavailable fixed catalysts during
  adapter preflight, so that recipe stays a stand-in instead of breaking all downstream planning.
- A Charger output that is itself a charging input cannot be extracted through its automation capability. NAST's
  `ae2cs:charged/purified_redstone_crystal_from_redstone_crystal` produces `appflux:redstone_crystal`, which charges
  again into `appflux:charged_redstone`. Reject the intermediate recipe with a reason; encoding it as a one-step
  processing pattern causes identical baseline/federated timeouts with the later product in storage.
- Processing pattern publication must be checked for every intermediate before planning, just as crafting patterns
  are. Checking only the root and crafting-pattern outputs introduces missing-input publication races.
- `tools/compat_playthrough_machines.py` validates complete chapter chunks with Phase 1 accounting, requires both
  machine paths and matching task/recipe selections, and writes the combined machine reports.
- GenericStack and the unlimited source accept AEKey quantities. Extra physical requester cells are declared by
  `storageCells` (AE2 fluid cells by default for fluid outputs). Their inventories join the full balance checks;
  chemical adapters supply the pack cell ID. Runtime coverage in Phase 3a remains item-only.
- Runtime commands, complete results and open limitations are recorded in `build/playthrough/phase3a-handoff.md`.
- Phase 1's original one-million-tick GameTest ceiling can expire before the 1400-second wall-clock guard when
  sprinting real machine chains. A 40-task chunk starting at offset 80 hit that ceiling during the 64m Applied Flux
  core after 21 completed pairs. Use the raised 20-million-tick ceiling and 20-target chunks for this range; keep
  the original timed-out artifacts separate from complete mergeable evidence.
- `PlaythroughMachineDiscovery` lets a registered adapter inspect raw RecipeManager entries whose outputs were
  invisible to Phase 0. It adds observed item producers through the probe's small registration hook and reruns
  resource analysis if needed. For known outputs with unreadable generic ingredients, concrete adapter inputs
  provide the reachability witness. Non-item raw inputs still require the adapter's explicit resource policy.
- Keep `requestGameToSprint` and the GameTest timeout on the same constant. Raising only the annotation leaves
  long jobs at normal 20 TPS after one million ticks. Phase 3a now shares `MAX_TICKS` between both paths.
- Cooking Storage Buses need explicit filters: the horizontal bus accepts only sticks; the top bus accepts only
  the selected recipe ingredient. Coal-to-graphite otherwise routes its coal into the fuel slot and strands sticks
  in the input slot. The shared baseline/federated failure was detected by the machine-buffer and ledger checks.

Final evidence: 138 tasks per machine path, with 137 both-pass and one pre-existing quantum-entangled-singularity not-bootstrappable result. All five adapters passed on projection and endpoint paths. The separate machines=none Quartz Fiber/Crafting Unit regression passed both targets and restored stand-ins with no enabled machine classes. Reports and exact replay commands are in build/playthrough/phase3a-handoff.md.
