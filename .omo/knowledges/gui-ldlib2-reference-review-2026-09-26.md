# LDLib2 GUI investigation — 2026-09-26

## Follow-up correction: KilaGraph's graph visuals

The initial review underweighted KilaGraph as a graph-rendering reference by focusing on its demo workspace.
KilaGraph itself has a `1.21` branch at `0f1b272cc2a61ddee01a8c23ffc76324cef6fabe`, targeting Minecraft 1.21.1.
Its properties declare build dependency LDLib2 2.2.41 and a runtime range starting at 2.2.33. This is not proof that
every current feature works with our pinned 2.2.34, but the demo's 26.1 default branch is not a categorical barrier.

`RenderTypeGraphView` extends `KGGraphView`, which extends LDLib2's
`com.lowdragmc.lowdraglib2.nodegraphtookit.gui.GraphView`. This is a different, richer class from the generic
`com.lowdragmc.lowdraglib2.gui.ui.elements.GraphView` currently used by Federation.

Direct inspection of our installed **2.2.34 sources JAR** confirms that it already includes:

- Node Graph Toolkit `GraphView`, `NodeElement`, `PortElement`, `WireElement`, inspector and graph models.
- Wire attachment to actual ports; geometry dependency tracking when nodes/ports move.
- Rounded polyline corners sampled with quadratic Bezier segments (`roundCorners(rawPoints, 6, 8)`).
- Endpoint colors derived from port types, selected blue wires, hover-dependent wire width and inactive alpha.
- Full-detail textured wire drawing through `LDLibRenderTypes.graphWire()` and a cheaper flat-line simplified LOD.
- Node LOD, selection/focus overlays, and node style rules in `mc.lss`, `ore.lss`, `modern.lss` and `gdp.lss`.
  The modern style includes dark canvas and SDF rounded node panels/title regions.

Recommendation update: treat KilaGraph / LDLib2 Node Graph Toolkit as a primary reference for Federation graph
appearance and node/port interaction; retain DataEnergistics as a complementary reference for a read-only graph
surface, selection and large-graph rendering. A new KilaGraph runtime dependency is not necessary merely to use
the node and wire facilities already present in LDLib2. Compare adapting the toolkit with reusing its rendering
approach before committing to either; its editor commands/model lifecycle are not a drop-in authoritative domain UI.
Free graph rewiring must not silently imply changing world connections or bypassing server policy/claim checks.

Source: https://github.com/Low-Drag-MC/KilaGraph/blob/0f1b272cc2a61ddee01a8c23ffc76324cef6fabe/gradle.properties

Source: https://github.com/Low-Drag-MC/KilaGraph/blob/0f1b272cc2a61ddee01a8c23ffc76324cef6fabe/src/main/java/com/lowdragmc/kilagraph/graph/util/KGGraphView.java

No new rendering or runtime compatibility test was performed for this follow-up.

## Scope and evidence

This is an investigation and redesign recommendation, not an implementation or a new runtime qualification.
Production code and existing working-tree art changes were left untouched. No issues or pull requests were created.

Reviewed the current production XML, LSS, menu holder, session, graph snapshot/projection/layout, localization,
UI scenario support, historical knowledge, and the source JAR of the pinned LDLib2 2.2.34 dependency.
Viewed actual-client images directly:

- `.omo/evidence/task-42-production/37-final-released.png`: the newer ME Federation Domain interface, including the release control.
- `.omo/evidence/task-33/attempt-20260921T120727803Z/ldlib2/screenshots/ui.mapping/29_ui-mapping-accepted.png`.
- `.omo/evidence/task-33/attempt-20260921T120727803Z/ldlib2/screenshots/ui.chinese-scales/29_ui-chinese-scale-4.png`.

These are historical screenshots. The Task 33 images use older Fabric/Hub terminology and older mapping wording.
They establish visual history, not an exact rendering of today's working tree. No new game client was launched.
The four external projects were inspected from public shallow clones; they were not built or run. Their rendered
GUI quality is not certified by this review. The comparison below concerns concrete source architecture and assets.

External snapshots:

