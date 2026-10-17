# Persistent Federation domain ids (2026-10-17)

Phase 2b of `.omo/plans/beam-converter.md`: a physical domain keeps its `physical:N` id across restarts, so statistics
can be keyed by domain id (D10). Direct Bridge ids (`direct:...`) were already stable and are not recorded.

## Where it lives

- `domain/FederationDomainRecords` (pure, unit-tested): the last sequence handed out and, per domain sequence, its
  anchors split into `devices` (every node whose kind is not `CABLE`) and `cables` (only for a domain with no device:
  its `CABLE_ANCHORS` = 8 smallest cable node ids). An index node -> sequence makes each node anchor one domain.
  `onChange` fires only on a real change.
- `persistence/FederationDomainSavedData`, overworld data `ae2federation_domain_ids`: sequence, a dimension-name table,
  and per domain `{sequence, devices, cables}` with nodes as `{d: dimension index, p: packed pos, s: part}`.
  `records.onChange(this::setDirty)`.
- `FederationDomainRegistryAccess.getLocked` builds the registry over the saved records; the registry starts
  `physicalSequence` from `records.sequence()` and advances it with every new id.

## Matching

- In `recompute`, `inherit` also offers every record whose id is not live and that anchors a node of a component (a
  "dormant" candidate: `membersEqual=false`, `memberOverlap=0`, `nodeOverlap` = anchored nodes in the component).
- New first key in `INHERITANCE_ORDER`: `restored`. True for a dormant record, and for a live domain whose id came
  from a record (or was inherited from such a domain); the registry keeps that set. So after a restart the part that
  loads first claims the saved id, a part loading separately later gets a fresh id (a live id is never offered again),
  and when they join the restored id wins even if the later part has more members. Between two restored ids, or two
  fresh ones, the old order (members, nodes, older sequence) is unchanged; an in-session world without restarts or
  unloads never has a restored id.
- After installing a component, `anchor` writes its record: its loaded devices (or smallest cables) plus the anchors of
  its record, and of the old domains it absorbed, whose nodes are not loaded now. Loaded anchors move to the domain
  that holds them; an absorbed domain's record is removed.

## When anchors go

- Only a broken block drops its anchor: `FederationDomainRegistry.removeBrokenNode` (`records.drop` + `removeNode`).
  Chunk unload, dimension close (`removeDimension`), server stop and budget exhaustion keep every record.
- Block entities tell the two apart like the Pattern Provider always did: `onChunkUnloaded` sets `unloading`, then the
  `setRemoved` that follows is not a break; `clearRemoved` resets it (Cable, Router/Switch, Endpoint, Provider).
- Federation P2P part: `removeFromWorld` is a break when the host block entity `isRemoved()` (bus block broken) or the
  host is still `isInWorld()` (the part alone removed); a chunk unload takes the whole bus out of the world first.
- A record left without anchors is deleted. A block broken before its first tick (no node id yet) keeps a stale anchor;
  harmless, it only matches if a node with that id loads again.

## Tests

- Unit: `FederationDomainRecordsTest` (restart, both partial-load orders, halves sharing one saved id, unload vs
  break, dimension close, cable-only anchors, no change event for unchanged anchors), `FederationDomainSavedDataTest`.
  `FederationDomainRegistryTest.physicalIdsAreNeverReused` and the random-topology test now break nodes instead of
  unloading them, since an unloaded domain loading again gets its id back.
- GameTest `domain-id.restart` (backend `gametest-restart`, family `domainId`, properties
  `-PfederationDomainIdPhase` / `-PfederationDomainIdStateFile`, 7 assertions). Not a `domain.` id: the contract test
  pins exactly five `domain.*` cases. Prepare builds a decoy cable (takes `physical:1`), then Router-cable-cable-Router
  (`physical:2`), breaks the decoy and tickets the chunks; verify checks the same id after the restart, the Routers as
  anchors, the decoy record gone and a new id after every saved one. Run by hand (paths relative to
  `neoforge-1.21.1/run-gametest`):
  `./gradlew :neoforge-1.21.1:runGameTestServer -PfederationGameTestSelection=positive -PfederationGameTestId=domainidrestart -PfederationDomainIdPhase=prepare -PfederationDomainIdStateFile=../../build/domainid/state.properties -PfederationRetainGameTestRuntime=true --no-configuration-cache`,
  then the same with `=verify` (add `-PfederationNativeEvidenceFile=...` for the evidence file). `dev_gametests.py` and
  `required_gametests.py` run only backend `gametest`.
