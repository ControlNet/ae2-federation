# Merge of dev into feature/function-system (2026-11-24)

The owner asked to bring the mainline into the branch first rather than merge the branch out ("先试试把master的内容
merge进来"). master (0.0.9) was already fully in the branch, so the merge brings dev's 64 commits since 9b946c94
(2026-11-03 to 11-15: review fixes, addon API called directly, 2x2 Beam Converter art, GuideME 21.1.20+, Quantum Cube and
P2P fixes, compat pins on the 2026-11-07 releases).

## Conflicts and how they were resolved

- `compat/adapters/CraftingLogicCpu.java`: dev deleted the reflective helper (30a455ed, Advanced AE and Lightning Tech
  CPUs through their API). The branch's `pendingOutputs` (for `CraftingCpuLifecycle.stillToMake`) moved into
  `AdvancedAeCraftingCpusDirect` and `LightningTechCraftingCpusDirect`; both add-ons' logic classes have
  `getPendingOutputs(AEKey)` (checked with javap in Advanced AE 1.6.12 and Lightning Tech 2.1.3).
- `FederationDomainPolicyMenuHolder`, `NeoForgeEntrypoint`, `verification-metadata.xml`: both sides added; both kept.
- `neoforge-1.21.1/build.gradle`: dev's `addonApi` already carries Neo ECO 21.2.15, so the branch's `neoEcoApi`
  configuration (Neo ECO 21.2.11 + Prototype 1.4.2) is gone; Prototype 1.4.6 (the compat pin) joins `addonApi`.
  Its verification entry replaces 1.4.2's (jar sha512 equals `tests/compat/mods.json`); 21.2.11's entry is dropped.
- `docs/compatibility/production-runs.md`: dev's versions, the branch's ECO - N wording, counts measured after the merge.

## Semantic fixes git did not flag

- Neo ECO 21.2.15 added `Supplier<MultiBlockDefinition>` to `MultiblockBuilderUI.Config`; the ECO - N controller now
  passes `this::getBuildDefinition` (its own N definition). Without it the branch jar crashes the server when the
  controller is right-clicked on 21.2.15 (`NoSuchMethodError`).
- dev catches `LinkageError` around add-on CPU calls (direct API calls can meet another version); the branch's own
  `jobLink` and `stillToMake` now do too.

## Checks

- Unit 2254/2254; GameTest manifest 413/413 (`dev_gametests.py --manifest --ci`).
- Compat, local: neoecoae 58/58, neoecoprototype 74/74, addons-all 151/151.
- In game (`build/merge-dev-check/run.sh start|look|shot|stop`, Neo ECO 21.2.15 + Prototype 1.4.6, lone N1 controller of
  the N1 scene): `build/merge-dev-check/eco-n1-before-after.png` - before, the server crash; after, the controller screen
  and Neo ECO 21.2.15's Structure Builder with its preview of the N structure.
- A throwaway trial merge in a scratch worktree found the same results before the real one.
