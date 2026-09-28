# GUI v2: design vs implementation gap list (2026-09-28)

- Source design: canvas https://claude.ai/artifact/RjetUoACLghDXje7FNzjfd, the `V2Topology.dc.html` prototype and
  `V2Identity.dc.html`.
- Status markers: [ ] open, [x] done (phase noted).

## Server facts

- `identity/IdentityStatus` already reports merge, split and copy conflict. `NetworkIdentityState` maps them to
  MERGE, SPLIT, COPIED, LOADING and UNLOADED.
- `NetworkOverview` reads its figures and blocks from the primary grid only. It sends `parts` without their blocks.
- `PolicyRule` has operations, filter and allowReexport, but the rules JSON the session sends omits them
  (`FederationDomainPolicySession` around lines 316–321).
- Flow is recorded per policy rule only (`ProviderObservationRegistry` 83–93). The send and return hooks exist, but
  nothing is recorded per lane.
- LDLib2 2.2.34 has `Scene` / `WorldSceneRenderer`, so a 3D preview can be done entirely on the client.

## Planned phases

### Phase 6: small items that current data mostly covers

- [x] (phase 6) Endpoint drop hints while dragging. Each card gets a green, red or grey ring and a reason:
  - "can drop · will claim"
  - "occupied by Provider @pos"
  - "already connected to this pattern"
  - "not loaded"
  - "rule not enabled", which needs the server to also send the Endpoints the Provider may not use, with a reason.
- [x] (phase 6) Storage detail row under the storage rule: operations, filter and re-export. Serialize these fields.
- [x] (phase 6) Identity detail and resolution lines, in per-state colours: loading blue, copy conflict red, merge and split
  yellow.
  - Merge lists its "contains" networks, from the live claims.
  - Split shows "N parts claim the same identity".
- [x] (phase 6) Search also matches devices and coordinates: "search networks, devices or coordinates…".
- [x] (phase 6) Connections list shows linked networks only, as "Connections (N)" with direction chips. Networks without rules
  are shown separately or not at all.
- [x] (phase 6) Rule states: a red "error" state and a "!" suffix on the pill chip. Needs a real runtime error code, never an
  invented one.
- [x] (phase 6) Read-only pair: a banner "open it from that domain's Bridge to edit", and unconfigured directions hidden.
- [x] (phase 6) Processing aside:
  - Labels "Unlink this mapping" and "Highlight both ends in world".
  - Release is shown only when the Endpoint is owned and has 0 patterns.
- [x] (phase 6) Acknowledgement text "Server confirmed: Storage rule enabled", replacing revision/ack codes in the visible
  text.
- [x] (phase 6) Header summary: "synced · N networks · M links · this domain".
- [x] (phase 6) Footer: "global rule revision #N · topology revision #M". The server counters exist; they need to be sent.
- [x] (phase 6) Card state line reads "Identity confirmed". Aside identity reads "pos · network 3f9a…c21", not the full UUID
  on two lines.
- [x] (phase 6) Throughput label on the canvas: amount over the 5 s window. Do not claim 10 s.
- [x] (phase 6) Live-flow toggle, client only.
- [x] (phase 6) Bugs:
  - "Highlight in world (10 s)" overflows its button.
  - The processing status spills under the footer (the drop-occupied screenshot).
- [ ] zh copy aligned with the design strings.

### Phase 7: medium items that need new server data

- [x] (phase 6) "Highlight both parts" for merge and split: blocks from every grid that claims the network.
- [x] (phase 7) Per-lane flow window, flow dots on busy wires, and the wire line "channel busy · sent N this batch · returned
  M".
- [x] (phase 7) Map slice: pick the Y range from the network's blocks instead of `WORLD_SURFACE`.
- [x] (phase 7) Endpoint facts:
  - "subnet ready" (`nodeReady`) and "owner … claim epoch N".
  - "return buffer empty, can be released", which needs a pending-return field.
- [x] (phase 7) "Via" line in the pair editor: Router group or Bridge, with the Bridge position from the domain registry.
- [x] (phase 7) Wire facts: its rule and state (client-derivable). A pending wire is drawn white and dashed until the server
  confirms.

