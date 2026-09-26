# First GUI redesign implementation

## Scope

The user authorized implementation following the proposed screen direction. The first implementation uses the existing
LDLib2 2.2.34 dependency and keeps the production policy/claim service boundaries. It introduces four persistent pages:
Overview, Policies, Pattern Mapping, and Diagnostics, with a shared header and authoritative status footer.

- `FederationGraphPresenter`: sampled cubic wires using LDLib2 `graphWire` and `DrawerHelper.drawTexLines`, port marks,
  structural rebuilds only, preserved view transform, membership/ownership layer toggles, object inspector.
  This follows the KilaGraph/LDLib2 wire-rendering approach without adding a KilaGraph dependency or claiming an executable graph.
- `FederationWorkspace`: task tabs, direct stable-identity selectors, native AE2 pattern output icons and names,
  client-side pattern search, fixed-height virtual rows, full 64-bit input quantities. NeoECO informed the native item
  presentation and list/detail separation; DataEnergistics informed the dedicated graph surface.
- `SELECT_TARGET` requests retain container, nonce, sequence, domain generation and policy revision checks. Selection
  previews remain authoritative until the server replies. The old numeric action identifiers remain intact.
- Provider keeps its native AE2 screen, with a NeoForge screen-init navigation button. The navigation packet checks
  the currently open native provider menu, container id, still-valid state, concrete Federation provider type and distance.
- Endpoint right-click opens the shared diagnostics page. Device entries prefer the physical domain containing the
  device's Federation node, otherwise require a unique eligible domain containing its native network. They preselect
  the source provider/endpoint. Provider return uses an owned navigation packet, checks the active container, holder and device
  validity on the server thread, and works independently of policy authority. An ambiguous domain never silently grants editing authority.

## Review boundaries

This is an initial usable redesign, not every proposed feature. Remaining refinements include searchable object pickers,
more complete translated diagnostic reasons, a dedicated retained-release dialog (the existing server-confirmed two-step
release remains), detailed names/locations beyond short identities, and richer graph-to-editor navigation. Small-screen
validation targets the existing 1600x960 UI test window at GUI scales 2–4; arbitrary narrow aspect ratios are not certified.
No policy operation/filter editor, batch mutation transaction, live traffic simulation, or mode-changing endpoint control
was introduced. A device outside a unique domain shows the existing pending authority state.

## Test harness notes

- Hidden pages require explicit tab navigation in Task 15 and multi-client harnesses.
- The member summary now contains only the count; the adjacent member list owns individual IDs.
- Pattern row text belongs to its child Label (`virtual-row`), not the enclosing no-text Button.
- During a fresh-world run the old Task 15 fixture timed out before opening any GUI because it compared extended cable
  grids to identities captured before AE2 merged those grids. `extensionsSettled` now re-resolves the operational Bridge
  identities and still checks both live cable attachment paths against those identities.
- Early evidence remains under `.omo/evidence/gui-redesign`; failed attempts are retained for diagnosis.
- Chinese resource reload must finish before scheduling integrated-server fixture steps. Language restoration is now
  a teardown so a failed Chinese scenario cannot contaminate English assertions in later cases.
- World screenshot scenarios move the player camera. Fixture placement now resets to one loaded chunk before building
  the next topology, instead of letting the previous camera decide the next fixture's position/chunk boundaries.

## Device scope finding

A Router physical domain and a direct Bridge domain can contain identical native network memberships while retaining
different domain identities. Matching a native network alone is therefore insufficient for choosing between them. The
Task 33 fixture now connects the Provider's actual upward Federation port to the Router with three real Federation cable
blocks, and the direct Provider-entry test requires a resolved domain. Isolated singleton topology records cannot host
the existing two-network editor. Ambiguous/unavailable scopes retain pending editing state and explain how to open a
specific Router/Bridge; native-menu return remains available.

## Final verification

Successful evidence: `.omo/evidence/gui-redesign/attempt-20260926T093826521Z/result.json` and its `ldlib2/report.json`.
The wrapper finished `BUILD SUCCESSFUL`; both parent and actual client exited 0. The LDLib2 report records 6/6 scenarios,
296 steps, 12 checks (0 failed), and 13 captures. All 237 unit tests passed with no failures/errors/skips. `git diff --check`
was clean. The built development jar contains the updated XML and `ProviderMenuNavigationPayload`.

```sh
./gradlew :neoforge-1.21.1:test --offline --no-configuration-cache --console=plain
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict \
  -PevidenceDir=.omo/evidence/gui-redesign \
  --dependency-verification=strict --no-configuration-cache --console=plain
```

The UI wrapper requires this exact case set and manages a disposable UI-test runtime/world. Tests use the actual client
with software OpenGL and synthetic mouse input, including the native Provider navigation button and server-confirmed
slot selection/mapping. Screenshots cover Chinese GUI scale 4 and the other scenarios' scales 2–3. The dedicated
multi-client runtime suite was not rerun; its source contract and compilation passed.

A navigation-specific test detail: LDLib2's generic `click(selector)` resolves the same selector again on mouse-up.
Provider return changes to a native AE2 screen on mouse-down. The test now checks initial hover, saves the coordinates,
and sends mouse-down/up through the real screen input driver without attempting to resolve a removed widget. The native
AE2-to-workspace direction also uses actual input-driver mouse events, not a direct button callback.

Notable final screenshots, relative to the successful attempt:
- `ldlib2/screenshots/ui.graph-controls/63_ui-graph-controls.png`
- `ldlib2/screenshots/ui.chinese-scales/35_ui-chinese-scale-4.png`
- `ldlib2/screenshots/ui.mapping/54_ui-provider-return.png`
- `ldlib2/screenshots/ui.mapping/62_ui-provider-mapping.png`
