# GUI fixes from manual testing (2026-10-02)

Seven issues came out of manual testing of the topology workspace. This note records the decisions and the LDLib2 /
Minecraft facts they rest on.

## Switched-off rules leave the graph

- Turning a capability off in the pair editor keeps the rule with `enabled=false`; it is not deleted.
- The graph treats such a rule as absent: no chip on the link label (previously struck through), no link and no energy
  chip. `pairsWithRules()`, `chips()` and `energyChip()` skip disabled rules. The pair editor still lists the rule, so
  it can be switched back on.
- The legend lost its "off" entry, and the `ae2federation.ui.topology.legend.off` lang key was removed.
- The showcase fixture switches off Sky Lab → Nether Outpost storage, so that pair has no link in the showcase.

## GraphView: pan, zoom and link picking over elements

LDLib2 2.2.34 `GraphView` pans only for a left press whose `event.target == this` (a middle press pans anywhere), and
zooms only for a wheel turn whose target is the view itself. Cards, link labels and Endpoint nodes are children of
`contentRoot`, so presses and wheel turns over them never reached the view.

`FederationTopologyView` adds **capture-phase** listeners on the graph:

- On a left press whose target is under `contentRoot`, it calls
  `graph.startDrag(new GraphView.DragOffset(offsetX, offsetY), null)`. GraphView's own `DRAG_SOURCE_UPDATE` handler
  then pans. LDLib2 `Button` fires `onClick` on mouse down, so the press still selects the card first; a refresh that
  rebuilds the cards does not break the drag, because the drag source is the graph itself.
- On a wheel turn under `contentRoot`, it repeats GraphView's zoom math: keep the point under the mouse, change
  `offsetX/Y`, then `setScale`, which refreshes the transform.
- On a left press on the bare canvas (`target == graph`), it finds the nearest link with
  `WireCurve.distance(x, y, LINK_SEGMENTS)`. The point is measured in the link layer's coordinates:
  `getLocalMouse` minus `getPositionX/Y`. A link counts if it is within 5 screen pixels (divided by the scale).
  - Candidates are the pairs with a rule switched on, plus the dashed links from the selected network to the networks
    it discovers.
  - The match selects the pair exactly as a click on its label does.
  - Dashed links are drawn only while a network is selected, and selecting a pair clears the network. So a selected
    pair with no rule switched on draws its own dashed link in the selection colour, with both end marks; otherwise
    the line the player just pressed would vanish while they set its rules.

The capture phase runs before anything under the pointer can stop propagation. `TopologyLink.between` is linear in its
inputs, so a test can rebuild a link in screen space from the card bounds and press on `curve().at(0.3)`.

## Tab rail background ends under its tabs

- `#domain_root` (`.domain-shell`) is a row, so its children stretch to the row's full height. Setting
  `align-self: flex-start` on the rail did not stop this in LDLib2 2.2.34; the rail still reached the window's bottom.
- `#workspace_tabs` is now a transparent column wrapper (`.workspace-rail-column`). It still fills the height, and the
  inner `#workspace_rail` holds the tabs and the `federation:RAIL` background. A column child is only as tall as its
  content, so the background ends under the last tab.
- `ui.graph-controls` checks that the bottom of `#workspace_rail` lies within 8 px below `#tab_mapping` and well above
  the root's bottom.

## Related-domain cards are dashed all round

- A related-domain network card uses `FederationTheme.CARD_RELATED`: a dark ring and a fill, without the light inner
  ring the normal card has.
- Over it lies `dashedBorder(color, 1)`, which is the selection colour while the card is selected or hovered.
- Its bottom state strip is dashed too, so no solid line runs along any edge.

## World highlight drawn over the level

`WorldHighlight` draws at `RenderLevelStageEvent.Stage.AFTER_LEVEL`, which is fired from `GameRenderer` after
`LevelRenderer.renderLevel` returns.

- At that stage the level's model-view stack has been popped. The pose must multiply `event.getModelViewMatrix()` (the
  camera rotation) before translating by the camera position.
- In Fabulous mode, `AFTER_WEATHER` and earlier stages are composited by the transparency chain, so translucent
  layers could still cover the lines. `AFTER_LEVEL` comes after that composite.
