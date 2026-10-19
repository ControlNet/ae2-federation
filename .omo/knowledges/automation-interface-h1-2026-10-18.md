# ME Federation Automation Interface, H1 (2026-10-18)

Branch `feature/function-system`. Decisions are in `docs/ideas/function-system.md` ("Interaction interface" and
"The interface as built").

## Adding a domain device: the places to touch

- `FederationDomainNodeKind` plus the exhaustive switches in `OperatingPowerCosts` (`perTick`, `Group.of`) and
  `CableCapacityCounter` (`device`, `addUnits`). The compiler finds the switches; `device()` is an `if`, so it does not.
- The topology view is built from networks and Endpoints, not node kinds, so a new kind stays out of it without
  any change.
- Resources: blockstate, block/item model, loot table, recipe + recipe advancement, `mineable/pickaxe` tag, lang
  (en_us/zh_cn), creative tab, guide pages (en + `_zh_cn`), `GuidePagesContractTest.FEDERATION_ITEMS`,
  `SurvivalRecipeContractTest` (expected map + inputs).
- GameTests: class in `FederationTestMod.registerGameTests`, ids in `tests/scenarios/manifest.json`. The
  `automation.` id prefix belongs to the native-automation cases (`NativeAutomationContractTest` lists them
  exactly), so these use `automation-interface.`.

## Behaviour worth knowing

- A node that links nothing is a one-node domain of its own in the registry (`federationDomainOf` is present).
  Test "not in the cable's domain", not "in no domain".
- AE2 `AEBaseBlockEntity.setBlockState` calls `onOrientationChanged` when the facing changes (wrench, or a
  same-block `setBlock` in a GameTest): the place to reopen face ports.
- Client-bound payload handlers may name client-only classes in their lambda (`AutomationInterfaceScreen.show`);
  the class only loads when the handler runs, on the client.
- In a screen with an `EditBox`, the inventory key (E) closes the screen unless `keyPressed` hands it to the
  focused box first.

## In-game check

`build/automation-interface-check/` (git-ignored): `compare.png` (cable end before / interface after),
`screens.png` (opened, alias set, duplicate alias in red), `full-manifest.txt`. A private server + client copied
from `build/visual-v07` of the main checkout (port 25583, display :87, flat world), the branch jar from
`./gradlew :neoforge-1.21.1:jar`. Typing into a screen: XTEST key events per character (shift for upper rows).
Stop only your own processes: by PID file, never `pkill -f "tail -f cmd"` (other sessions feed servers that way).

## H2: programs on the server

- Network membership of a domain (`FederationDomainSnapshot.memberships`) comes from Switch native evidence, which
  settles some ticks after the Federation links: a GameTest must wait for it, not only for the domain.
- `AEKeyType.loadKeyFromTag(registries, {"id": ...})` logs at debug level only, while `AEKey.fromTagGeneric` warns on
  every failure: use the former for "is this ID a resource" checks, and compare `key.getId()` with the input.
- In-game check `build/programs-check/compare.png`: the H1 jar has no `programs` command, the H2 jar lists a program
  with its one problem. Swap the jar in both `server/mods` and `client/mods`, then restart both.

## H3: the host

- AE2 crafting plans: `ICraftingSimulationRequester.getGridNode()` must return a node on the grid (the pivot works);
  with null, `CraftingTreeNode.buildChildPatterns` skips patterns and the plan only reports the target as missing.
- AE2 plans from `getCachedInventory()`, updated a tick after an insert: a test that inserts and plans in one tick
  sees the materials missing.
