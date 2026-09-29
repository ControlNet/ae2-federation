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
- [x] (phase 10) zh copy aligned with the design strings (V2Topology, V2Identity, BuiltProcessing).
  - 身份已确定 (not 已确认) throughout.
  - The design's pair note and rename help are used as written.
  - The drag help follows "从左侧样板行右边的端口按住拖到右侧端点".
  - "释放保留的端点…": the release has a confirm step.
  - "全局规则修订 #N · 拓扑修订 #M".

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
- [x] (phase 8) 3D isometric preview with a Map/3D switch, built with LDLib2 `Scene` over loaded chunks.

### Phase 9: visuals

- [x] (phase 9) Curved processing wires (`WireCurve`: a cubic curve that leaves the port and enters the card
  horizontally) in the Provider network's accent. Flow dots, pending dashes and click hit-testing all follow the
  curve.
- [x] (phase 9) Related-domain pill: dashed border and a lock marker (`FederationTheme.dashedBorder/lockMark`).
- [x] (phase 9) Short names on pills (`PillName`): the first word of the player's name, else the identity tag.
  Colliding short names fall back to tags.
- [x] (phase 9) Cards: an identity badge (the tag, coloured by identity state), a position line and an energy bar.
  The bar is stored/max, green, yellow under 25%, red when empty.
- [x] (phase 9) Map thumbnails on cards (`FederationMapPreview(true)`: no 3D switch, hidden outside the player's
  dimension).

## Phase 6 notes (2026-09-28)

- The rule error state comes from `RuleHealth`: `operation_missing`, or a backend reason the player has to fix in the
  world (crafting cycle, missing CPU or Provider, missing energy source or interface, missing domain reference).
- Drop hints use `DropHint`. The "rule not enabled" reason was closed in phase 7: every target carries its rule
  state (on/off/none), and a drop without an enabled rule is a yellow NO_RULE hint ("will pause").
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

- The 3D preview (`FederationScenePreview`) is an LDLib2 `Scene` over the real `ClientLevel`. `TrackedDummyWorld`
  proxies block reads and filters them to the rendered core.
  - The core is the non-air blocks of a `SceneWindow`: radius 6 around the focus, from 4 below the lowest nearby
    network block to 2 above the highest, and at most ±8 from the focus.
  - It is read again every 200 frames. Devices are overlaid in their colour and network blocks faintly.
  - The Map/3D choice is static, so it holds across previews and screens.
- The UI tree is also built on the dedicated server, but a `Scene` subclass cannot be loaded there. Instantiating it
  makes the verifier load `ClientLevel`, which crashes the server (T34 caught this). The Scene is therefore created
  lazily on the first client frame (`drawBackgroundAdditional`).

## Phase 9 notes (2026-09-28)

- There is no storage bar on cards. AE2 gives no per-network byte or type capacity, so it would need an invented
  denominator (same as the type-capacity item below). The card has a "Storage  N types" row without a bar.
- "1 link" uses its own key (`ae2federation.ui.domain.links.one`).
- (Superseded in Phase 11: links are curved now, see below.)

## Phase 11: second design pass (2026-09-29)

A side-by-side review after the window grew found the layout, not the features, off the design. Fixed:

- Proportions: the topology aside is 30% of the page (176..320), the processing aside 34% (118..340). The design's
  left rail really is icon-only (36x40 at design scale); the tabs are just larger (22x24).
- Header: "ME Federation Domain Management · <tab>" (tab names: Topology / Processing / Diagnostics), then
  "● Synced · entrance · summary". The summary is the members line on topology and "N Providers · M Endpoints ·
  K mappings" on processing. Scope and live-flow toggles and the Wires/List switch sit in the header.
- Footer: processing feedback (`#processing_status`) moved to the footer, coloured, hidden when neutral or off-page
  (LSS classes `feedback-neutral` / `off-page`, not `setDisplay`, so the LSS display rules keep working).
- Cards are 200x88: map tile top left, name + identity badge, position, state; labelled Energy bar with %, Storage
  N types, CPU x/y and Channels N. The bottom line takes the state colour. Unnamed networks read "Unnamed network"
  on the card and "Network 082A" (4-hex tag) everywhere else; tests use the 4-hex tag.
