# ME Federation Quantum Cube: implementation plan

Date: 2026-10-17. Status: D1-D5 answered by the owner (2026-10-17); D6-D10 proposed, waiting for the owner.
Design source: [remote-federation-connections.md](../../docs/ideas/remote-federation-connections.md), section
"Follow-up 2026-10-17: a wireless cube for bundled cables".

Settled before this plan and not reopened here: a solid cube of one block type; edge 3, 4 or 5; a Federation cable or
bundle connects inside a face's (K-2) x (K-2) centre and the border ring connects nothing, so 3³ takes 1x1, 4³ up to
2x2 and 5³ up to 3x3 (only squares are buildable today, see the square gate); a cube carries the largest tier it can
connect: 3³ C, 4³ 6C, 5³ unlimited; the two ends count as cable joined directly, one capacity region, as Federation
P2P does; cross-dimensional; bound with an AE2 Memory Card; energy and unloading follow AE2's Quantum Network Bridge,
and chunks are never force-loaded.

## Decisions

| # | Question | Answer or proposal |
|---|---|---|
| D1 | Name and ID (owner, 2026-10-17) | `ae2federation:quantum_cube`, "ME Federation Quantum Cube", 「ME联邦量子立方」. AE2's "ME Quantum Ring" is only the outer part of its bridge, so "ring" is not reused. |
| D2 | Pairing (owner, 2026-10-17) | One to one, as AE2's bridge: one cube links to exactly one other cube. |
| D3 | Energy (owner, 2026-10-17) | AE2's bridge copied: 22 AE/t per block, no dimension or distance term. `perTick(QUANTUM_CUBE)` = 44 FE/t at multiplier 1 (1 AE = 2 FE). Per cube: 3³ 594 AE/t (1188 FE/t), 4³ 1408 AE/t (2816 FE/t), 5³ 2750 AE/t (5500 FE/t). Both ends pay, as both sides of a bridge do. |
| D4 | Recipe base (owner, 2026-10-17) | Built on AE2's `ae2:quantum_ring` and `ae2:quantum_link` plus a Nexus Core; the exact pattern is D6. |
| D5 | Textures before the artist delivers (owner, 2026-10-17) | Temporary: an existing Federation device texture recoloured; listed as temporary in the commit and the art hand-off note. |
| D6 | Recipe and yield | AE2 ring is `aba/cde/aba` (iron, logic processor, engineering processor, energy cell, smart dense cable); chamber is `aba/b b/aba` (quartz glass, fluix pearl). Proposal: `CRC / RQR / CLC` → **8** cubes; C Federation Cable, R `ae2:quantum_ring`, Q `ae2:quantum_link`, L Nexus Core. A 3³ pair (54 blocks) is 7 crafts, 21 rings, close to an AE2 bridge pair's 16; a 5³ pair (250 blocks) is 32 crafts, 96 rings. Yield 4 doubles that. |
| D7 | Ends of different sizes | Proposal: no link, Jade "another size", as the Beam Former refuses a partner of another section. |
| D8 | Cost while not linked | Proposal: every formed block pays whether linked or not, as AE2 charges a formed bridge's idle power. An unformed block costs nothing. |
| D9 | Card use and rebinding | Proposal, as on AE2 machines: sneak + use on a formed cube copies it to the card ("cube at dimension x, y, z"); use on another formed cube binds the two with a new pair id, and both drop whatever pair they had before, so a third cube cannot join a pair. Use on the same cube, or with a card holding something else, does nothing but say why. |
| D10 | Player feedback | No renderer (nothing lies between the ends). One Jade line: "Quantum link: linked to dimension x, y, z", "not bound", "partner not loaded", "another size" (D7), "not formed". |

## Architecture

- **Package `quantum/`.** `QuantumCubeBlock` (a `FormableDevice` with `FORMED`, `CONNECTED`, the ticker reusing
  `RouterBlockEntity.serverTick` as the converter does), `QuantumCubeBlockEntity`, `QuantumCubeShape`.
- **Cube rule.** `judge` accepts only bodies with three equal edges of 3, 4 or 5; 6³ to 8³ (within
  `DeviceStructure.MAX_EDGE`) and every non-cube cuboid are `WRONG_SHAPE`. The section follows the edge through
  `CableCrossSection` (3 → 1x1, 4 → 2x2, 5 → 3x3), so each node's `capacityFactor` is the section's (1, 6,
  `UNLIMITED`) and `CableCrossSection.ofCapacityFactor` keeps working in the counter and the converter match.
- **Face-centre ports.** A member has a port on each face toward another member. On an outer face F it has a port
  only when its two coordinates across F lie strictly between the body's min and max, i.e. inside the (K-2)²
  centre; edge and corner blocks expose nothing outward. The rule is "inside the centre", not "centred": a 2x2 bundle
  anywhere in a 5³'s 3x3 centre connects. `BundleContacts.contact` only checks that a formed body covers the bundle's
  end, so a bundle pressed partly on the border reads as covered but finds no port on its border cells and does not
  join; a GameTest pins that.
