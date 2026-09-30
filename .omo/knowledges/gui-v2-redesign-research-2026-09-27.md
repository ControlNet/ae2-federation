# GUI v2 redesign research, 2026-09-27

The user considers the current GUI a functional test surface and wants a full redesign: NeoECO visual style, a network
topology graph as the Router/Bridge entry view, clickable network-network edges that edit policy, per-network stats,
and a top-down world preview so players recognize networks instead of opaque IDs. Rendering must be client-side;
server work is limited to small, cached, on-demand data (user concern: multiplayer load).

Reference clones (shallow, scratch only): NeoECOAEExtension 70cea49, DataEnergistics 1.21 c95a124,
KilaGraph 26.1 b84b64b, KilaGraphDemo 90ff912. Earlier notes: `neoeco-gui-insights-2026-09-26.md`,
`gui-ldlib2-reference-review-2026-09-26.md`.

## NeoECO style (to follow fully)

- Light AE2-like frame: outline `#413F54`, highlight `#F2F2F2`, face `#CBCCD4`, bottom shadow band `#878FA5`
  (`background.png` 16x16 border 2,2,2,4). Buttons 20x20 border (2,2,2,5): face `#9A9FB4`, highlight `#ADB0C4`,
  3 px lip `#696D88`; hover/active cyan `#9CD3FF`/`#DAFFFF`/lip `#708CBA`; disabled `#696D88`.
- Dark inner cards: LDLib `gdp_styles` BORDER_THICK_RT1 (body `#47434F/#605A66`, inner `#2F2A34`), task cards
  `#2C2735` with edge `#D8D3E4`, border `#121016`, 1 px state accent line.
- Text: default MC font, no shadow; on light `#3F3D52`/`#6D6A82`; on dark `#D6D0E0`, title `#EFEAF8`, muted `#AAA4B2`.
- Accents: value `#8377FF`, time `#55A7FF`, ok `#00FC00`/`#55FF8A`, warn `#FFD65A`, orange `#FF9A3D`,
  error `#FF6A75`, teal bar `#26A6BD`. Ratio colors <75/75/90/100 %. Lamps 13x13 on `#55FF8A` / off `#FF6A75`.
- Patterns: `NETextures` static fields → `ui-eco:` builtins, class-driven LSS state; side rail (AE2 toolbar buttons,
  23 wide at x=-21); floating side panels; 500 ms cubic-bezier ratio animation; `HostText` formatting.

## World preview (KilaGraphDemo finding)

The demo's "45° view" is a camera-override mixin + orthographic projection of the real world behind a transparent
screen (MC 26.1 only, loaded chunks only). Not portable. On 1.21.1 use LDLib2 2.2.34 `Scene` (`useOrtho`,
`setCameraYawAndPitch(45, 35.26)`, `setRenderedCore`, `onSelected`, `project()` for label overlays) with either the
live `ClientLevel` proxy (loaded chunks, same dimension) or a server snapshot in a
`TrackedDummyWorld` (AE2 cable bus renders empty from state alone; needs stand-ins). Map tile alternative: MapColor
grid 64x64 (~1-2 KB), cave-slice from network bbox for underground bases. Budget 1-3 cached Scenes, never per row.
`setCameraYawAndPitchAnima` swaps yaw/pitch in 2.2.34. Tiering: client-first, server fallback on demand (cached per
network+revision, loaded chunks only, rate-limited, server-config toggle), text fallback.

## Backend facts that shape the design

- Physical domain = BFS group of Router/Cable/Provider nodes; each Bridge is its own two-member direct domain and
  never joins a Router group. A network can be in several domains. Port evidence map exists but is private; node kind
  not stored. Endpoint is not a domain node.
- Policy is global (overworld saved data), keyed `(consumer, provider, capability)`, rule has operations, filter,
  allowReexport; GUI can only TOGGLE today. `policy:` target selects only configured rules.
- Current graph snapshot has no network-network edges and no positions; member status literal "online".
- Cheap NEW per-network stats via AE2 services: energy stored/max/avg in/out, CPUs busy/total, item types, channels,
  controller state, grid size. Byte capacity needs a drive walk (moderate).
- `FlowState` telemetry exists (storage insert/extract, energy supply, processing send/return) but lacks PolicyKey and
  direction; adding them is cheap. Crafting requests are not metered. Observation stream is not read by the GUI.
- No network names exist. Candidates: anvil-renamed controller/drive `customName`, owner profile; player-assigned name
  needs new persistent data.
- DESIGN §17 supports full-domain view, text-labelled states, flow effects only from real delivery events, one directed
  pair editor stating the rule is global; §4.1 same-dimension scope; §17.10 client-side layout.

## Correction: AE2 cable bus in an LDLib2 Scene (source reading, not yet verified in game)