| Repository | Branch inspected | Commit | Declared LDLib2 |
| --- | --- | --- | --- |
| ModularMCLib/DataEnergistics | 1.21 | c95a12448143facd4c3c80b24f7acff443e77295 | 2.2.40, `gradle/forge.versions.toml` |
| DancingSnow0517/NeoECOAEExtension | main | c33f736a821683c6aa4715665ec7c3dadad7765f | 2.2.8 |
| AyaYumi/OmniSequence-Transfinite | 1.21.1-neoforge | 567e73def8dca77dbc1b43462fd7495d4290c373 | 2.2.18; its UI regression notes separately report tests against 2.2.39.a |
| Low-Drag-MC/KilaGraphDemo | master | 90ff912ab24846c37ad0917e883c481c31901f06 | 26.1.2.31, Minecraft 26.1.2 |

The general UI/UX skill's useful contribution was hierarchy, progressive detail, stable selection and explicit states.
Its web landing-page, font-family and mobile breakpoint recommendations do not apply to this Minecraft UI.

## Current production boundaries worth keeping

- `RouterBlock` and `MultipartBridgePart` open the shared Federation Domain workspace.
- `FederationPatternProviderBlock` opens its provider menu through the native menu locator path. Improving the domain
  workspace does not require replacing AE2's inventory/crafting UI.
- `FederationDomainPolicyMenuHolder` constructs XML UI and binds authoritative server values through LDLib2.
- Action requests carry container ID, nonce, sequence, domain reference/generation and expected policy revision.
  `dispatch` validates these before invoking the server session.
- Mapping resolves live providers/endpoints and uses production claim/mapping controllers. Retained endpoints have
  a two-step release confirmation. Preserve the distinction between unmapping and releasing retained ownership.
- Observation subscriptions close with `ModularUI.onRemoved()`.
- Member/pattern lists already use `VirtualScrollerView`; graph coordinates already have a structural cache.
- There are actual-client scenarios for mapping, endpoint diagnostics, claim conflicts, Chinese/scales, and graph controls,
  plus multi-client lifecycle evidence and a bounded 40-node UI benchmark.

## Findings

### 1. The screen is an overloaded diagnostic workspace

`assets/ae2federation/ui/domain.xml` shows membership, directional policy, graph controls, graph, policy acknowledgment,
provider/slot/target selection, mapping actions, mapping acknowledgment, pattern search/list, and endpoint diagnostics
at once. The LSS fixes everything into 396 by 236 logical units: left 72, center 182, right 126.

The historical reason was fitting a 400 by 240 logical viewport at 1600 by 960 / GUI scale 4. It should become a
compact-layout constraint, not the only layout for every window. Main body text is 4, headings 5, and paired button
text 3.5 logical units. Increasing GUI scale cannot repair the hierarchy when the graph itself is also fitted smaller.

The viewed images show large default-text buttons alongside tiny content, dense UUID/status text, and a substantial
grid area that contributes little understanding. Members are repeated in a summary and a list. Main actions compete
visually with diagnostics and layer controls. The newer image also shows a clipped membership heading.

### 2. Some intended styling never reaches the rendered element

`domain.lss:59` uses `background-color`. The pinned LDLib2 sources register `background` (an `IGuiTexture`) and contain
no Java occurrence of `background-color`. Use the actual LSS property, for example `background: color(#ff11191d);`,
and verify the rendered result. A `border(...)` texture alone is not an opaque panel fill.

LDLib2 `Button` owns a separate `TextElement` with class `.__button_text__` and a `textStyle` API. The project targets
that child for left and compact buttons, but sets only `font-size` on the host for graph-toolbar and other sidebar
buttons. This matches the observed large-versus-small button labels. Establish a single explicit button-text rule,
then intentional variants. Do not assume web CSS inheritance or properties apply to LSS.

### 3. The graph has a real pan/zoom surface but no geometric edges

`FederationDomainPolicyMenuHolder.ClientGraphState.render()` creates Label nodes and midpoint Label edges containing
`--- PHYS --->` or `--- CAP ---->`. Their length/orientation does not connect node boundaries or describe an arbitrary
route. Nodes are grouped in three fixed columns at x=20/150/280, then spaced by 38 in each kind.

