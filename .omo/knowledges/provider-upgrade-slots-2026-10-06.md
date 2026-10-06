# Federation Pattern Provider upgrade slots and Applied Flux (2026-10-06)

The owner asked that the Provider's upgrade support be inherited from AE2's own Pattern Provider, never a list of
addon cards. Layout B of the GUI canvas was chosen: an **Upgrades** column between Inventory and Return buffers.

## How Applied Flux 1.21-2.1.x does it (decompiled)

- `MixinPatternProviderLogic.initUpgrade` runs in every `PatternProviderLogic` constructor:
  `UpgradeInventories.forMachine(host.getTerminalIcon().getItem(), 1, …)` and adds an `EnergyTicker` as
  `IEnergyDistributor` node service. `MixinPatternProviderLogicHost` makes every `PatternProviderLogicHost` an
  `IUpgradeableObject`. Save/load/drops/clear use the logic's `"upgrades"` tag.
- The card is registered in AF's `FMLCommonSetupEvent` only for `AEBlocks/AEParts.PATTERN_PROVIDER` (group
  `group.pattern_provider.name`, an AF key) and the Interface. AE2 has no public per-machine card list: enumerate
  `BuiltInRegistries.ITEM` with `Upgrades.isUpgradeCardItem` + `getMaxInstallable(card, AEBlocks.PATTERN_PROVIDER)`.
- `EnergyTicker`: `AFUtil.getSides(host)` is all six sides for a BlockEntity host, none for other hosts (our
  `LaneHost`). `EnergyCapCache.checkGrid` skips a neighbour whose `getGridNode(side)` is in the sender's own grid.
  `EnergyHandler.send` tries AF's registered handlers, then `Capabilities.EnergyStorage.BLOCK`.
- AF registers `EnergyStorage.BLOCK` (`FEGenericStackInvStorage`) at LOWEST on every block with
  `GENERIC_INTERNAL_INV`, unless the block entity has the Induction Card. The Endpoint's generic return is null on
  its Federation face, so a Provider's card sends nothing into an Endpoint at its front.

## What AE2 Federation does

- `CapturedManagedGridNode.owner(...)`: the owner logic forwards services other than `IGridTickable` and
  `ICraftingProvider` to the physical node (only before it exists); Lanes still drop them. Before this the owner
  dropped AF's distributor too, so the card could never work.
- `ProviderUpgradeCards.inherit` at `FMLLoadCompleteEvent` copies every card AE2's Pattern Provider takes onto
  `PROVIDER_ITEM` (no tooltip group). `FederationPatternProviderBlockEntity.upgrades()` exposes the owner's inventory.
- Menu: LDLib2 `PlayerUIMenuType` sends only the menu id, so the client cannot know the slot count when the screen is
  built. Both sides create 8 `FederationUpgradeSlot`s; the server binds the first `size` to the inventory and the rest
  to a take-nothing handler; `providerChoices` carries `upgrades` and the client shows that many, 4 per column
  (3 on the compact screen). No addon → column hidden, screen unchanged.
- Empty slot: AE2 `states.png` (240,208) at 40%; hover lists `GuiText.CompatibleUpgrades` +
  `Upgrades.getTooltipLinesForMachine`.

## Verifying the screen with Applied Flux

The UI harness has no AF client. For a local look only (never commit), temporarily set `uiTestClient`'s
`sourceSet = appfluxTest` in `neoforge-1.21.1/build.gradle` and run under Xvfb:
`./gradlew :neoforge-1.21.1:runUiTestClient -PfederationUiSelection=ui.mapping -PfederationUiOutputDir=<dir>
-PfederationUiWindow=1600x960 -PfederationUiGuiScale=3 -PenableAppfluxCompatibility=true`; screenshot
`764_ui-showcase-provider-own.png`. Restore the build file afterwards.

## Tests

- JUnit `CapturedManagedGridNodeTest` (owner forwards, lane drops, late service ignored).
- Compat `appflux`: `providerInheritsUpgradeSlots` (slot count equals AE2's provider, card in, refuses Speed Card,
  keeps through save, distributor on the node; fails when owner forwarding is disabled).
- Compat `appflux-mekanism`: `inductionCardPowersAdjacentMachine` (no FE before the card, Crusher powered after).

## FE across Federation (owner chose option 1, 2026-10-06)

- A lane's authorized target is the Endpoint's subnet input (`AuthorizedNativeTarget.position` is the Endpoint,
  `side` the face opposite its Federation face), not a machine. The owner chose: FE goes to machines touching the
  Endpoints the Provider holds.