- Two networks on one Switch: their energy cells (or any AE2 parts) must not touch, or they merge into one grid.
- The checker wants one item type per list literal: build a test result with `" ".join([str(...), ...])`.
- H3 has no player-reachable surface (programs run only from GameTests until H4's installations), so its in-game
  check comes with H4.

## H4: installations and the scheduler

- `DomainDeviceBlockEntity` holds what a cable-linked domain device needs (ports, ID claim/release, node evidence,
  unload vs break); a new device overrides `kind`, `claim`, `release` and `links`. Add the node kind to the switches
  listed above.
- Checker.Result.effectfulFunctions() tells whether a function acts, through every function it calls: use it to keep
  conditions read-only.
- Brigadier: lambdas cannot be passed as `Object...`; GameTest step lists need a small builder. Count parentheses per
  `.then(` branch when nesting command trees (a depth trace per line found two extra closers).
- Block events from tests: `NeoForge.EVENT_BUS.post(new CropGrowEvent.Post(...))` and
  `new PlayerInteractEvent.RightClickBlock(mockPlayer, hand, pos, hit)` reach the listeners as the game's do.
- In-game check `build/automation-run-check/compare.png`: H2 jar (no device, `install` unknown, lamp dark) vs H4 jar
  (device placed, `install`, `run`, `log` in chat, lamp lit). IDs come from `/ae2federation programs devices` and
  `interfaces` typed into the server console.

## H5: the tab's traffic

- A payload record's static `type(...)` helper clashes with `CustomPacketPayload.type()`: name it `payloadType`.
- Common unit tests run in the NeoForge project, so they can reach NeoForge-module classes in the same package
  (`ProgramPayloadsTest` round-trips the index through a `FriendlyByteBuf(Unpooled.buffer())`).
- Keep "compute the answer" apart from "send it" (`ProgramSync.receive`/`handle` vs `upload`/`request`): GameTests
  then drive the server side without a network connection.
- zsh does not split `$ids` into words: `${=ids}` passes a list of GameTest ids.

## G0: the design row 自动化页签

- Render a Design canvas board: read `project/<Board>.dc.html` and `artifact-type/dc-runtime.js` from the canvas
  (Artifact read with `paths`), save the runtime as `support.js` beside the boards, serve the folder
  (`python3 -I -m http.server`) and run `google-chrome --headless=new --hide-scrollbars --window-size=1440,900
  --virtual-time-budget=8000 --screenshot=<png> http://127.0.0.1:<port>/<Board>.dc.html`. Copy the PNGs to the
  git-ignored `build/automation-design-check/`: the user cannot open the scratchpad.
- Added for G0: `AutoScriptErrors` (problems: gutter mark, wavy underline, problem list, count in the program list,
  Graph disabled, a refused push's answer on the status line), `AutoInstallCondition` (condition picker, run log, Run
  now, devices shown by ID and position as built), `DeviceGui` (proposal: device alias like the interface's).
  `AutoInstallEvent` updated to the events as built (no `ae2.` prefix, no network argument).
- Index gaps the boards expose (H5 follow-ups for G1): no interfaces in `ProgramSync.Index` (the aside names them),
  installation arguments are one repr string with no `args` request, devices are listed server-wide while the tab
  lives in a domain's GUI, `FunctionInfo` drops `_` names (the client can parse the fetched text itself).

## G1a: the Automation tab shell

- A fourth workspace page follows the statistics pattern: a `rail-tab` button and `page_<name>` in `domain.xml`,
  the name in `FederationWorkspace.PAGES` with an icon from `FederationIcons`, `bind<Page>(view)` and
  `view.setOpen(...)` in `show()`, the lang keys `ui.workspace.<name>` and `ui.workspace.tab_title.<name>`. A header
  caption that belongs to one page goes in `header-status` with `off-page` and is toggled in `show()`.
- `createUI` builds the tree on both sides; anything that talks to `ClientPrograms` must be gated on the client
  (`session == null`). `ClientPrograms.open(false)` clears every listener, so listen again after each `open(true)`;
  close the subscription in `FederationModularUI.onRemoved` too, or closing the screen on the tab leaks it.
- LDLib2's `TextArea` content view scissors its own drawing to its content box (`UIElement.drawContents`), so a
  gutter drawn there is invisible. `ScriptEditor` widens the content view's left padding and draws the gutter in
  `drawBackgroundOverlay`, which runs after the children; the caret and selection keep using the content X.
- `CodeEditor` never calls `ILanguageDefinition.shouldIncreaseIndentation`, its comment toggle is a private `//`,
  and Tab inserts two spaces: G1b has to override `insertNewLine`, `toggleCommentAtBol` and `onKeyDown`. Read-only
  without looking disabled: override `isEditable()`.
- The editor's placeholder defaults to "Empty"; clear it with `textAreaStyle(style -> style.placeholder(...))`.
- `ldlib2.uitest.run` takes `name`, `a,b,c`, `group:<g>` or `tag:<t>`; a bare group name selects nothing and
  still passes. All federation scenarios: `-PfederationUiSelection=group:ae2federation`.
- `:neoforge-1.21.1:build` checks the release jar for test registrations; `RegisterCommandsEvent` is allowed only
  in the reviewed command classes listed in `neoforge-1.21.1/build.gradle` (`ProgramCommands` added in G1a; it had
  failed the check since H4).
- The check world (`build/automation-interface-check`): the Switch at 0,-60,10 opens the domain GUI from
  `tp VisualProbe 0.5 -59 13.5 180 30` + right click; GUI clicks need pointer motion (XTEST `MotionNotify`) before
  the button press. Its domain reads "No confirmed Federation Domain membership" (pre-existing); the original
  computing device at 8,-60,8 is on a network outside that domain, a second one was placed at 1,-60,11.

## G1b-i: editing

- `TextArea.setValue` resets the caret, selection and scroll to the start: replace the text from the server only
  while the editor holds nothing unsent, or typing is lost. Compare `ProgramSync.version(editorText)` with the index
  version instead of waiting for answers: closing the tab drops the awaited callbacks (`ClientPrograms.open(false)`).
- Edits reach listeners through `onRawLinesUpdated` (`setLinesResponder`); `setValue(..., false)` does not notify.
- The upload countdown runs on the root's TICK listener: a hidden element does not tick.
- The server answers an upload after sending the new index, so the answer callback can select a just-created file.
- `LocalCopies` must not cache its bases: a second writer (another instance, an outside editor) would be missed and
  an old copy uploaded over the server's text. The UI scenario caught this.
- UI tests: `.type("a:\nb")` presses Enter for `\n`; `.key(GLFW_KEY_SLASH, GLFW_MOD_CONTROL)` holds real Ctrl, so
  `KeyState.isCtrlDown()` sees it; `.typeInto(selector, text)` focuses and replaces a field's text. Clear the
  client's local copies before and after a scenario, or the next run opens an old copy.
- In game, `type_text.py` sends real keys and presses Return; a remote client's copies are under
  `<client>/ae2federation/programs/server-127.0.0.1_<port>/`.
- `pixi run ... python` re-parses its arguments as a shell line: quotes inside an argument break it.

## G1b-ii: problems on the client

- The parser records where each node ends (`ParseResult.ends`: just after its last token). Java evaluates
  constructor arguments left to right, so `new Expr.Binary(id(start), op, left, sum())` draws its ID before the
  right operand is read: such sites wrap the node in `ended(...)`. Do not reorder the arguments: IDs are drawn in
  sequence and would change.
- `ScriptProblems.check(text, workspace)` gives line and column spans; a call's span is its function name (found with
  `Effects.calls`), as the `AutoScriptErrors` board underlines `amout` only.
- `ScriptChecker` runs one daemon thread with a latest-wins slot (`AtomicReference` swapped on submit; a drain task is
  queued only when the slot was empty). The view hands it a snapshot: the editor text, the other programs' received
  texts and a `GameCatalog` over fixed lists from the index plus `RegistryResources(level.registryAccess())`. A result
  is dropped unless the editor still holds the checked text.
- The catalogue's scope must match the server's index count: `ProgramStore.library()` checks with
  `catalog(server, null)` (every interface and network), so the client does too.
- Underline x: `contentView.getContentX() - getScrollX() + stringWidth(styledLineComponent(line, 0, column)) * scale`.
  Draw it after the content view (its drawing is scissored), under its own scissor of the content box.
- Two labels in a row with one `flex: 1` and one `adaptive-width` text from `<lang>` overlapped: one label with a
  coloured appended `Component` is simpler.
- An empty `<label>` shows LDLib2's default text "Label" until code sets it: clear it at construction.
- G1c completion: LDLib2 has no completion UI; the popup is a hand-drawn `UIElement` added to the UI root with
  `TaffyPosition.ABSOLUTE`, placed with `root.worldToLocalLayoutOffset(editor.localToWorld(...))` and kept on screen
  with `adaptPositionToScreen()` on `LAYOUT_CHANGED`, as LDLib2's `Selector` does.
- Stamp completions: a result is shown only if the text and caret are unchanged, and accepting checks them again;
  Enter with a stale or already-typed choice falls through to a new line. A colon must not ask for candidates
  (`-> bool:` then Enter accepted `int`), except inside a resource ID.
- **The domain GUI's tree is built on the dedicated server.** A client-only (`@OnlyIn(CLIENT)`) class that the
  editor passes where a supertype is expected (`root.addChild(popup)`) is loaded by the verifier on the server and
  the GUI fails to open ("Attempted to load class ... for invalid dist DEDICATED_SERVER"). Mark only the drawing and
  measuring methods client-only. The integrated UI tests cannot catch this: check on the dedicated check world.
- A vanilla screen closes on Escape before any element sees the key. `ModularUI.shouldCloseOnEsc(false)` (fluent
  Lombok setter) while a popup is open lets Escape reach the focused element; restore the old value on hide.
- In game, a failed GUI open leaves the player in the world: clicks then break blocks (creative). Verify the screen
  opened before clicking further.
- In game: `press.py` (scratchpad) sends keysyms (`F2` saves a screenshot to `<client>/screenshots`) or `button3`;
  the check world's mod file is `mods/ae2federation-check.jar` on both sides (copy over it, do not add a second).

## G1c-ii: item icons inside the script

- LDLib2's `TextArea` measures the caret, the selection and the popup/underline positions with
  `styledLineComponent(line, 0, column)`. Room for an inline icon is a few spaces (in the text's font) appended in
  that component before the string's opening quote, included when `from <= quote <= to`, so the caret at the quote's
  column sits after the icon. `drawLines` (copied from `CodeEditor`) advances by the same room; the icons are drawn
  in `drawBackgroundOverlay` under the content box's scissor (not inside `drawManaged`, which runs with
  `depthMask(false)`).
- `TextArea.getCursorUnderMouse` maps a click with `font.substrByWidth(...).getString().length()`, which would count
  the room's spaces as columns: `ScriptEditor` overrides it with a binary search over the measured column widths.
  The UI scenario checks that a click at each column's left edge finds that column.
- Not adjusted: `ensureCursorVisible` and the private `getMaxWidth` still measure plain text, so horizontal
  scrolling can be off by the icons' room on long lines (the horizontal scroller is hidden in the tab).
- Strings come from the `CodeEditor` segments: a string token is one segment from quote to quote and a comment is
  one segment, so `ResourceStrings.find` needs no tokenizer of its own. Dark items (coal) are hard to see on the
  editor's background; that is the item art.

## G1d: the Library panel

- `Button` with `text=""` in a layout file calls `noText()`: the label is removed and a later `setText` shows
  nothing. Give such a button any text in the layout file.
- `Button.setOnClick` fires on the press (`MOUSE_DOWN`). A control that hides itself or its row on click should
  listen to `UIEvents.CLICK` instead: the UI test's `click` then finds the target on release (else "Target ... has
  zero size").
- Style sheet `display: none` does not hold a `TextArea` scroller hidden: `updateScrollers` calls `setDisplay` itself.
  Use `scroller-horizontal-display: never` (`ScrollDisplay` AUTO/ALWAYS/NEVER). `scroller-view-mode: horizontal`
  turns the wheel into sideways scrolling.
- `flex: 0` sets a zero basis, so an element with a code-set height collapses in a column. To size a `ScriptEditor`
  (whose `.script-editor` rule has `flex: 1`), set the height on its parent box.
- `TextArea`'s scroll range comes from the private `getMaxWidth` (plain text). The read-only example pads each line
  with the icons' room (`ScriptEditor.setReadOnlyLines`, again in `onTextAreaStyleChanged` once the style sheet sets
  the font) so a line's end can be reached; `scrollX` has no setter, so the range cannot be fixed otherwise.