`Scene.createScene` always wraps the level in `TrackedDummyWorld`, which forwards block states/BEs to the proxy but
not `getModelData`; NeoForge's default returns `ModelData.EMPTY`, and `CableBusBakedModel.getQuads` returns nothing
without `CableBusRenderState.PROPERTY`. Expect invisible cables/parts even with the live proxy. Fix: subclass
`TrackedDummyWorld`, override `getModelData(pos)` to return `getBlockEntity(pos).getModelData()`, pass it to
`createScene`. Verify in game before relying on the 3D path.

## Highlighting a network

- 3D: `setRenderedCore(context ∪ network, hook)` and dim context via
  `VertexConsumerWrapper.setColorMultiplier` (RGB only, no alpha/desaturate; baked into cache → `needCompileCache()` on
  selection change; BESR ignores tint). Outlines in an overridden `renderBeforeBatchEnd` with AE2
  `OverlayRenderType.getBlockHilightLine()` + `getBlockHilightLineOccluded()` (x-ray, occluded pass first). Pulse in
  `setAfterWorldRender` with time-based alpha. Cut away with a Y slice / `setBlocked`.
- Network footprint: `IGrid.getNodes()` → owner BE pos (+ part side), dedupe to pos→sideMask, send packed longs
  with revision (5k nodes ≈ 45 KB).
- 2D: MapColor tile + column bitset mask (64×64 = 512 B); dim non-mask, tint mask toward network accent, 1 px accent
  edge; vanilla `DynamicTexture`/`NativeImage` drawn via a custom `IGuiTexture`.
- In world: AE2 `OverlayManager.showArea` is chunk-granular only. Add our own time-limited `NetworkHighlightRenderer`
  at `RenderLevelStageEvent AFTER_LEVEL` using the two AE2 line render types.

## Network naming and identity (see `docs/architecture/network-identity.md`)

AE2 19.2 has no persistent grid id (`Grid.serialNumber` is debug only). Our `NetworkId` lineage is stored in every
node's NBT via `NetworkIdentityGridService`, so it survives controller removal, reloads and restarts. Split while both
halves are loaded → `AMBIGUOUS_SPLIT` on both, recovers when one half disappears or they reconnect. Connecting two
established networks → `AMBIGUOUS_MERGE`, with no automatic tie-break (contract forbids newest-wins); appears
permanent until separated. Player names: key by `NetworkId` in a new overworld SavedData; show/edit only when
`SETTLED`; show ambiguous status with candidate names otherwise; keep orphaned records. A "choose surviving identity"
action would be a separate feature that changes the identity contract.

## User decisions (2026-09-27)

- Scope toggle between current domain and all related domains; edits allowed only for pairs visible in the current
  domain (others read-only).
- Player-editable network names: yes.
- Preview: undecided between 3D and 2D, but either must clearly highlight the network's own blocks.

## AE2 toggle idioms and design round 2 (2026-09-27)

- AE2 icon toggles are `IconButton`/`SettingToggleButton`: 16x16 icon from `ae2:textures/guis/states.png` on
  `TOOLBAR_BUTTON_BACKGROUND` (176,128, 18x20; focus 194,128; hover 212,128). Cycling icons, tooltip carries the text.
  Candidate built-ins: `VIEW_MODE_STORED` (0,16) / `VIEW_MODE_ALL` (32,16); custom local/global icons also fit the cell.
- AE2 switch: `ae2:textures/guis/checkbox.png`, 22x12, OFF (0,28) knob left + dark `#696D88` track, ON (0,40) cyan
  `#9CD3FF` with `#DAFFFF` bar, knob right, hover at x+22. NeoECO reuses these as `SWITCH_*`.
- User decisions: scope = two icon toggles (local domain / global), no text; pair editor rows use switches (OFF on an
  unconfigured rule creates it); rail buttons are page tabs (topology / processing / diagnostics / settings), not
  floating windows; processing mapping is a full-page node graph — drag from a pattern row's output port to an
  endpoint's input port, many-to-many; every Provider and Endpoint shows a small location map with the device marked.
- Current backend: an endpoint has one owning Provider (`EndpointClaimAuthority.compareAndSet` → `OWNER_CONFLICT`);
  the design round 2 drag UI rejects cross-Provider drops onto an owned endpoint for that reason.
- User decision (2026-09-27): this does NOT match the intended model. Endpoints should be shareable by several
  Providers (true many-to-many, possibly across networks); the backend is to be changed together with the GUI later.
  Why the single owner exists today: the Claim epoch authorizes both input and the return binding, and returns land in
  the owning Provider's native `PatternProviderReturnInventory` (docs/architecture/native-integration-gates.md Task 7,
  retained-endpoint-cleanup.md). Returns are selected by call context, never by resource identity, so shared endpoints
  need a new return-attribution rule (e.g. per-dispatch lease/serialized access, or a shared return path into a
  network the waiting CPU can see) plus per-owner retention/release. Design must then drop the "owned" rejection.
- Follow-up (same day): after seeing the return-attribution problem the user decided to KEEP the single-owner model
  and the current design (one Provider per endpoint, cross-Provider drop rejected). Shared endpoints are shelved.
