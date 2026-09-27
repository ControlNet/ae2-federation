# GUI v2 phase 4: location maps and in-world highlight (2026-09-28)

## Location map (`FederationMapPreview`)

- A UIElement that draws a top-down tile around a centre from the **client's** loaded level.
  - Each column is `Heightmap.WORLD_SURFACE`, shaded against its northern neighbour like vanilla maps.
  - `MapColor.calculateRGBColor` returns ABGR; swap it to ARGB for `GuiGraphics.fill`.
  - Unloaded columns stay dark. Nothing but positions comes from the server.
- It resamples every 40 frames, or at once when the centre, radius or dimension changes. The radius covers all
  marks (`BlockMarks.radius`, clamped to 8..32 blocks).
- Network detail (`#network_location`: `#network_preview`, `#network_location_note`, `#network_highlight`):
  - The mask is the network's node blocks, taken from `NetworkOverview` `blocks` (flat `[x,y,z,...]`, at most 256,
    same dimension only).
  - The mark is the controller, or the grid pivot when there is no controller.
- Processing detail (`#processing_preview`, `#processing_highlight`):
  - Marks the selected Endpoint, or both ends of the selected wire, or the Provider when nothing is selected.
  - The other devices are tinted.
  - Positions come from the `position` strings (`BlockPos.toShortString`) in the choices.
- Another dimension: the client cannot draw it, so the note says which dimension it is and Highlight is disabled.

## In-world highlight (`client/WorldHighlight`)

- One running highlight at a time: pulsing `LevelRenderer.renderLineBox` outlines for 10 s, drawn at
  `RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS`.
  - It uses a fresh `PoseStack` translated by `-camera`, because in 1.21.1 the camera rotation is already in
    RenderSystem's model-view stack.
- Registered on the client-only entrypoint (`NeoForgeClientEntrypoint`). It is local presentation and sends nothing
  to the server.
- The topology note keeps "Outlining N blocks for 10 s" while that network's highlight runs. Otherwise the per-second
  overview refresh would reset the note (this was a real flaky failure).

## Tests

- `BlockMarksTest` covers parsing, the centre and the radius (pure, no MC types).
- `ui.graph-controls` checks `FederationMapPreview.sampledCells() > 0` and `WorldHighlight.activeBlocks() > 1`, and
  captures `ui-network-location` plus `world-network-highlight`: the highlight is triggered just before the screen
  closes, then the Provider camera is positioned.
- `ui.mapping` highlights the selected Endpoint (`activeBlocks() == 1`).
- `TaskThirtyThreeScenarioSupport.revealInAside(context, selector)` scrolls the topology aside to any element.
- Custom UIElement subclasses that override `drawBackgroundAdditional` and use `GuiGraphics` in method bodies are
  safe on the dedicated server. Only `IGuiTexture` lambdas are not; see the phase 1 note.

Evidence: `gui-v2-phase4-t33/attempt-20260927T162217276Z` (6/6).