- The two RenderTypes are made with `RenderType.create` and the public NeoForge shards; no subclass or access widener
  is needed:
  - shader: `RENDERTYPE_LINES_SHADER`;
  - depth test: `NO_DEPTH_TEST`;
  - write mask: `COLOR_WRITE`;
  - output: `MAIN_TARGET`;
  - transparency: `TRANSLUCENT_TRANSPARENCY`.
- Lines are 6 px over an 11 px dark halo, at 1920 px window width; both scale up with wider windows, as vanilla's
  window-scaled line width does.
  - An anonymous `LineStateShard` subclass overrides `setupRenderState` to set the scaled width.
  - The `rendertype_lines` shader turns lines into screen-space quads, so widths above 1 work in the core profile.
- The group colours are unchanged. The pulse now stays between 0.7 and 1.0 alpha, so the lines never fade out.
- Only the first-person hand (drawn after `AFTER_LEVEL`, with depth cleared) and the GUI cover the outlines.

## Endpoint selected on the topology; diagnostics page removed

- An Endpoint node is selected like a network card: `selectedEndpoint` in `FederationTopologyView`. At most one of
  network, pair and Endpoint is selected; every selection site clears the other two.
- The aside reuses `#network_detail` for the Endpoint, so it matches a network's panel. Rename and the stats panel are
  hidden; `#endpoint_detail` (fact rows) and `#endpoint_identity_panel` are shown.
- The panel contains:
  - title "Endpoint · x, y, z" and a dimension · position · short-id line (the full UUID is in the tooltip);
  - an explanation line (as the node's tooltip says, plus a non-routine claim result);
  - the map/3D preview of the network the Endpoint sits on (`nativeNetwork`), with the Endpoint as the anchor mark;
  - "Highlight 10 s", which outlines only the Endpoint block;
  - "Owner mappings" on `#graph_open`, sent as `openObject("endpoint_mapping", id)`, which leads to the workspace's
    `endpoint_mapping:<id>/<uuid>` receipt;
  - fact rows `#endpoint_fact_{configured,runtime,face,return,owner,claim,native}`;
  - "Bound patterns (N)" rows (`.endpoint-pattern`);
  - the identity block (the `ae2federation.ui.workspace.endpoint_identity` text, the owner instance epoch and the
    native network UUID).
- The server adds these Endpoint choice fields in `FederationDomainPolicySession.endpointFacts`:
  - `x/y/z`, `dimension`, `face`, `returnBinding`;
  - `instanceEpoch`, `claimEpoch`, `generation`, `nativeNetwork`, `ownerPosition`.
- `patterns` (`mappedPatterns`) lists every Provider slot whose controller has `endpointsForSlot(slot)` containing the
  Endpoint. It is computed on the server because the client's slot data is split between the `slot` group and
  `processingProviders` (which skips the selected Provider and is capped).
- An Endpoint block entrance now opens `overview` with `initialEndpoint` (the Endpoint is selected and centred).
- With no domain, the session sends `localEndpoint` (id `local`): one read-only node and panel, with no owner
  navigation and no patterns.

Removed with the diagnostics page:

- `#tab_diagnostics` and `#page_diagnostics`, `FederationEndpointBrowser` (the endpoint table), the endpoint selector,
  `FederationIcons.DIAGNOSTICS` and the diagnostics LSS;
- `Session.endpointDetailText` and `endpointIdentityText`, and the menu holder's server-text binds for them;
- the workspace lang keys used only by them.

Each Endpoint is a node on the graph, so the table's comparison now means selecting nodes.

UI tests:

- `TaskThirtyThreeScenarioSupport.selectEndpointNode(context, "x, y, z")` matches the node's label text. A no-text
  Button's `text()` is empty in the uitest `Texts`.
- `endpointFacts` joins the fact rows as "name: value". The gradle Task 33 gates still check
  `endpointDetail` for "Configured mode: Federated" and `endpointIdentity` for "Claim epoch: 1" and `OWNER_CONFLICT`.
- `attach` no longer records Endpoint text; `attachEndpoint` does so in `ui.endpoint` and `ui.reject-claim-conflict`.
- In the 320x240 Chinese layout the highlight button text does not fit for networks either, so the narrow check
  covers only `#graph_open`.