- **Pair id on the block side.** Every member's block entity stores the pair id (a random 64-bit value from D9) and
  the partner's last known anchor and dimension, the latter for Jade only. On forming, the body takes the id its
  members agree on; members that disagree (bodies merged by the player) leave the body unbound. A dropped block keeps
  nothing. No `SavedData` is needed: nothing outside the loaded bodies has to be known.
- **Link index, server-wide.** `QuantumLinkService` lives on the `MinecraftServer`, not a level, because pairs cross
  dimensions. It indexes formed, loaded bodies by pair id. A pair links when exactly two bodies of one id are loaded
  and their sizes match. The anchor (min corner) of each adds the mirrored port pair through
  `RouterBlockEntity.remotePorts()`, `"quantum:" + B` → `(B, "quantum:" + A)`, as the Beam Former does with
  `"beam:"`; `FederationDomainNodeId` already carries the dimension, so the evidence needs no new field. Both ends
  republish in one `TickTask` drain, as Federation P2P does, so the registry never sees a one-sided edge.
- **Unloading.** As AE2's bridge: a body whose chunk (or dimension) unloads withdraws its nodes as every block entity
  does and leaves the index; the other end stops publishing the link, so the domain splits. When it loads again it
  rejoins. No tickets, no chunk loading. A cube across a chunk border is formed only while all its chunks are loaded
  (`DeviceStructure` already answers `Unknown`).
- **Memory card.** `MemoryCardItem.doesSneakBypassUse` returns true and the block's `useItemOn` runs before the item's
  `useOn` (verified against AE2 19.2.17 bytecode), so the cube handles both the sneak copy and the paste and returns
  success; AE2's own "clear card" on sneak in the air keeps working. The card carries our own data component (the
  source cube's dimension and anchor) and `AEComponents.EXPORTED_SETTINGS_SOURCE` for AE2's tooltip. New code: the
  Federation P2P never touched the card, since AE2 sets tunnel frequencies itself.
- **Capacity.** New kind `QUANTUM_CUBE`. `CableCapacityCounter.passable` and the demand assignment include it, so the
  two cubes and whatever both sides reach are one region judged against the cube's tier.
- **Power.** `OperatingPowerCosts.perTick(QUANTUM_CUBE)` = 44.0 FE/t; a new statistics group `QUANTUM_CUBES`
  ("Quantum Cubes" / 「量子立方」) with a seventh colour in `FederationStatisticsView`. `OperatingPowerService.plan`'s
  `largest` takes linked cubes' factors, so a 5³ pair needs a 3x3 converter, as a 3x3 Beam Former pair does.
  Every exhaustive switch over `FederationDomainNodeKind` (`perTick`, `Group.of`, the counter) is a checklist item.

## Phases

Each phase is a commit on `dev`, pushed once its checks pass. TDD: tests are written first in each phase.

### 1. Pure logic

- `QuantumCubeShape` (edge to section, the face-centre predicate), the pair index (two of one id link, one or three
  do not, sizes must match), with unit tests.

### 2. Block, body and card

- Block, block entity, item, forming, face-centre ports, card copy and bind (D9), registration, creative tab, lang,
  recipe (D6), loot table, pickaxe tag, temporary textures (D5).
- GameTests: 3³, 4³, 5³ form; 2³, 6³ and a 3x3x4 do not; a 1x1 cable on a 3³ face centre joins, on its border does
  not; a 2x2 bundle inside a 4³ centre joins, one cell off does not; a 2x2 anywhere in a 5³ centre joins; card bind
  sets both bodies' id and a rebind drops the old partner.

### 3. Link

- `QuantumLinkService`, the mirrored `quantum:` ports, kind `QUANTUM_CUBE` in the counter, the Jade line (D10).
- GameTests: a bound pair joins two networks' domain; another size does not link (D7); breaking one block unlinks
  and replacing it relinks; a pair over its tier stops the domain; a 3³ pair carries C. Cross-dimension and unloading
  cannot run as GameTests (one level, no unload); they are checked in game.

### 4. Power

- `perTick`, the statistics group, the converter match. Tests: `OperatingPowerCostsTest` for the cost; GameTest that
  a 5³ pair needs a 3x3 converter.

### 5. Docs and in-game check

- `docs/features/quantum-cube.md`; guide item page en/zh with a hand-written scene; index entries;
  `GuidePagesContractTest.FEDERATION_ITEMS`; the operating power and capacity pages; the idea page's status; a
  knowledge note.
- In game, before/after under `build/quantum-cube-check/`: a pair across the overworld and the Nether joining two
  networks; Jade before and after unloading the far end.
