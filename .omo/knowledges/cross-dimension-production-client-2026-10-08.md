# Cross-dimension production client run (2026-10-08)

How the review-82 production run was set up and driven, and what it taught. The scrubbed public record is in
`docs/testing/production-jar.md#cross-dimension-production-run`.

## Fixture

- The manual GameTest `crossdimensionproductionexport` (`CrossDimensionProductionExportGameTests`) writes
  `crossdim_overworld.nbt`, `crossdim_nether.nbt` and `crossdim.properties` next to `-PfederationNativeEvidenceFile`.
- Place the overworld template at `200 69 201` and the nether one at `202 70 200`; both Endpoints then sit at
  `203, 70, 205`. The GameTest structure fills empty space with barrier blocks: replace them with air after placing.
- Give each region a floor first (glass y 68 overworld, obsidian y 69 nether) and keep both regions force-loaded over
  `192 192 223 223`; release the nether ticket to unload the nether Endpoint mid-order.
- The templates keep the Endpoints' identities (the Lane stores them), so place them once per world; each attempt used
  a new world.
- An Endpoint's Domain workspace shows "no domain" unless a Router or Bridge joins its network to a Domain. That is by
  design; the fixture has one Router per dimension so the Domain graph shows both networks.

## Driving the client

- Run the server with `tail -n0 -f console.in | ./run.sh nogui` and append commands to `console.in`.
- The XTEST driver supports click, drag, move, text, key, button and wait. Drag-to-map on the Provider screen did not
  register; select the pattern, select the Endpoint card, then press **Map #0 here**.
- The Provider screen layout shifts when a pattern is selected, so button positions change; take a screenshot before
  each click. Endpoint card order differs between worlds.
- In an AE2 terminal a left click on a stocked item takes it out; a middle click opens the craft amount screen.
- Use explicit yaw and pitch with `tp` (`tp verifier x y z yaw pitch`); `tp ... facing` pitched the view badly.
- Find the client process by its command line (`ps -eo pid,comm,args | awk '$2=="java" && /prod-client/'`); `pkill -f`
  with a pattern from the current shell's own command line kills that shell.
- The client stays on the disconnect screen after the server stops; close it with SIGTERM before restarting.

## Findings fixed during the run

- Endpoint cards, details, wire end labels and search named coordinates only (`0456867`, `DevicePlace`).
- Return-buffer rows and Domain-graph Endpoint labels named coordinates only (`b75467d`); the return row then clipped
  the dimension name (`cc6df41`).
- While the Endpoint's chunk was unloaded, its Lane's return row showed an identity fragment; the Provider now reads
  the Lane's stored position and dimension (`a2a20de`).

## Known limits

- The Provider screen hides the card of a mapped Endpoint whose chunk is unloaded; the `→1` count and the return row
  remain.
- Chunk loading in the run is vanilla `forceload` by the operator; the mod loads no chunks.
