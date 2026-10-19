# A player's network missing from the Switch topology: Merge pending (2026-10-19)

## Symptom
- A controller's ME network sat right next to a formed Switch, but the Switch's topology did not show it at all.

## What the save held
- One AE2 Grid (controller, cables, a terminal, a creative energy cell, a Neo ECO storage multiblock joined through its
  storage interface) whose nodes saved three different `ae2federation_network_identity` network ids:
  - the Neo ECO multiblock, most cables and the energy cell carried one;
  - the controller and three cables carried a second;
  - the terminal part carried a third, although its host cable carried the first.
- The Switch faces' saved ids are only boundary hints (neutral nodes) and do not matter.
- Read with a standard-library NBT/Anvil reader run as `python3 -I`; the extracted save stays in its own directory.

## Code path
- `IdentityReconciler.reconcile`: more than one network id in a Grid gives `AMBIGUOUS_MERGE` with no network id.
- `FederationDomainRegistryAccess.confirmedNetworkId` is then empty, so the Switch face never publishes the network as a
  domain member.
- `NetworkOverview.describe` and the topology list only domain members. A network that was merged before it ever
  joined the domain therefore shows no "Merge pending" card; it is simply absent. The card appears only for a network
  that was already a member.
- `NetworkIdentityGridService.adoptEstablishedLineage`: a fresh node adopts an id only while exactly one durable id is
  present. So in a Grid that already holds several ids, a re-placed block does not fix anything; it just takes one of them.

## How it arises in ordinary play
- Any two pieces built in different ticks are each durable once their tick ends. Even a lone controller or a single
  chest counts (`identitySingleEstablishedMerge`).
- Joining two such pieces later with a cable is a fail-closed merge by contract: no newest-wins or size tie-break
  (`docs/architecture/network-identity.md`).
- A part placed alone (a cable bus holding only the part) also becomes durable after its tick. A cable added to that bus
  later is then a merge too. This likely explains the terminal's own id; the save cannot date it.

## Recovery advice (derived from code, not run on the save)
1. Break every block or part that carries a minority id.
2. Only then place them again, so that the remaining Grid has one id when the new nodes adopt.
- AE2 drops these items without node data, so a re-placed block mints fresh.
- The guide line "Disconnect them to recover" only makes each piece settle on its own id; reconnecting merges again.

## Decision (user, 2026-10-19): A + B + C
- **C, done.** `NetworkIdentityGridService.resolveMerge` runs at a Grid start tick once the Grid holds several ids.
  - It acts only when the settlement is `AMBIGUOUS_MERGE`, so copied and split states never take part.
  - An id carries settings when `persistence.NetworkSettings` finds a name, a configured rule naming it, or crafting
    returns.
  - If at most one id carries settings, that one survives; with none, the id held by the most regular nodes survives,
    then the lowest UUID.
  - Every node is rewritten (node UUID and revision kept), followed by the same publish as the fresh-tick refresh.
  - Several configured ids also merge when their settings are interchangeable (user, 2026-10-19). That means the
    same name, the same rules (side, other network, capability, rule content), no rule between the merging ids, and
    no crafting returns. The most-nodes id survives, and `NetworkSettings.fold` deletes the others' name and rules.
  - Differing ids stay pending and are rechecked every 20 ticks.
  - This save's three ids carry no settings, so the save heals on load.
  - Every folded id goes through `NetworkSettings.fold(level, folded, survivor)`. On the function branch it also folds
    the short id, so a program naming `network-xxxxxx` reaches the joined network: the folded id's short id
    becomes the survivor's when the survivor has none, else an alias (saved under `aliases`). That is why short ids
    are not counted as settings.
- **A, done.** `FederationDomainPolicySession.pendingMerges` adds the ids of an `AMBIGUOUS_MERGE` Grid that touches one
  of the domain's Switch faces (`RouterFacePort` owner node) to the shown networks and to the overview.
  - These ids are shown read-only: they are not in `members()`, so no rule editing applies to them.
  - Lookup is throttled to once a second.
- **B, done (user chose plan B: the unchosen ids are deleted).** A Merge pending card shows **Keep this one** in
  the action row, in place of Devices.
  - A confirmation dialog names the ids whose names and rules are deleted.
  - Confirming sends `KEEP_NETWORK` with the kept id. `FederationDomainPolicySession.keepNetwork` accepts only a
    pending or member id and calls `NetworkIdentityService.keep` on its Grid. `keep` refuses unless the Grid is
    `AMBIGUOUS_MERGE` with no provisional nodes; it then runs the same `settleOn` as C.
  - The domain's generation changes when the kept network joins it, so the open session goes stale.
    `takeReopen` (polled by the holder's status binding) reopens the same Router or device entrance once the entrance
    sits in a domain with a member again; it gives up after 5 seconds. A Bridge entrance is not reopened.
  - Opening the dialog needs mouse down and up on the button in UI tests (`activateNavigation`); `.click()` releases
    on the dialog.
- Tests: GameTests `identityunconfiguredmerge`, `identityconfiguredmergewins`, `identityconfiguredmergestayspending`,
  `identityidenticalsettingsmerge`, `identityplayerkeepsone`;
  UI scenarios `ui.merged-network` and `ui.pending-merge`.
- T33's merge step now names both networks first, to keep that merge pending.
