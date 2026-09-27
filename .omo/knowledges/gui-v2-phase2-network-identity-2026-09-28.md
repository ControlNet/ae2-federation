# GUI v2 phase 2: network names, identity states and stats (2026-09-28)

Builds on `gui-v2-phase1-topology-2026-09-28.md`. Design artboards: V2Topology and V2Identity.

## Server side

- `persistence/NetworkNames` (SavedData `ae2federation_network_names`, in the overworld) maps a network id to a display name.
  Its pure logic is in `NetworkNameBook`: names are stripped, can be at most 32 code points, and may not contain ISO
  control characters or `§`. An empty name clears the entry. Unit tests cannot load MC classes such as
  `SavedData`/`CompoundTag`, so keep the testable logic in MC-free classes.
- `workspaceChoices.networks[].name` carries the stored name.
- `NetworkOverview.describe(level, members)` builds per-network JSON: `identity` (a `NetworkIdentityState` key),
  `parts`, a location (first controller, else the grid pivot), energy, I/O, `types`, `cpus`/`cpusBusy`, `channels`,
  `controller` and `nodes`.
  - The session sends it through a separate `stringS2C` binding (`networkOverviewText`), throttled to once every 20
    game ticks or on a membership change, because the choices binding is resent on every change.
- `NetworkIdentityState.of(network, settlements)`: SETTLED wins if any live grid claims the id settled; otherwise
  MERGE, then SPLIT, then COPIED (copied live identity or conflicting node data), then LOADING. With no live grid the
  state is UNLOADED. Only SETTLED can be renamed.
- The `RENAME_NETWORK(14)` action carries `NetworkRenameTarget` (`uuid/name`). It is dispatched with `SET_POLICY`,
  after the container, nonce, sequence and context checks but before the selected-rule revision gate. The server
  re-checks editing rights, membership and the settled state.
- An AE2 part owner is an `appeng.parts.AEBasePart` (it has `getBlockEntity()`); `IPart` alone does not expose it.

## Client side

- Cards are 58 px tall with four lines: name, identity or status, `card_stats`, and devices. The state and stats lines
  update in place (`updateCards`); the graph is rebuilt only on a structural change, including names.
- The aside order is: title and Rename, rename row, ID, detail, Devices, links, then the stats panel (identity,
  location, an energy bar drawn with `FederationTheme.painted`, I/O, types, CPUs, channels).
  - Stats sit last because at 320x240 anything above Devices pushed `#graph_open` and `.network-link` off screen
    (`ui.chinese-scales`).
- Rename flow: `#network_rename` → `#network_rename_row` (`#network_rename_field`, `#network_rename_save`,
  `#network_rename_cancel`). The row stays open, with Save disabled, until the choices carry the new name.
- **Pitfall:** LDLib2 buttons act on press. If a click hides the pressed button, the uitest `.click()` release step
  fails with "Target ... has zero size". Keep the button visible (inactive), or use
  `TaskThirtyThreeScenarioSupport.activateNavigation`, which presses and releases in one step. The server can confirm
  within the same frame, so waiting for confirmation does not avoid this.

## Verification

Run the Task 15, 33 and 34 commands from the phase 1 note, with `-PevidenceDir=.omo/evidence/gui-v2-phase2-t{15,33,34}`.
`ui.graph-controls` now renames the Provider host network to "North Storage", checks the stored name on the server,
then clears it. The verifier requires `networkRename == 'North Storage'` and the `ui-graph-network-renamed` capture.
Evidence: `gui-v2-phase2-t33/attempt-20260927T154648611Z` (6/6).