### Phase 8: large items

- [x] (phase 8) Several Providers on one processing canvas, each under a Provider header card with "N/9 slots".
  Only the selected Provider is edited; the others show their patterns and wires muted, and a click selects one.
- [ ] 3D isometric preview with a Map/3D switch, built with LDLib2 `Scene` over loaded chunks.

### Phase 9: visuals

- [ ] Curved links and wires. Wires take the Provider network's accent colour.
- [ ] Related-domain pill: dashed border and a lock marker.
- [ ] Short names on pills ("Main▸Mine") instead of hex tags.
- [ ] Cards: energy and storage bars, a position line and an identity badge. Stat bars where a real denominator
  exists.
- [ ] Map thumbnails on the cards.

## Phase 6 notes (2026-09-28)

- The rule error state comes from `RuleHealth`: `operation_missing`, or a backend reason the player has to fix in the
  world (crafting cycle, missing CPU or Provider, missing energy source or interface, missing domain reference).
- Drop hints use `DropHint`. The "rule not enabled" reason is still open, because the server does not send the
  Endpoints a Provider may not use.
- The card state line keeps the online status. "Identity confirmed" is the settled wording in the aside and the
  stats.
- The footer uses "Rule rev #N · topology rev #M" (shortened to fit). The values are the policy store's high
  watermark and the domain registry's topology revision.
- zh copy was updated alongside every new string.

## Phase 7 notes (2026-09-28)

- Lane flow is recorded per Provider lane (`LevelObservabilityService.LaneKey`), sent and returned separately, over
  the same 5 s window.
  - The send hook knows the lane, not the pattern slot. The wire detail therefore says "Lane · last 5 s", and every
    pattern mapped to one Endpoint shares that figure.
- The map tile looks through the network's own heights (`BlockMarks.slice`): from the highest block down to 6
  blocks below the lowest, so underground bases are drawn.
- Endpoint detail shows "Subnet ready" (subnet node active and booted) and "Claim epoch N".
- "Via" comes from the domain snapshot's device nodes: a Router group (physical) or a Bridge (direct), with up to
  3 positions.
- A wire shows the processing rule it dispatches under. Mapping is allowed without a rule, but dispatch then
  pauses with POLICY_DENIED, so the drop hint is a yellow "no processing rule, will pause".
- A dropped wire is drawn white and dashed until the server confirms it (5 s at most).

## Phase 8 notes (2026-09-28)

- The server sends `processingProviders`: every Provider of the domain (at most 6, plus the selected one), with
  slot use and, for the non-selected ones, their pattern slots and wired Endpoints. It uses the same slot encoding
  (`patternSlot`) as the `slot` choices.
- The Endpoint column stays relative to the selected Provider. Claim, rule and lane facts depend on which Provider
  asks, so another Provider's pattern is edited by selecting that Provider first. Dragging across Providers would
  show hints for the wrong owner.
- The session pins the selected Provider by `ProviderIdentity`, not only by index. A Provider added or removed
  elsewhere in the domain no longer shifts which one is edited.
- A Federation Pattern Provider is a domain node. Placing or removing one renews the domain generation, and an
  open workspace becomes stale by design. The multi-Provider test therefore reopens the screen.
- Test fixture: place the second Provider against the first so AE2 joins it to the main network. Seeding it with
  the main network's identity and connecting afterwards made a duplicate identity, and the domain lost all its
  Providers.

## Deliberately not implemented

- Type capacity and channel capacity: AE2 gives no per-network total, so a denominator would be invented.
- "Extract" as a flow direction, unless the recorder is extended to tag the operation.
- The Settings tab: the design says it was not redesigned.
- The prototype's instant local edits and fake acknowledgements. The game stays server-authoritative.
- A single merged "merging network" card, which would guess at identity.
- Opacity on disabled switches: AE2 sprites render as black boxes.
- Rendering unloaded chunks.
- "Return buffer empty, can be released": Endpoint returns are handed back synchronously and there is no pending
  buffer to report. Release eligibility already follows the real retained state.
