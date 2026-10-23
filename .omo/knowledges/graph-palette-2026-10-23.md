# Graph editor palette: Library groups, Add menu, held-card ghost (user, 2026-10-23)

Owner picks on the design canvas row "图编辑面板" (boards Palette*): both A (Library groups) and B (right-click Add
menu); the dragged card must follow the pointer for every Library entry, namespace functions included.

## Model
- `script/Palette`: one `Entry` type for every group. Groups in order: `statements` (value =, if, for, while,
  return, break, continue), `operators` (17, no `**`: the language refuses it), `builtins` (17), `values` (list,
  dict, set literals), `mine` (the file's functions plus other files' public ones as `file.fn`, acts/lasts from
  `Effects.of`), then the namespaces sorted. `Entry.execIn()` = statement, action or a call giving None;
  `valueOut()` otherwise.
- Statements are written by `GraphCards.insertStatement` (`ScriptParser.statement` parses the text inside
  `def _(): while True:` so `break`/`continue` parse; `Edits.Draft.done()` still reparses the whole program, so
  `break` outside a loop is refused there). Values go on a port with `GraphCards.put`.
- `GraphCards.freshName` gives a new variable `value`, `value_2`, ... avoiding locals, definitions, built-ins,
  namespaces and file names. `renameVariable` renames throughout the function. `addArm` writes `elif False:`
  before the else; `removeLastArm` refuses when there is no elif.
- `Edits.Draft.attach`: an IF always accepts the "else" output (the else is added when missing); a lone `pass` with no
  links that a new statement displaces is removed instead of chained after it. `Layout` always lists "else".

## GUI
- Library rows: `auto_library_ns_<group>` and `auto_library_fn_<group>_<idPart(name)>`; `idPart` keeps
  `[A-Za-z0-9_]` and writes other chars as `x<hex>` (`value =` -> `valuex20x3d`). Group labels use
  `ae2federation.ui.automation.group.<key>`; namespaces show literally.
- Ghost: `FederationGraphView.ghost(entry, DragHandler)` sets `dragTexture` painting the draft at the graph's scale,
  offset `-GRAB_X*scale, -GRAB_Y*scale`, so the dropped draft lands exactly under the ghost. Variable chips use
  `chipGhost`.
- Add menu (`FederationAddMenu`): opens on right-button RELEASE (MOUSE_UP, button 1) on `#auto_graph`; opening on
  press made the release hit the menu (the UI DSL's `rightClick` hit-tests its release target). Escape / press outside
  closes; Enter picks the first match; ids `auto_graph_add`, `auto_graph_add_search`, `auto_graph_add_<group>_<id>`.
- IF cards get a 12 px foot with `auto_graph_elif_add_<id>` / `auto_graph_elif_remove_<id>` (remove only with >1 arm).
  Rename: `auto_graph_rename_<id>` over the variable in `x =` and `for x` titles.
- `show()` drops the last dry run when the text changes: its values are keyed by the old text's node ids, which an
  edit renumbers (before this a drop briefly showed e.g. `True` on a new `pass` card).

## Testing notes (LDLib uitest 2.2.42, SYNTHETIC input)
- Run: `xvfb-run -a -s "-screen 0 1600x960x24" ./gradlew :neoforge-1.21.1:runUiTestClient
  -PfederationUiSelection=ui.automation -PfederationUiOutputDir=$PWD/build/<dir> -PfederationUiWindow=1600x960 -q`
  (`federationUiTest -Pcases=...` is a different runner and refuses this case).
- A held drag: `context.input().mouseDown`, then `dragTo` steps, screenshot, then `mouseUp` (helper `hold`/`release`
  in `AutomationTabScenario`). The frame draws the drag texture at the game's cursor (`MouseHandler.xpos()`), which
  in SYNTHETIC mode stays where the press was; `dragTo`/`placeCursor` do not move it during the drag, and
  `glfwSetCursorPos` is dropped by LDLib's `MouseHandlerMixin`. So the screenshot shows the ghost at the press
  point; in play the cursor moves and the ghost with it. REAL input mode does not get through this scenario.
- `String.replace` on IRON_TOOLS hits both `else:` blocks (refill and low_iron_alarm); use `lastIndexOf`.
- My functions count in the scenario is 4: iron_tools' three plus `smelting.raw_iron_low`.