- Links are curves (`TopologyLink`): side edges when cards stand side by side, top/bottom when stacked, with an
  accent square at each end. The pill sits on the curve's middle; flow dots follow the curve and hide under the pill
  (`TopologyLink.dots`). `FlowPath` was removed.
- Pills: one row per direction, "A▸B" then a bordered chip per capability in its state colour, struck through when
  off, "!" on error. Related-domain pills add "Other domain · read only".
- Legend is shown by default, with coloured squares; "?" folds it.
- Pair editor rows are one line: capability, state (wraps for explanations), mapping link, switch.
  "Not configured" replaces "Not configured · switch on to create". The via line is "Via 5 Routers · first at
  x, y, z · this domain"; every Router position is in the title's tooltip.
- Network aside order follows the design: title, position · id, preview (caption names Map/3D), Highlight + Devices
  side by side, detail, stats with energy and CPU bars, connections with "›".
- Processing canvas: column headings, Provider cards (map tile, accent, "Provider · <network>", "pos · used/total
  slots · editing|open"), 22px rows with "→ N", round ports on the right edge, Endpoint cards with a round input
  port on the left edge, map tile, "Endpoint · <network>", position, claim and a claim-coloured bottom line. The
  drag draws a dashed curve with a cursor ring and outlines the dragged row. A legend line sits at the bottom.
  - Endpoint choices now carry `networkIndex` and `ownerPosition`, so hints say "Taken by Provider @ x, y, z".
  - The Provider selector row shows only in the list view.
- Processing aside: title, preview, then a facts table (`.processing-fact`, values `#processing_fact_value_<key>`):
  wire = Rule, Ownership, Lane, State; Endpoint = Network, Owner, Claim, Patterns, State. Buttons share one row.
  One map shows both ends of a wire instead of the design's two thumbnails: it keeps the Map/3D switch and the
  relation between the ends.
- Selector tooltips no longer show internal choice ids (64-hex Provider hashes).

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

## Phase 12: processing tab rebuilt against the V2Processing board (2026-09-29)

- Cause of the miss: phase 11 compared the game against the design's source and the code-rendered `BuiltProcessing`
  board, never against a rendered image of `V2Processing.dc.html`. Render the design itself before comparing:
  copy the canvas `project/*.dc.html`, save `artifact-type/dc-runtime.js` as `support.js` next to them, serve the
  folder (`python3 -m http.server`) and run
  `google-chrome --headless=new --hide-scrollbars --window-size=1330,800 --virtual-time-budget=8000 --screenshot=... URL`.
- Matched: columns 39% / 39% with the gap for wires; Provider cards with a 16:10 device thumbnail, a divider under the
  header and 18px rows; round ports centred on the cards' edges; Endpoint cards with "Patterns mapped: N"; wires 2px
  (3px selected); boxed two-line legend with the one-owner rule in yellow.
- The aside is on the light frame, not a dark panel: title, the wire's two ends side by side ("Provider @" /
  "Endpoint @"), facts in a dark inset, grey Unlink + blue Highlight, and a light note pinned at the bottom. The
  Map/3D switch left the processing aside (the design has none there); its test moved to the topology location map.
- Thumbnails (`FederationMapPreview(true)`) darken the ground, draw the network's blocks in its accent and the device
  in white, around a fixed radius of 5, widened to the box's shape. Blocks come from the topology overview through
  `FederationTopologyView.networkBlocks` and refresh every 40 ticks.
- Never call `Minecraft.getInstance()` while the UI tree is built: the dedicated server builds it too and crashes
  (T34 "clients timed out"). Measure fonts on the first `screenTick` instead.
- The editing Provider is shown by its lit ring and the header's `selected` class, not a " · editing" suffix.
- Flow dots flicker frame to frame (dots in the label box are skipped), so evidence records the count seen inside the
  same `waitUntil` that observed it.

## Phase 13: Provider right-click opens its wires (2026-09-29)