- `editor(context)` in the scenario must select `#auto_script .script-editor`: the library's example is another
  `.script-editor`, earlier in the tree.

## G1e-i: the Install view

- Style sheet `justify-content: space-between` did not spread a row's children inside a `ScrollerView`; setting
  it in code (`layout(style -> style.widthPercent(100).flexDirection(ROW).justifyContent(AlignContent.SPACE_BETWEEN))`,
  as `FederationProcessingGraph` does) works. Without it the two columns sat side by side with no room for wires.
- Wires are drawn in the canvas element's `drawBackgroundAdditional` between the ports' `getPositionX/Y` (screen
  positions), with `FederationProcessingGraph.polyline(new WireCurve(...))`, so the cards paint over them.
- The aside's argument fields use AE2's `text_field.png` (`FIELD`): in game it is a mid grey, so dark text
  (`#413f54`) reads better than the light text the domain's text fields use; the board draws them lighter.
- A Computing Device beside the UI test's Router: a federation cable at `routerPosition().east()` and the device at
  `.east(2)` join the Router's domain; wait for `ComputingDevices.domainOf` before installing.
- Check world: `ae2federation programs install <device> <file> <function> <args>` takes the arguments separated
  by spaces (`"minecraft:iron_ingot" 256 "interface-bk8hty"`), not commas. Device `device-w7c47q` at 1,-60,11 is in
  the Switch's domain; the Install chip is at (1852,47), the first installation row at (960,205), the aside's
  actions at y=397 (Uninstall 1390, Open in editor 1590, Run now 1792). Compare: `build/automation-install-check/`.

