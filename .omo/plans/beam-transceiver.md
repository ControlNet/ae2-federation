# ME Federation Beam Transceiver: implementation plan

Date: 2026-10-17. Status: draft for owner review; no code written yet.
Design source: [line-of-sight-beam-links.md](../../docs/ideas/line-of-sight-beam-links.md) (decisions of 2026-10-17)
and the transceiver row of the cost table in [federation-operating-power.md](../../docs/ideas/federation-operating-power.md).

Settled before this plan and not reopened here: a solid M x N x 1 body, 1x1 to 3x3; the back takes a bundle of the
same section (1x1: ordinary cable); two transceivers of one section, on one axis and facing each other, link with no
tool; the span is cable of that section (3x3 unlimited); the whole M x N volume must be clear; glass passes; entities
never block; nearest partner only; no relay device (back to back is the relay); same dimension; range is a server
setting, 256 by default, at most 1024; an amortized scan with air sections skipped; unloaded chunks in the middle
keep the last result; about one second of debounce each way; cost M·N × max(2, L)^1.05 × 2 FE/t.

## Decisions this plan still needs

| # | Question | Proposal |
|---|---|---|
| D1 | Name and ID | `ae2federation:beam_transceiver`, "ME Federation Beam Transceiver", 「ME联邦光束收发器」, beside `beam_converter`. |
| D2 | Back to back vs. body merging | `DeviceStructure` merges touching blocks of one type, so two bodies back to back become one M x N x 2 candidate and fail. Proposal: a block belongs to a body only with the same facing; back to back faces opposite ways, so it stays two bodies. Placement sets the front toward where the player looks. |
| D3 | Wrench | On a formed body: flip it front for back, as on the converter. On a single block: turn it to the clicked face, so a stray block can join its body. |
| D4 | What blocks the beam | A block whose collision shape is not empty, except glass (`c:glass_blocks`, `c:glass_panes`, AE2 quartz glass) and a datapack tag `ae2federation:beam_passes`. Fluids, grass, flowers, torches and snow layers without collision pass. |
| D5 | What the back accepts | What a Router face accepts: a bundle of the same section covering it, or ordinary cable on any cell. The capacity is then that of whatever touches it. |
| D6 | Converter size match | A linked pair's section counts with the cables', so a 3x3 pair among 1x1 cables needs a 3x3 converter. |
| D7 | Energy details | Charged once per linked pair, not per end. L is the number of blocks between the two fronts. Transceiver blocks themselves cost 0, linked or not. |
| D8 | Fronts touching (L = 0) | Allowed; `max(2, L)` already covers it. |
| D9 | Player feedback | One Jade line: "Beam: linked, L blocks", "Beam: blocked at x, y, z", "Beam: no partner within R", or "Beam: wrong size" (a facing partner of another section). No screen. |
| D10 | Recipe (one block per craft) | `GFG / CLC / GFG`: G `ae2:quartz_vibrant_glass`, F `ae2:fluix_pearl`, C Federation Cable, L Nexus Core. |
| D11 | Textures before the artist delivers | Temporary: the converter's textures recoloured; listed as temporary in the commit and the art hand-off note. |
| D12 | Scan budget and debounce lengths | Constants, fixed after the phase 4 benchmark; only the range is a setting. Start from 64 blocks per beam per tick and 20 ticks each way. |

## Architecture

- **Package `beam/`.** `BeamTransceiverBlock` (a `FormableDevice` with `FACING`, `FORMED`, `CONNECTED`, the ticker
  reusing `RouterBlockEntity.serverTick` as the converter does), `BeamTransceiverBlockEntity`, `BeamTransceiverShape`
  (M x N x 1 with the thin axis along `FACING`, section from `CableCrossSection.of`).
- **Forming (D2).** `DeviceStructureUpdater.evaluate` takes membership from the device: a new
  `FormableDevice.member(BlockState self, BlockState other)` defaults to "same block"; the transceiver adds "same
  facing". An opposite-facing neighbour then reads as `OTHER`, which satisfies both the solid check and the shell
  check of `DeviceStructure.evaluate`. `judge` accepts only `BeamTransceiverShape.of(min, max)`.
- **Ports.** As on the converter: a block exposes a Federation port on its back face and on faces toward the rest of
  the body; the front exposes none (the beam is not a port). Bundle contact needs no new rule, since
  `BundleContacts.contact` already counts a formed device body covering the end face.
- **Evidence.** Every body block is its own node of a new kind `TRANSCEIVER` with `capacityFactor` = its section's
  factor (0 for 3x3), through the four-argument `FederationDomainNodeEvidence`. The body's min-corner block (the
  anchor) adds the remote link as a mirrored port pair, as `FederationP2PTunnelPart.publish` does with `"tunnel:"`:
  A names `"beam:" + B` → `(B, "beam:" + A)` and B the reverse. Both ends republish in the same pass (a `TickTask`
  drain as in P2P), so the registry never sees a one-sided edge.