There are no node/row selection listeners in this renderer. Graph, member list, provider selection and endpoint
diagnostics therefore do not form one directly manipulable workspace. Initial fitting is done by the test scenario
support clicking `#graph_fit`; the production renderer does not automatically fit on initial graph arrival.

Important semantic limitation: PHYSICAL edges currently mean native network membership of providers/endpoints.
CAPABILITY edges currently mean provider-to-endpoint ownership claims. They do not show all storage/crafting/energy/
processing policy permissions, live traffic, or the block-by-block cable/router topology. Rename the layers to match
their data or extend the projection before presenting them as richer topology.

### 4. Serial cycling is the main selection interaction

Consumer, provider, capability, mapping provider, slot, target, and diagnostic endpoint are selected with NEXT actions.
The member and pattern rows are Labels, so search filters the pattern text but does not let the player select its row.
Navigation cost increases with list size, and it is hard to connect a short ID with an actual machine.

Direct selection requires a protocol change: `FederationDomainPolicyActionRequest` currently contains an action enum
and authority fields, without a selected provider/endpoint/slot payload. Add bounded typed selection requests with
server-side domain/identity checks. Do not implement direct selection by issuing a burst of NEXT actions; each action
advances the menu sequence and selection must remain correct across topology changes.

### 5. Presentation data is too close to debug serialization

Patterns arrive as `List<String>`. Projection builds names and quantities into rows, including `empty`, input quantities
and Lane IDs. This supports diagnostics but not useful recipe identification, output icons, clickable slot identity,
localized quantities, or rich tooltips. Extend rows to structured presentation records: stable slot identity, renderable
AE resource/output summary, counts, endpoint associations and states. Do not reconstruct identity from display strings.

Keep stable internal IDs, but display available human-readable names, machine type and location; full identities belong
in a diagnostic disclosure/tooltip. Names and positions are not currently in the graph node snapshot and need explicit
projection fields. Long quantities should retain an exact tooltip/detail value even if the primary display is abbreviated.

`MEMBER`, `provider_pending`, `FEDERATED`, `ACQUIRED`, `ready`, and mapping acknowledgment codes remain literal internal
strings in several paths. Labels should explain status and next steps in the selected client language.

### 6. Visual state is incomplete

`applyState()` updates the policy acknowledgment classes and four policy buttons. It does not apply matching active
states to all mapping/endpoint actions. `.mapping-status.rejected` exists in LSS but no matching class update is wired
in the reviewed holder. Server rejection is not equivalent to visibly disabled/unavailable controls.

The graph layer booleans trigger a rerender but no selected button state. Give toggles a persistent on/off indicator.
Provide distinct loading, no-members, no-provider, no-pattern, search-no-match, conflict and stale-context messages.
Turn release confirmation into a clearly scoped prompt that identifies the endpoint and consequence, while preserving
the existing server confirmation contract.

### 7. Layout reuse is not widget reuse

Every accepted snapshot calls `render()`, which clears all graph children and recreates nodes and edges, even when
the layout cache reuses coordinates. Status lookup also scans the node list for each layout node. Prefer ID-indexed
updates for small graphs; adopt a drawn surface, viewport culling and LOD if measured graph sizes justify it.
The existing ui-small benchmark measures a bounded snapshot/layout workload, not universal large-graph frame-time
acceptance. Add actual rendering/interaction measurements when graph rendering changes.

## Reference projects and applicable lessons

### DataEnergistics: graph rendering and data-driven views

