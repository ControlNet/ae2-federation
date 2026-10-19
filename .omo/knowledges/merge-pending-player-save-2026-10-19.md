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
