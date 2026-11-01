# AE2 Crystal Science compatibility profile (2026-10-26)

AE2 Crystal Science (mod id `ae2cs`, package `io.github.lounode.ae2cs`) 1.21.1-1.2.1 from Modrinth
(`uJ9afomy/versions/taIhgdOA`, same as GitHub release `v1.2.1-1.21.1`). Profile `ae2cs` (group `addons`), test class
`CrystalScienceCompatGameTests`, also in `addons-all`.

```bash
python3 tools/compat_run.py --accept-eula ae2cs                 # 33/33, about 90 s with the default parallel run
python3 tools/compat_run.py --accept-eula ae2cs --tests resonatingRouting,resonatingRoutingJoined
```

## What matters to Federation

- Providers, all `PatternProviderLogic` subclasses, blocks and parts: Meteorite (`MeteoritePatternProviderLogic`),
  Resonating (`ResonatingPatternProviderLogic`), Mirror (`MirrorPatternProviderLogic`), Basic (`simple_pattern_provider`).
- Meteorite: `pushPattern` crafts an `IMolecularAssemblerSupportedPattern` at once (50 AE from the grid per craft) and
  inserts the outputs into its own grid's storage, else its return inventory. Up to `(8 << speedCards) + 128 *
  overclockCards` crafts per round; four `ae2cs:overload_card` = 520 a tick. Other patterns fall back to AE2's push.
- Resonating: its own `ResonatingPatternDetails` (data component `ae2cs:encoded_resonating_pattern`, record
  `EncodedResonatingPattern(sparseInputs, sparseOutputs, inputTargets)`, target = `GlobalPos` + face).
  `ResonatingPatternDetails.encode(ItemStack processingPattern)` converts an AE2 processing pattern; `withTarget(index,
  target)` sets one input's target. `pushPattern` requires `getAvailablePatterns().contains(details)`.
- Mirror: no pattern slots; `setMirrorTarget(new MirroredPatternProviderTarget(GlobalPos, null))` binds it to any
  loaded `PatternProviderLogicHost`, whose patterns it offers and pushes into its own sides.
- Machines (`AENetworkedSelfPoweredBlockEntity`): join the adjacent network and draw its AE. Side config
  (`SideConfigComponent` via `getMachineComponents().getService(...)`) defaults to `ALL` on every face; auto-export is
  off until set. Crystal Pulverizer: stone -> cobblestone, 8000 AE at 200 AE/t.
- ME Ender Emitter: `GridHelper.createConnection` to bound positions (Ender Linker = `addPosToEmitter(emitter, pos,
  true, false)`), auto-binds blocks/parts placed later within range (auto mode on by default); the menu's "link all"
  scans nearby chunks. It links only nodes on a controller-less network (or its own network short of channels). For a
  non-cable host it takes the first face node DOWN, UP, N, S, W, E; for a cable bus the center cable. A Switch face node
  is an ordinary node of the neighbour's network (`CANNOT_CARRY`), a Router face has none, so the emitter can only
  natively merge networks, as a cable would. Bind after the emitter has loaded: a bind on its placement tick is lost.
- `GridNodeMixin` changes `getMaxChannels` only for its own `CustomChannelProviderHost` (Emitter, Broadcaster).

## Upstream problems found (not reported by us)

- Needs AE2WTLib but does not declare it (`AEPlugin.<clinit>` loads `de.mari_023.ae2wtlib.api.gui.Icon`).
- `CableBusBlockEntityMixin.replacePart/addPart` calls `ResonatingMemoryCardHelper.tryApplyToPart(player, ...)` for a
  Resonating Pattern Provider part without a null check: `PartHelper.setPart(..., null, ...)` throws. Our runner then
  fails every test running alongside. `EndpointMachineScene` places provider parts as `FakePlayerFactory.getMinecraft`.
- Mixed routing (some inputs targeted, the rest to the sides) never pushes: `removeFromRemaining` leaves zero entries in
  AE2 `KeyCounter`s (no `dropZeros`) and `adapterAcceptsAllLocal` treats the zero insert as a refusal. Also fails on
  one Grid (`resonatingRoutingJoined`); the tests target every input.