- `ProviderEnergyRelay`: `Capabilities.EnergyStorage.BLOCK` registered on the Endpoint, Router and Federation Cable;
  it answers only when asked from the side of a Provider whose Federation face points at it. AF's ticker sends into
  it like into any neighbour. Targets come from `FederationPatternProviderTargetCache.authorized(lane)` (the same
  resolution pushes use), deduplicated per Endpoint, every face but the Federation face, skipping Federation blocks
  and other relays; filled in turn with a rotating start (AF simulates, extracts, then receives for real).
- The Provider invalidates its six neighbours' capabilities on ready and on rotation.
- Our registration precedes AF's LOWEST `FEGenericStackInvStorage` on the Endpoint; ours is null on other faces, so the
  Endpoint still takes FE returns there.
- Tests (appflux-mekanism): `inductionCardPowersEndpointMachine` (the guide's build: Crusher on the Endpoint, no own
  power, no subnet energy cell; ordered before the card goes in, the job waits 300 ticks with nothing back, then
  finishes once the card is in; red before the relay, and red with the card in from the start), `providerEnergyFollowsTheLink` (cable relay one-sided, AF through cables and a Router, stops when the
  Endpoint leaves the domain).

## Guide example (2026-10-06)

`guide_induction_mekanism` pack (group `induction`, see below), `examples/induction-card.md` ("A Crusher on the
Provider's FE", position 46), scene `induction_crusher.snbt`: main network (energy cell, crafting terminal, CPU, drive with item and FE cells), Provider,
one Federation Cable, Endpoint facing the cable, Crusher on the Endpoint's top, subnet cable with a Storage Bus on the
Crusher's side. Its "Try it" (order before the card is in) is `inductionCardPowersEndpointMachine`.
`guideSceneCables` in the appflux-mekanism profile confirmed the channel counts. `GuidePagesContractTest.MOD_ITEMS`
allows `appflux:induction_card`.

### Variants by installed mod (owner's order, 2026-10-06)

AE2's own FE machines (Inscriber, Charger) cannot show the card: the Inscriber takes power and joins the grid on every
face but its front, so on the Endpoint it either gets no FE (front) or joins the subnet, which the Endpoint already
powers (`getGridConnectableSides` = complement of FRONT; the Charger likewise). Applied Flux has no FE machine of its
own. So the example needs another mod's FE-only machine, and `GuideExamplePacks` groups variants: in one group only
the first pack whose mods are all loaded is active, and every pack of a group must ship the same page ids
(`GuidePagesContractTest.packsOfOneGroupShowTheSamePages`).

Owner's order: Mekanism Crusher, then Create Crafts & Additions, then Ender IO, then Industrial Foregoing. Vetted from
the ATM10 jars (not yet in game):
- Create Crafts & Additions (done, 1.7.2 pinned, profile `appflux-createaddition`, test
  `inductionCardTurnsElectricMotor`): Electric Motor south of the Endpoint facing up, shaft, cogwheel meshing with a
  Millstone above a Chute on the Endpoint; passed first time and fails with the card in from the start. The guide
  scene puts the motor column behind the Endpoint row (a mirror of the test build) so it does not hide the Millstone.
  `InductionCardExample.run(helper, machine)` is the shared test flow for every variant. Notes from vetting 1.7.1:
  no FE machine with an inventory. Electric Motor takes FE on all faces
  (`max(480*|rpm|/256, 8)` FE/t, shaft only on `facing`); drive a Millstone (cobblestone -> gravel, no auto-output)
  with a Chute under it into the Endpoint; about five blocks.
- Ender IO (done, v8.2.12-beta pinned, profile `appflux-enderio`, test `inductionCardPowersSagMill`): the test fits
  the capacitor through `getInventory().setStackInSlot(getCapacitorSlotIndex(), ...)` and sets DOWN to PUSH with
  `setIOMode` (reflection); without the capacitor the mill shows energy 0/0 and the job never finishes (mutation
  checked). Chinese name 半自磨机. Vetting notes: SAG Mill needs `enderio:basic_capacitor` (without it 0 FE capacity); faces default NONE, set
  the Endpoint face to PUSH (still takes FE, refuses items); `stone` -> cobblestone, 2400 FE.
- Industrial Foregoing 3.6.39 Resourceful Furnace: vanilla smelting, FE on all faces, set the output inventory's face
  toward the Endpoint to Push (`FacingModes`), 40 FE/t, 100 ticks.
- Rejected: Oritech Pulverizer and Actually Additions Crusher (never push output), Powah (orb never pushes), XyCraft
  (crusher unimplemented), Immersive Engineering (multiblocks), Just Dire Things (no item processor).
