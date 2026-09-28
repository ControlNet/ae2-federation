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

- [ ] Endpoint drop hints while dragging. Each card gets a green, red or grey ring and a reason:
  - "can drop · will claim"
  - "occupied by Provider @pos"
  - "already connected to this pattern"
  - "not loaded"
  - "rule not enabled", which needs the server to also send the Endpoints the Provider may not use, with a reason.
- [ ] Storage detail row under the storage rule: operations, filter and re-export. Serialize these fields.
- [ ] Identity detail and resolution lines, in per-state colours: loading blue, copy conflict red, merge and split
  yellow.
  - Merge lists its "contains" networks, from the live claims.
  - Split shows "N parts claim the same identity".
- [ ] Search also matches devices and coordinates: "search networks, devices or coordinates…".
- [ ] Connections list shows linked networks only, as "Connections (N)" with direction chips. Networks without rules
  are shown separately or not at all.
- [ ] Rule states: a red "error" state and a "!" suffix on the pill chip. Needs a real runtime error code, never an
  invented one.
- [ ] Read-only pair: a banner "open it from that domain's Bridge to edit", and unconfigured directions hidden.
- [ ] Processing aside:
  - Labels "Unlink this mapping" and "Highlight both ends in world".
  - Release is shown only when the Endpoint is owned and has 0 patterns.
- [ ] Acknowledgement text "Server confirmed: Storage rule enabled", replacing revision/ack codes in the visible
  text.
- [ ] Header summary: "synced · N networks · M links · this domain".
- [ ] Footer: "global rule revision #N · topology revision #M". The server counters exist; they need to be sent.
- [ ] Card state line reads "Identity confirmed". Aside identity reads "pos · network 3f9a…c21", not the full UUID
  on two lines.
- [ ] Throughput label on the canvas: amount over the 5 s window. Do not claim 10 s.
- [ ] Live-flow toggle, client only.
- [ ] Bugs:
  - "Highlight in world (10 s)" overflows its button.
  - The processing status spills under the footer (the drop-occupied screenshot).
- [ ] zh copy aligned with the design strings.

### Phase 7: medium items that need new server data

- [ ] "Highlight both parts" for merge and split: blocks from every grid that claims the network.
- [ ] Per-lane flow window, flow dots on busy wires, and the wire line "channel busy · sent N this batch · returned
  M".
- [ ] Map slice: pick the Y range from the network's blocks instead of `WORLD_SURFACE`.
- [ ] Endpoint facts:
  - "subnet ready" (`nodeReady`) and "owner … claim epoch N".
  - "return buffer empty, can be released", which needs a pending-return field.
- [ ] "Via" line in the pair editor: Router group or Bridge, with the Bridge position from the domain registry.
- [ ] Wire facts: its rule and state (client-derivable). A pending wire is drawn white and dashed until the server
  confirms.

### Phase 8: large items

- [ ] Several Providers from several networks on one processing canvas, each with its own allowed Endpoints and a
  Provider header card with "3/9 slots".
- [ ] 3D isometric preview with a Map/3D switch, built with LDLib2 `Scene` over loaded chunks.

### Phase 9: visuals

- [ ] Curved links and wires. Wires take the Provider network's accent colour.
- [ ] Related-domain pill: dashed border and a lock marker.
- [ ] Short names on pills ("Main▸Mine") instead of hex tags.
- [ ] Cards: energy and storage bars, a position line and an identity badge. Stat bars where a real denominator
  exists.
- [ ] Map thumbnails on the cards.

## Deliberately not implemented

- Type capacity and channel capacity: AE2 gives no per-network total, so a denominator would be invented.
- "Extract" as a flow direction, unless the recorder is extended to tag the operation.
- The Settings tab: the design says it was not redesigned.
- The prototype's instant local edits and fake acknowledgements. The game stays server-authoritative.
- A single merged "merging network" card, which would guess at identity.
- Opacity on disabled switches: AE2 sprites render as black boxes.
- Rendering unloaded chunks.