- `FederationPatternProviderBlock.useWithoutItem`: a plain empty-hand use opens `FederationDomainPolicyMenu.openDevice`,
  whose `forDevice` session already selects this Provider (`mappingProviderIndex`) and starts on the mapping page. A
  sneak-use, or a use while the workspace cannot open (a session already pending), opens AE2's own Pattern Provider
  screen, which players still need to insert patterns. **Back to provider** and AE2's "Federation mapping" button remain.
- The wires/list choice is a static preference; a Provider entrance resets it to wires in
  `FederationWorkspace.acceptChoices`, while Router and Bridge entrances keep the last choice.
- Test: `TaskThirtyThreeWorldFixture.rightClickProvider` drives `player.gameMode.useItemOn` with empty hands and
  `setShiftKeyDown`, so the real interaction path (including the sneak branch) is exercised, not `openDevice` directly.

## Phase 14: topology page against the rendered V2Topology / V2PairEditor / V2Scope boards (2026-09-29)

- Header: bold "ME Federation Domain" + " · <tab>"; the status line reads "Synced · N networks · N links · this
  domain · <entrance>" (entrance last, so it is the part that clips). Scope is a segmented pair of icon buttons
  (`#graph_scope_domain` dashed square, `#graph_scope` globe) plus the flow arrow icon; glyphs are 16x16 rows in
  `FederationIcons`, put on buttons with `FederationIcons.apply`. The scope text is `#scope_caption`.
- Header summary labels are shown per page by the `off-page` class, not `setDisplay`, so the compact LSS can still
  hide them (`setDisplay` overrides LSS).
- Cards: bold name ("Network 0A1F" while unnamed; no separate tag badge), state line "Online · Identity confirmed",
  "Online · Low energy" under 25 %, or the identity doubt alone. Tests find a card by its tooltip's full UUID
  (`TaskThirtyThreeScenarioSupport.networkCard`), which survives a rename.
- Layout: `TopologySpacing.factor` spreads the ellipse until no link label (sized by `pillHalfSize`) comes within
  8 px of a card; fixes labels hidden under cards in the related scope and merge state.
- Network aside: name, "Overworld · x, y, z · network 3f9a…c21", explanation panel (`#network_explain`) only for
  doubt / related / waiting, location panel with caption + segmented Map/3D (`#network_view_map` / `#network_view_3d`,
  replacing the in-map toggle), 16:10 map (`aspect-rate: 1.6` in LSS; compact 2.6) with dimmed surroundings, opaque
  accent blocks and a white controller outline, legend row, blue "Highlight 10 s" + grey "Devices (N)" (provider and
  endpoint counts in its tooltip), then five figure rows `label | bar | value` (`#network_stat_<name>` is the value).
  AE2 reports no type or channel capacity, so those bars stay empty tracks like the design's I/O and CPU rows.
- Pair editor: bold titles, row rules (`ROW_RULE`), painted slider switches (`FederationTheme.slider`, locked
  variants for read-only, replacing AE2 checkbox sprites), outlined "Map ›" link, storage terms as green operation
  chips (`#policy_terms_row_*`) with "Filter · Re-export" in `#policy_terms_*`, "Not configured · switch on to create".
  Revisions stay in the state text: stale-edit tests and players rely on them.
- The mapping case's Provider right-click now selects an empty hotbar slot first; an earlier case can leave an item
  in the selected slot, which made the step fail intermittently.
- Kept deviations: zoom/fit/legend buttons in the canvas, the 5 s flow window label top-left, Minecraft font, revision
  numbers, very narrow (320 px) header crowding when "Back to provider" is shown.

## Phase 15: a many-network, many-device showcase (2026-09-29)

