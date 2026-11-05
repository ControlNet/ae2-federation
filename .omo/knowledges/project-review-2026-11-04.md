# Project review (2026-11-04)

Owner request: a deep review of the whole project. Scope: `dev` at caa32f36; `feature/function-system` not covered.
Main code (`common/src/main`, `neoforge-1.21.1/src/main`) went through the code-review skill at max effort; everything
else was checked by script and by running it.

## Outside main code

### Tests that CI never runs
- 396 `@GameTest` methods, 297 test ids in the manifest's `gametest` backend (dev CI: `dev_gametests.py --manifest
  --ci`; release: `required_gametests.py` shards). Compare lowercased method names with `tests/scenarios/manifest.json`
  `testId`s to list the rest: 92 methods. Most are benchmarks (`perf*`, `scale*`, `p2prefreshscale*`) or harness
  self-tests (`harnessrequiredfailure`, `harnesstimeout`), which is intended.
- Behaviour tests in no manifest case, run in one batch on 2026-11-04: `EndpointFederationFaceGameTests` (11),
  `providerfederationfacerequired`, `BoundaryIdentityGameTests` (4), `storagenativeoperationsprioritycharacterization`,
  `p2pacrossdimensionschunkunload` (`required = false`) all pass. `packetauthority` passes alone but fails in a shared
  batch: it expects the global Policy revision to start from zero (1 expected, 3 after earlier tests).
  `identitynativenodebaseline` is a two-phase restart test that needs `-Dae2federation.identityPhase`, so it fails
  outside the restart harness by design.
- The three `gametest-restart` manifest cases (`identityrestart`, `domainidrestart`, `policylifecyclematrix`) run only
  through `federationVerify`, which no workflow calls for them: persistence across a real restart is not in CI.

### Bundle forming skips permission checks
`BundleInteraction.form` (the server side of `BundleFormPayload`) checks distance, the box and its cells, but not a
wrench in hand, spectator mode or `level.mayInteract`; `WrenchDismantle.dismantle` checks the last two. A bundle never
links to an ordinary cable (`BundleContacts.open`), so a modified client can bundle a 2x2 or wider block of someone
else's cables, even in a claim, and cut their links. `BundleInteraction.place` (extending a bundle) also sets cells
without `mayInteract` or a place event.

### Checked clean
- Lang: every literal key in code and `ui/*.xml` exists in `en_us.json` (652 keys, same set as `zh_cn.json`); keys
  without a literal use are read by the engine (block/item names, Jade config, mod name).
- Resources: blockstates -> models -> textures, loot tables (each block drops itself), recipes (every item except
  `federation_p2p_tunnel`, which comes from P2P attunement), recipe advancements, tags.
- Guide: 34 EN/ZH pages; links, scene files and item ids resolve. Translated pages under `_zh_cn` use the English
  pages' assets, so a script that resolves their relative paths reports false misses.
- AE2 range `[19.2.9,)`: javap over `neoforge-1.21.1/build/classes/java/main` finds 464 AE2 references, none missing
  from 19.2.9; the mixin target classes have identical member signatures in 19.2.9 and 19.2.17. No compat profile runs
  AE2 below 19.2.17, so the range is checked by signatures only.
- Python tooling: all 14 test files pass, including `test_dev_gametests.py` and `test_task37_resource_preflight.py`,
  which CI does not run.
- GameTests changed since the a01db57a sweep add no check after an action outside `GameTestChecks.once`.
- Mixins: no `@Overwrite` or `@Redirect`; the optional hooks use `require = 0`.
- CI: the newest runs of all six workflows are green. The weekly newest-modpack job skips NAST in seconds when its pin
  is already the newest release.

## Main code

A max-effort run was stopped at the owner's request and redone at high; its 14 candidates (V01-V14) went into the
high run's verify pass. Neither run found a diff to review, so both covered recent commits on these paths rather than
every file. Not fixed; findings only.

Gameplay:
- V06 `QuantumCubeBlock.java:145`: the Memory Card's `quantum_cube_card` component is not in AE2's
  `ae2:exported_settings` data-component tag (the repo ships no such tag), so AE2's card clearing (Shift+right-click in
  air, or copying another block's settings) leaves it. Right-clicking another cube then binds it to the old cube,
  breaking both existing pairs.
- V01 `DataEnergisticsCountedProjections.java:110` + `PatternProjection.java:285`: the consumer's return debt is
  recorded only when the real admission's `commit` returns true, but Data Energistics 3.3.5 counts a batch as committed
  once the inputs were taken, even when `commit` returns false or throws. On that failure path the consumer's job waits
  forever.

Permissions (multiplayer):
- `BundleInteraction.form` (above).
- `WrenchDismantle` is not a defect (checked 2026-11-05 against AE2 19.2.17 sources): AE2's
  `WrenchHook.onPlayerUseBlock` checks spectator, main hand, `InteractionUtil.isInAlternateUseMode` (shift),
  `InteractionUtil.canWrenchDisassemble` (`c:tools/wrench`) and `Platform.hasPermissions`, which is only "same level +
  `level.mayInteract`". Ours checks the same; neither fires `BreakEvent`. Both listeners use the default
  `receiveCanceled = false`, so a claim mod that cancels the right-click stops ours as it stops AE2's. AE2 applies
  `Platform.hasPermissions` to every world-changing tool (wrench, network tool, entropy manipulator, color applicator,
  matter cannon); that is the rule `BundleInteraction.form`/`place` should follow.

Rendering (visual only):
- V08 `RefinedDeviceRenderer.java:92`: the cube carrier computes its own beam style; an unpowered cable draws thin red,
  its carrier into the cube draws at full load.
- V14 `RefinedDeviceRenderer.java:80`: side contacts of a 2x2/3x3 bundle get no cube carrier.
- V13 `RefinedDeviceRenderer.java:139`: no `shouldRenderOffScreen`; a body across a section boundary loses its core when
  the owner member's section is culled.
- V02 `BundleLook.java:181`: `armRunsOn`'s same-body shortcut is wrong for Quantum Cube border members (they do not
  link), so the arm and the beam turn run onto a border row.
- V12 `BundleBeam.java:40`: two branches on different sides may overlap by one layer (`BundleSet.branch` checks only
  the new cells); the second branch's joint box is never drawn.
- V07 `BeamConverterBlock.java:59`: an unformed converter still occludes as a full cube, but its model leaves gaps
  (148/256 pixels on one end face), so neighbours' faces are culled behind the gaps.
- V10 `en_us.json:648`: the Jade toggle "Router, Switch and Beam Converter" also hides the Beam Former and Quantum
  Cube structure lines.

Checked clean: the RouterFacePort sweep reorder, the statistics idle checks, the creative converter removal, the
renderer's `covering()` lookup. Dropped: V05 and V11 (nothing to check), V09 (needs add-on API drift), V04.
