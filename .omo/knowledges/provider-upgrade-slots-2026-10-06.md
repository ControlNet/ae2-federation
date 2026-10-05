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

## Open

FE to machines behind Endpoints is not done: a lane's authorized target is the Endpoint's input storage (its
subnet), not a machine, so "forward FE to the Endpoint's machines" needs an owner decision on the destination.
