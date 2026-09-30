# GUI v2 phase 3: processing wires (2026-09-28)

Design artboard: V2ProcessingDrag. Builds on the phase 1 and 2 notes.

## What it is

- The processing page has a Wires/List switch (`#mapping_view_graph`, `#mapping_view_list`, inside `#mapping_view_switch`).
  - Wires is the default. The choice is a static field in `FederationWorkspace`, so it persists for the client session
    and reopened menus keep it.
  - The switch only appears when the choices say `mappingGraph` (the selected Provider has a controller).
  - Native lane Providers keep the list view.
- `FederationProcessingGraph` layout:
  - `#processing_canvas` sits inside `#processing_scroll`.
  - Pattern rows are on the left (`#processing_pattern_<slot>`, output port `#processing_port_<slot>`); empty slots
    get no row.
  - Endpoint cards are on the right (`.processing-endpoint`, `#processing_endpoint_<sanitized uuid:epoch>`, state
    label `.processing-endpoint-state`).
  - The aside `#processing_detail_text` has `#processing_unlink` and `#processing_release`, plus `#processing_status`,
    which is bound to the same mapping feedback as `#mapping_status`.
- Wires are straight lines drawn in the canvas background, one per `slot choice.endpoints[]`. A click hit-tests them
  within 4 px on the canvas `MOUSE_DOWN`.
- Many-to-many: a pattern can go to several Endpoints, and an Endpoint can serve several patterns of the same Provider.
  - The single owner is enforced: a drop onto an Endpoint owned by another Provider (`owner` present, `ownedHere`
    false) is refused locally with the owner's short id, and no request is sent.
  - The server would refuse it anyway with `rejected-owner_conflict`.
- Endpoint click: sends `SELECT_TARGET target:<id>` and shows its claim and mapped patterns.
  - Release is enabled only when the Endpoint is `retained` and is the confirmed target.
  - Release then reuses the existing `PREPARE_RELEASE` confirmation dialog.

## Server contract

- `SET_MAPPING(15)` carries a `MappingWireTarget` (`slot/endpointUuid:epoch/1|0`), an explicit state rather than a toggle.
  - It goes through the normal selected-rule revision gate, like the other mapping actions.
  - `setMapping` selects the slot and Endpoint. If the wire already has the requested state it acknowledges `ready`;
    otherwise it calls `toggleMapping()`, so ownership and claim checks are those of the list view.
- The choices gained `slot[].endpoints`, `target[].retained`, `target[].ownedHere` and `root.mappingGraph`.
- The `accepted-<slot>-<generation>` ack uses the slot's *mapping* generation, which changes on every edit. The Task 33
  verifier expects `accepted-0-2` because the wires section maps and unlinks slot 0 before the list toggle.

## Testing pitfalls

- LDLib2 drag: the source must call `startDrag` from a `MOUSE_LEAVE` listener guarded by `isMouseDown(0)`. The uitest
  `drag(from, to)` expands to hover, press, nudge, leave and release across frames. Drop targets listen for
  `DRAG_PERFORM` and read `getModularUI().getDragHandler().draggingObject`.
- Buttons that hide themselves on press (Unlink clears the selection) need
  `TaskThirtyThreeScenarioSupport.activateNavigation` instead of `.click()`.
- uitest attachments are grouped per step, and the verifier keeps the last record that has a `caseId`. To add evidence
  later in a scenario, re-attach the whole record in one step (see `TaskThirtyThreeClaimConflictScenario`).
- List-view scenarios click `#mapping_view_list` when they first reach the processing page. Because the choice
  persists, later scenarios in the same client inherit it; always click the view you need.

Evidence: `gui-v2-phase3-t33/attempt-20260927T160436280Z` (6/6). Screenshots: `ui-processing-wires`,
`ui-processing-wire-selected`, `ui-processing-endpoint-detail`, `ui-processing-drop-occupied`.
