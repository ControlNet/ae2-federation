# ECO - N communication interface (2026-10-20)

Commit `9adf4a6c` on `feature/function-system`. The user's rules: the controller links no cable. There are 1-4
interfaces, only in the side face (the 2x3 end where Neo ECO wants its ME interface), in any of its six cells. A 2x2
bundle links only when four interfaces fill a 2x2 square. Two defaults were my own choice: 0 interfaces forms and links
nothing, and auto-build places one interface at Neo ECO's interface cell.

## Structure check

- `NEComputationClusterCalculator.verifyStructure(level, C, tier, front, back, top, down, interfaceSide, expandSide,
  networkSwitchSide, mirrored)` is protected. The side face is checked by private `validateCasingOrNetworkSwitch` (column
  C+switchSide) and by overridable `validateInterface` (column C+back+interfaceSide).
- Trick that avoids copying the walk: check the six side cells ourselves (casing or our interface, count <= 4), then
  call super with `interfaceSide = networkSwitchSide = expandSide`. Super then checks the expand-side casing columns
  twice and never looks at the side face. A switch anywhere fails, because check 2 wants casing at C+expand.
- Unmirrored: interfaceSide = right, expandSide = left. For a controller facing north that expands east, the side face
  is west. The interface's outward face is found geometrically: the sign of interface - controller along
  `front.getClockWise().getAxis()`.
- NeoECO re-checks synchronously on block change. A GameTest sequence of form, unform and re-form can therefore
  finish in one tick (a report of 8 ms is real).

## Members and rendering

- The interface BE extends `NEBlockEntity<NEComputationCluster, …>` directly, with calculator
  `EcoComputationClusterCalculator::new`; `isValidBlockEntity` needs the calculator to be an `NEComputationClusterCalculator`.
- The block extends `NEBlock` directly, not `ECOMachineCasing`. It therefore has no INVISIBLE state, and
  `hideWhenFormed` is false.
- The formed controller model (`controller_formed_base`) is one 48x48x32 box over the whole head, including the side
  face. A formed interface is therefore drawn as a plate 0.02-0.04 px outside its outward face (both of Neo ECO's
  interface layers, emissive lights).
- `NEBlocks.*` are Registrate `BlockEntry`s and are not on our compile classpath. Use the calculator's `casing()`
  Holder. Compattest has no Neo ECO classes, so it looks blocks up by ID.

## Domain and bundles

- Interface node kind `COMPUTING_INTERFACE`: passable for cable capacity, power cost 0, no statistics group.
- Interface port `controller` <-> controller port `interface x, y, z`. Both come from `DomainDevice.Host.remotePorts()`;
  `claim` may return null (no ID).
- The cluster is complete before any member's `updateCluster` → `updateState`, so the controller's remote ports see
  every interface.
- `BundleBody` (`bodyFormed`/`bodyConnections`) lets `BundleContacts` accept non-Router bodies. The interface sets
  `RouterBlock.CONNECTED` bits only while four interfaces fill a square. After its state changes, it must invalidate
  capabilities on itself and its six neighbours, because `NEBlockEntity.updateState` uses `UPDATE_CLIENTS` only.

## Also fixed

The ECO - N controllers had no loot tables. `BlockLootContractTest` now pins one per blockstate, with
`neoforge:item_exists` conditions for the `eco_` blocks.

Evidence: `build/eco-interface-check/before-after.png`. Compat run: `python3 tools/compat_run.py --accept-eula neoecoae`
→ 43/43.

## Structure Builder (auto-build)

- Neo ECO's builder reads `getBuildDefinition()` from the controller. `EcoComputationBuild` wraps Neo ECO's
  `MultiBlockDefinition`: one instruction that replays Neo ECO's instructions into a `Swapping` `MultiBlockContext`,
  which swaps `ECOComputationSystem` for our controller and `ECOMachineInterface` for one communication interface
  (FACING EAST: Neo ECO's built structure grows west). `Swapping.isFormed()` is false so `onFormed` runs once, by the
  outer definition.
- Verified in game (build/eco-interface-check/build/built.png): Build forms the structure with a green interface.
- The panel's "network online" flag must count only foreign nodes: the controller's domain always holds its own
  interfaces, so it checks `domain.nodes().size() > 1 + remotePorts().size()`.