- **Link service, per level, server thread.** `BeamLinkService`:
  - an index of formed front rectangles keyed by axis and perpendicular bounds; the nearest opposite-facing body on
    the same line within range is the candidate; any body in between, of any section, is an obstruction;
  - per candidate pair, a cursor over the M x N x L volume, a fixed number of blocks per tick; chunk sections with
    `hasOnlyAir()` are skipped whole; a position in an unloaded chunk is never read (checked with
    `getChunkSource().getChunkNow`) and keeps its last result, checked again when the chunk loads;
  - debounce: a pair links after the whole volume has stayed clear for the clear delay, and unlinks after a blocking
    position has stayed blocked for the blocked delay;
  - public NeoForge `BlockEvent`s (place, break, neighbour notify) inside a known volume move that position to the
    front of the scan; no mixin.
  Runs in `onServerTickBindings` before `OperatingPowerService.tick`, so a link change lands in the same tick's plan.
  An end whose chunk unloads withdraws its nodes as every block entity does, so the link breaks when either end
  unloads; only the middle may be unloaded. This follows the settled rule and is not a bug.
- **Capacity.** `CableCapacityCounter.passable` and the demand assignment include `TRANSCEIVER`, so a pair and its
  span are one region and are judged against their section's limit. `CableCapacity.demand(level, nodeId)` already
  serves a Jade line.
- **Power.** `OperatingPowerCosts.perTick(TRANSCEIVER) = 0`; `OperatingPowerService.plan` adds each linked pair once
  (ordered by node id): M·N × max(2, L)^1.05 × 2 × multiplier, with L from the two anchors' positions, which node ids
  carry, so no new evidence field. `largest` takes linked transceivers' factors as well as cables' (D6). The
  statistics stack gets a "Beam links" group.
- **Client.** A `BlockEntityRenderer` on the anchor of the lower node id draws the beam from front to front with
  `CableFlowRenderer`'s CORE and GLOW render types and `CableBeamStyle` (width by section, load colour, red when over
  capacity or unpowered). The update tag carries the partner anchor, load and unpowered state. The render bounding box
  spans the beam; the view distance stays 256, so a longer beam is drawn only within 256 blocks of the viewer.
- **Config.** `FederationServerConfig` `push("beam")`: `maxRange` 256 (1 to 1024).

## Phases

Each phase is a commit on `dev`, pushed once its checks pass. TDD: tests are written first in each phase.

### 1. Pure logic

- `BeamTransceiverShape`, `BeamVolume` (the positions between two fronts in scan order, section by section),
  `BeamObstruction` (the D4 predicate over a block state and its collision shape), `BeamDebounce`, `BeamPartnerIndex`.
- Tests: shapes 1x1 to 3x3 accepted and others refused; volume size M·N·L and order; nearest partner, wrong size,
  out of range, body in between; debounce both ways.

### 2. Block and body

- Block, block entity, item, forming with facing membership (D2), wrench (D3), ports, registration, creative tab,
  lang, recipe, loot table, pickaxe tag, temporary textures (D11), connected-face model.
- GameTests: forming 1x1 to 3x3; back to back stays two bodies; a 2x2 back takes a 2x2 bundle; wrench flips a body.

### 3. Link

- `BeamLinkService`, the mirrored beam ports, kind `TRANSCEIVER` in the counter, Jade line (D9).
- GameTests: a pair links and joins two networks' domain; a stone block breaks it after the delay and removing it
  restores it; glass and water pass; nearest only; a third transceiver in between; different sections do not link;
  back-to-back relay of three bodies; a pair over capacity stops the domain; middle chunk unloaded keeps the link.

### 4. Benchmark

- A manual GameTest in `PerformanceBenchmarkGameTests`: one 3x3 x 1024 beam through stone-free terrain and through a
  filled volume, measuring scan cost per tick and the time to notice a change; plus the registry recompute a link
  change causes (`recomputeNanos`). Fix D12's constants from it.

### 5. Power

- Link cost, section match, statistics group. Tests: `OperatingPowerCostsTest` for the formula; GameTest that a 3x3
  pair needs a 3x3 converter.

### 6. Client

- Beam renderer and its previews; in-game before/after images under `build/beam-transceiver-check/`.

### 7. Docs

- `docs/features/beam-transceiver.md`; the guide item page with a hand-written scene, en and zh; the idea page's
  status; the operating power and capacity pages; a knowledge note.
