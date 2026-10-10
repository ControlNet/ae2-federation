# Federation Switch split (2026-10-10)

User decisions: the Switch (`ae2federation:switch`, ME Federation Switch / ME联邦交换机) takes over everything the
Router did (six faces, each an ME network or a Federation port, plus the domain screen); the Router keeps its block but
attaches Federation ports only. Provider and Bridge attachment paths stay as they are. No old-save migration
(pre-alpha). The split exists for later ideas; nothing else is planned on it yet.

## Code

- One block class, two registrations: `RouterRegistration.SWITCH` / `SWITCH_BLOCK_ENTITY` reuse `RouterBlock` and
  `RouterBlockEntity`. `RouterBlock.attachesNetworks(state)` (is the Switch) picks the block entity type and is passed
  to every `RouterFacePort`.
- `RouterFacePort(..., attachesNetworks=false)` creates no boundary node and no energy link; `resolve` is
  `reciprocal ? Federation : Disconnected`; the capability cache validity is an `alive` flag set in `initialize` and
  cleared in `destroy`. `getGridNode`/`boundaryNode` return null on a Router.
- Places that list the Router block also list the Switch: FE capability block list (`ProcessingRegistration`),
  `FederationPatternProviderBlockEntity.federationBlock()`, creative tab, pickaxe tag. `CableVisualConnections` uses
  `instanceof RouterBlock`, so the Switch joins cables densely with no change.
- The pre-rename registry aliases (`hub`, `federation_cable`, `multipart_bridge`) and the legacy-save GameTest with its
  sample were removed afterwards at the user's request (pre-alpha, no old-save support).
- Recipes: the Switch first took the old Router recipe; the same day (user-approved) its Import/Export Buses became
  Quartz Fibers (cables in corners, fibers top/bottom, Storage Bus left, Interface right, nexus core), so its inputs
  name what a face exchanges, as the Bridge's storage bus + quartz fiber + core do;
  Router is cables in corners, fluix crystals on the sides, nexus core. Since the same day every yield follows AE2:
  Switch x1, Router x1, Cable x8 (eight glass cables in), Nexus Core x2 (as the Formation Core).
- Art: none on purpose; `models/block/switch.json` names `block/switch/switch`, which is not shipped, so the block
  shows the missing-texture cube (also in guide scenes) until the artist draws it.

## Tests

- New GameTests `routerrefusesmenetworks`, `switchesjointhroughrouter`; manifest ids use a `switch.` prefix because
  `RouterTopologyContractTest` pins exactly six `router.*` entries (old Task 12 gate in `gradle/federation-qa.gradle`).
- Test scenes with an ME neighbour on a Router face moved to `RouterFixtures.placeSwitch` / `SWITCH`; Router-only
  placements (cable model, occlusion, "Router beside the path") stay Routers.
- `survivalrecipes` asserts both recipes; `SurvivalRecipeContractTest` and `GuidePagesContractTest`
  list the Switch. Lesson: a recipe change also needs the `survivalrecipes` GameTest, not only the JUnit contract.

## Guide

- Scenes: a neighbour script classified every guide Router; all but the one in `cable_connections.snbt` (cables on
  both sides) touched ME cables and became Switches. `router_hub.snbt` / `router_cable.snbt` were renamed
  `switch_hub.snbt` / `switch_cable.snbt`; the old Router page became `items/switch.md`, and a new short
  `items/router.md` uses `cable_connections.snbt`.
- UI strings that named the Router as the entry point now name the Switch (or "Switch, Router or Bridge"); the
  topology "Via N Routers" label counts both block kinds, so it says "Switches and Routers".