- [CraftingPlanGraphCanvas](https://github.com/ModularMCLib/DataEnergistics/blob/c95a12448143facd4c3c80b24f7acff443e77295/src/main/java/com/fish_dan_/data_energistics/client/crafting/tree/render/CraftingPlanGraphCanvas.java)
  extends `GraphView` and hosts one drawn surface instead of thousands of widgets. It supplies hit testing,
  cursor-centered zoom, fit, selection, neighborhood highlighting and bounded recipe-viewer exclusion areas.
- [CraftingPlanGraphRenderer](https://github.com/ModularMCLib/DataEnergistics/blob/c95a12448143facd4c3c80b24f7acff443e77295/src/main/java/com/fish_dan_/data_energistics/client/crafting/tree/render/CraftingPlanGraphRenderer.java)
  draws geometric routes/arrows, visible nodes, resource icons and state-dependent visuals, with LOD input.
- [TrinityDataCoreHostUi](https://github.com/ModularMCLib/DataEnergistics/blob/c95a12448143facd4c3c80b24f7acff443e77295/src/main/java/com/fish_dan_/data_energistics/gui/ldlib2/trinity/core/TrinityDataCoreHostUi.java)
  mounts an editor-authored `.ui.nbt` tree and binds runtime panels, native slots and synchronization. Its plan-tree
  screen separately uses XML/LSS. This is a mixed authoring approach, not exclusively XML or exclusively Java.

Best fit here: geometric graph edges, stable selection, localized detail on demand, single-surface rendering when
needed. Do not copy its crafting-tree semantics into a potentially cyclic Federation relationship graph. Check each
API against 2.2.34, since this project now declares 2.2.40. Our pinned source already has `GraphViewLod` APIs.

### NeoECOAEExtension: a reusable AE-style component theme

- [eco.lss](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/c33f736a821683c6aa4715665ec7c3dadad7765f/src/main/resources/assets/neoecoae/lss/eco.lss)
  defines panel, button, disabled text, toggle, slot, progress and host-panel variants.
- [NETextures](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/c33f736a821683c6aa4715665ec7c3dadad7765f/src/main/java/cn/dancingsnow/neoecoae/gui/theme/NETextures.java)
  centralizes bordered sprite textures and AE checkbox sprites rather than styling each screen independently.
- [StorageHostPanelUI](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/c33f736a821683c6aa4715665ec7c3dadad7765f/src/main/java/cn/dancingsnow/neoecoae/gui/storage/StorageHostPanelUI.java)
  composes sections, gauges, summaries, exact-value tooltips and synchronized visibility. It explicitly keeps both
  logical sides' sync trees identical when visibility changes.

Best fit here: one Federation theme for panel/button/slot/list/scrollbar/status/tooltip, with compact readable data
components and consistent interaction states. Its fixed panel sizes are not a complete responsive-layout solution.

### OmniSequence-Transfinite: AE visual integration and coordinate correctness

- [AeUiTheme](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/567e73def8dca77dbc1b43462fd7495d4290c373/src/main/java/com/atir/molecularmanipulator/client/AeUiTheme.java)
  shares AE button sprites, slots, panel/text/state colors and LDLib button styling.
- [MolecularCenterLdUi](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/567e73def8dca77dbc1b43462fd7495d4290c373/src/main/java/com/atir/molecularmanipulator/client/MolecularCenterLdUi.java)
  uses XML-defined controls for tabs/actions/meters alongside a container screen. This is a hybrid, not a full-screen
  all-LDLib rewrite.
- [ResponsiveContainerScreen](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/567e73def8dca77dbc1b43462fd7495d4290c373/src/main/java/com/atir/molecularmanipulator/client/ResponsiveContainerScreen.java)
  handles fitted rendering, transformed input and external overlays; `ResponsiveModularUI` converts extra areas.
- [UI regression notes](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/567e73def8dca77dbc1b43462fd7495d4290c373/tools/ui/README.md)
  explicitly distinguish coordinate tests from actual client visual validation.

Best fit here: task tabs, AE-native visual language and matched render/click/hover/tooltip/exclusion coordinates.
Prefer reflow/collapsing detail first; uniform shrinking would compound this project's existing tiny-text problem.

### KilaGraphDemo: editor workspaces and object-centered interaction

- [HologramEditorWindow](https://github.com/Low-Drag-MC/KilaGraphDemo/blob/90ff912ab24846c37ad0917e883c481c31901f06/src/main/java/com/lowdragmc/kilagraphdemo/client/editor/HologramEditorWindow.java)
  assembles `SplittableWindow` views with a KilaGraph `RenderTypeGraphEditorView`, resources, model settings and a
  live world view. The graph implementation is partly provided by KilaGraph, not authored entirely in the demo.
- [HologramEditorScreen](https://github.com/Low-Drag-MC/KilaGraphDemo/blob/90ff912ab24846c37ad0917e883c481c31901f06/src/main/java/com/lowdragmc/kilagraphdemo/client/editor/HologramEditorScreen.java)
  handles dirty state and save/discard/cancel on close, giving dialogs the first chance to consume Escape.
- [ScreenScaleControl](https://github.com/Low-Drag-MC/KilaGraphDemo/blob/90ff912ab24846c37ad0917e883c481c31901f06/src/main/java/com/lowdragmc/kilagraphdemo/client/editor/ScreenScaleControl.java)
  temporarily changes GUI scale and restores it on close, retaining a local preference.

Best fit here: a primary canvas plus selected-object inspector and resizable/collapsible detail. The Federation
workspace does not need shader editing, graph programming, or a new KilaGraph dependency. Its default branch targets
a different Minecraft generation, so this is an interaction reference, not a drop-in implementation.

## Recommended product shape

Use an AE-style neutral shell, restrained Federation accent, pixel-aligned borders/icons, clear inset content regions,
and explicit success/warning/error labels. A dark graph canvas may sit inside the lighter shell. Prototype normal
body text around 8–9 logical units and secondary text around 7–8; validate Chinese and actual GUI scales before fixing
the values. Avoid solving space pressure by shrinking essential text to 3.5–4.

Suggested task navigation: Overview / Policies / Pattern Mapping / Diagnostics.

- Overview: readable member list and relationship graph; select a node to show its details and related edges.
- Policies: direct consumer/provider selection, explicit capability/operation and direction, configured versus effective
  state, clear apply acknowledgment. Do not imply that physical membership grants permission.
- Pattern Mapping: choose provider, click a recognizable pattern/output row, select endpoint targets, then apply a clear
  map/unmap action. Surface retained claims and release separately; Lane is an implementation detail for diagnostics.
- Diagnostics: searchable endpoint table, owner/mode/availability, rejection reason and full IDs when requested.

On a wide logical viewport, show list + primary content + inspector. On a compact viewport, use tabs and a collapsible
inspector so the current task retains readable text. Preserve selected IDs, graph pan/zoom and list position when
switching views. First graph arrival should fit once; ordinary status refresh must not reset the user's view.

Do not imply that a UI class name below already exists. Possible implementation boundaries are a reusable theme,
workspace shell, graph view, policy editor, pattern mapping panel and endpoint inspector. The current server session,
authorization and lifecycle remain the starting point.

## Delivery order and acceptance

1. Correct LSS properties/button child styling; define shared theme and states; localize display status codes.
2. Build a readable task shell with compact/wide layouts and direct selection. Introduce typed selection/presentation
   records where required, preserving request authority and stale-state rejection.
3. Implement real relationship edges, node/list/inspector selection, initial fit and targeted updates.
4. Improve measured large-graph rendering and recipe-viewer integration; expand scenario coverage only around the changes.

Verification command for a subsequent implementation (not executed in this investigation):

```bash
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict \
  -PevidenceDir=.omo/evidence/gui-redesign \
  --dependency-verification=strict --no-configuration-cache --warning-mode=fail
```

Expected: six scenarios pass with authoritative mapping/claim outcomes and fresh captures. Existing selectors/scenarios
will need intentional updates when NEXT controls are replaced. This command's wrapper owns and recreates the temporary
`neoforge-1.21.1/run-uitest` directory; it should only be run under the repository's documented test-run ownership rules.

Additional acceptance should cover visible text and real pointer hit areas at GUI scales 2/3/4; English and Chinese;
long IDs/names; no-provider/no-results/loading states; selected graph neighbors; local view preservation on refresh;
release confirmation; two-client stale revisions; and actual frame times for the agreed graph sizes. Functional scenario
success alone is not a readability or visual-quality judgment.
