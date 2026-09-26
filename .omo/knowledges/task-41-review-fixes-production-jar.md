# Task 41: review fixes and production JAR verification (2026-09-26)

## Identity: AE2 readies a part before connecting it
- `CableBusContainer.addPart` calls `part.addToWorld()` (node `markReady` → `updateState` → `getInternalGrid()` creates a
  one-node Grid) before `GridHelper.createConnection`. `NetworkIdentityGridService.addNode` minted a new NetworkId for
  that Grid, and the merge into the cable Grid saw two ids: `AMBIGUOUS_MERGE` for any Export Bus placed on a settled cable.
- Fix: lineages minted for a lone node are provisional (in memory plus a `provisional` flag in node data, carried by
  `GridNode.setGrid`'s `saveNodeData` → `add`). Discarded when the node joins a non-empty Grid; adopt a durable id when
  the Grid grows; durable once the Grid has two or more nodes. Test: `identitypartonsettledcable`.
- Known remaining edge: two fresh nodes that meet each other first form a durable Grid; joining it to an established
  Grid is still `AMBIGUOUS_MERGE` (fail-closed). Place cables one at a time in fixtures.

## identityambiguoussplit CI failure (run 36160106901)
- The chests became ready before the middle node and each minted its own id, so the "split" fixture was really a merge.
  Fixed by readying the middle node first; the no-inherit assertion stayed.

## Provider retention and return rebinding
- An empty sendList/returnInv does not prove the machine is empty. `LaneBinding.dispatched` (set by
  `NativeLaneDispatchListener` after a successful native push) keeps the Claim after unmapping; explicit two-step
  release in the Domain GUI.
- Returns rebind every 20 ticks per Provider (own Lanes only) through `FederationPatternProviderTargetCache.find(lane)`,
  which revalidates Provider, Endpoint, Claim epoch, Lane and Policy.

## Storage alias
- Only `getClass() == DelegatingMEInventory.class` is transparent. `MEInventoryHandler` subclasses change native
  landing (filter/access/preferred + separate priority), so wrapper + mounted delegate is `NON_TRANSPARENT_ALIAS`.

## Production run tips
- Lane bindings store absolute Endpoint positions: place exported templates at the GameTest origin's absolute position.
- A relocated copy of the same identities in the same world is a copied-live identity; use a fresh world per attempt.
- `user_jvm_args.txt` ends without a newline; `printf '\n…\n' >>` or the flag lands in a comment.
- Never `pkill -f <pattern>` from a command line that itself contains the pattern (kills the calling shell); use PIDs.
- Client: pixi env with `portablemc` + `python-xlib`, Xvfb `+extension GLX`, `onboardAccessibility:false`, and
  `tp <player> <pos> facing <x y z>` to aim. The Router opens the Domain screen; the Provider opens AE2's screen.
- Details and checksums: `docs/testing/production-jar.md`.