## G1e-ii: editing in the aside

- `TextField` has no Enter or commit hook: listen to `UIEvents.KEY_DOWN` (Enter, keypad Enter) and `UIEvents.BLUR`.
  `field.focus()` does nothing before the field is in the UI tree: open a field drafted during a rebuild only after
  the aside holds it (`opening` runs at the end of `rebuildAside`). Select the text on open
  (`setCursor(len)` + `setSelection(0, len)`), or typed digits are appended (seen in game: 256 became 256128).
- A new index rebuilds the whole aside; skip that while one of its fields is focused, or the typed text is lost.
- `TextField`'s default placeholder is "Empty"; clear it with `textFieldStyle(style -> style.placeholder(...))`.
- Style sheet `flex-wrap` did not take; `layout(layout -> layout.flexWrap(FlexWrap.WRAP))` does, and the row needs
  `flex: 1; min-width: 0` to have a width to wrap in.
- `flex: 1` in a column gives a zero basis: an element with a set height and no parent height collapses to zero
  (the dropdown's field). Give such elements `width: 100%; height: N; flex-shrink: 0` instead.
- LDLib2's `Selector` options fire on the press (`setOnClick`) and its dialog is an overlay; a plain list of rows
  listening to `UIEvents.CLICK` in the aside's flow is simpler to test and survives the rebuild a choice causes.
- The check world has no `args` command; reset an argument through the GUI. Compare:
  `build/automation-install-edit-check/compare.png`.

## G1e-iv: installing by drawing a wire

- Same drag pattern as the Processing graph: a row's `MOUSE_DOWN` remembers the press, a capture `MOUSE_MOVE` on
  `ui.rootElement` starts `port.startDrag(object, null)` past 3 px, capture `MOUSE_DOWN`/`MOUSE_UP` forget it;
  targets listen to `DRAG_ENTER`/`DRAG_LEAVE` (capture) and `DRAG_PERFORM`; `getModularUI().getDragHandler()`
  tells what is dragged. The UI test's `.drag(from, to)` drives it.
- A painted texture's lambda is read on every draw, so a card ring can follow the drag state without a rebuild.
- `ProgramSync` sends the new index before the answer: select an installation named in the answer, then rebuild.
- In game: `drag_hold.py x0 y0 x1 y1` (scratch helper) presses and moves without releasing, for a screenshot of the
  lit cards; compare `build/automation-install-drag-check/compare.png`.

## G1e-iii: state and log

- `ProgramSync.perform` calls `changed()` after every request, which sends the index to every subscriber: a request
  polled every second (`log`) must return before that.
- A request's answer can be dropped (closing the tab clears the awaited callbacks): a poller that waits for its
  answer needs a way out (the Install view asks again after 100 ticks).
- `UIEvents.TICK` on the aside polls only while the Install view is shown: hidden elements do not tick.
- Update the State row and the log box in place; a full `rebuildAside` each second would close an open field.
- Compare: `build/automation-install-log-check/compare.png`.

## G1c-iii: stock in the completion footer

- `ProgramSync.perform` answers `stock [domain, resource]` before `changed()`, like `log`: it calls the host's
  `ae2.amount` through a throwaway `ProgramHost(server, domain, "tab")`. A `RunError` (unknown resource) must be
  rethrown as `IllegalArgumentException`, or it escapes the request handler.
- The popup asks `ScriptEditor`'s resource note on every draw; the tab caches answers per resource and asks again
  after 5 s, so the note fills in on a later frame without any repaint call.
- Completion lists resources only where the type is known: type `iron: Item = "minecraft:iron_in`, not `x = "...`.
- The check world's domain has no networks ("No confirmed Federation Domain membership"), so it reads "in stock: 0";
  the exact amount is checked by the `aStockChangeStartsARun` GameTest (5 ingots inserted, "5" answered).
- Compare: `build/automation-stock-check/compare.png`.