- `TaskThirtyThreeShowcaseFixture` (end of `ui.mapping`) builds a fuller domain from production blocks: ME Chest +
  4k cell + creative cell networks on the Router's free east and down faces, names (Main Base, Automation Tower,
  Mine, Storage Hall) via `NetworkNames`, seven rules of all four capabilities (one off), two more Providers
  (`H.east()` on main; one on the Mine's energy cell) and four more Endpoints (grid connections, no adjacency), mapped
  many-to-many (7 mappings, one Endpoint free). Its cleanup runs first in `TaskThirtyThreeWorldFixture.cleanup` and
  deletes rules and clears names (both persist). `ServerContext.put(key, null)` throws: do not use it to clear state.
  Screenshots: `ui-showcase-topology`, `-pair`, `-processing`, `-processing-other`. The data is test-world data.
- Adding members makes the open workspace "Out of date" (policy changed elsewhere); reopen the Router before looking.
- Found and fixed with it:
  - "Via 5 Routers": a Router group's domain nodes include Federation cables and device ports; `viaJson` now counts
    loaded Router block entities only (unloaded nodes still count).
  - Networks reached only through a Router face had no position or thumbnail: the grid pivot can be the face port,
    which has no block; `NetworkOverview.location` falls back to any node with a block.
  - Every new Endpoint is `EndpointMode.LOCAL` until a claim switches it to Federated, so a fresh one showed "Local
    mode" and refused drops the server accepts. "local" now needs a live `EndpointModeGeneration.Local` (an adjacent
    native Provider actually bound); otherwise "Available · not claimed".
  - Endpoints owned by another Provider in the view read "Patterns mapped: N" with a green inset, not a red fault;
    only a drop onto them is refused. A foreign owner with no wires here (claim conflict) keeps "Owned by another".
  - Crossing links (a diamond's diagonals) stacked their labels in the middle: `TopologySpacing.labelSpots` moves a
    label along its link (0.5, 0.35, 0.65, …) until it clears cards and placed labels; `TopologyLink.labelAt`/`label()`
    carry it, and flow dots hide under the moved label.
  - Card state lines and the aside's connection rows clip at a word (`TextWrap.HIDE`) instead of running out; the
    connection rows use darker state colours (`onPaper`) on the light aside. Pill names allow 10 characters, then "…".

## Phase 16: the V1 list view is gone (2026-09-29)

The processing page is now only the V2 wires view; the Wires/List switch, `mapping_list`, `pattern_list`
(VirtualScroller), `mapping_provider_next`, `mapping_slot_next`, `mapping_lane_next`, `mapping_selection_value`,
`mapping_release`, `mapping_status` and `mapping_feedback_scroll` were removed. What only the list could do moved:

- Pattern search → the header `#graph_search`. `FederationTopologyView.onSearch(listener)` feeds the same query to
  `FederationProcessingGraph.filter`: a Provider whose header (name, network, position) matches shows all its rows,
  otherwise rows match on `patternText`; Endpoint cards match on title and position; `#processing_search_empty` shows
  when nothing matches. Hidden cards/rows drop their wires (`ends()` returns null when not displayed).
- Click-to-map → click a pattern row (selection kind `PATTERN`), click an Endpoint (keeps the chosen slot), then the
  aside's `#mapping_toggle` ("Map #N here" / "Unmap #N"), which sends `SET_MAPPING` with an explicit state. The server
  `TOGGLE_MAPPING` action still exists but no client control sends it.
- Release → `#processing_release`, shown only for a selected Endpoint this Provider retains with no patterns.
- Status/ack → `#processing_status` (the verifier's `mappingAck` comes from `attach()` reading it).

Gotchas found on the way:

- LDLib `Button.loadXml` turns `text=""` into `noText()`, which hides the label for good; a button whose label is set
  in code needs `.enableText()` (or a non-empty text attribute).
- The design keeps the aside's buttons directly under the facts, inside the detail scroller. In the 320x240 layout
  they stack (`.domain-shell.compact .processing-actions` column), facts put the name above the value, the paper note
  is hidden, and the aside is at least 104 wide so "Release retained" fits beside the scrollbar. UI tests scroll
  `#processing_detail` down before hovering those buttons, as a player would.
- In LSS `flex: 0` sets a zero basis and lets a fixed-height button collapse (to 6px here); use `flex-grow: 0` plus
  `flex-shrink: 0`.
- `federationUiTest` accepts only the exact case sets (T3/T15/T33/T34), not a subset.
