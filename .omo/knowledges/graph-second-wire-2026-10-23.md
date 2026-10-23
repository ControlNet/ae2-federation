# Graph: second wire from a value = extract to variable (G1f-vi, user, 2026-10-23)

- Gesture (user pick): drag a second wire out of a value pin that is already wired. Every non-draft value card's
  pin is always wired, so a plain drag onto an input port now shares (`GraphCards.share` -> `Edits.share`, which is
  `Edits.extract` plus a `Name` on the new port); Ctrl held at the press keeps the old move (`GraphCards.move`).
  `PinDrag.moves` records Ctrl at MOUSE_DOWN (`event.isCtrlDown()`). Drafts keep putCall/insertCall.
- Dropped anywhere but a port: nothing happens (no DRAG_PERFORM handler).
- Name (`GraphCards.variableName`): the call's or attribute's name (`ae2.amount(...)`, `x.amount` -> `amount`);
  `value` for operators, literals and built-ins (`len(...)`), since `len = ...` would hide the built-in. Taken names
  (locals, the file's functions and classes, built-ins, catalog namespaces, workspace files) get `_2`, `_3`, ...
- Refusals: `Edits.share` refuses a port inside the value itself; `GraphCards.share` re-checks the edited file and
  refuses any new checker problem; a new "cannot access local variable '<name>'" becomes
  "<name> would be read before it is set; drag from the value that runs first" (e.g. the `if` condition above).
- `ScriptChecker.Graph` now carries the `Workspace` the graph was checked against, for the re-check.

## Testing notes
- In `ui.automation` the statements inside the alarm's `if` are off screen at 1600x960; `.scroll("#auto_graph", -3)`
  zooms out so `#auto_graph_in_<call>_arg1` is clickable. Hold Ctrl for a drag with `.keyDown(GLFW_KEY_LEFT_CONTROL)`
  / `.keyUp(...)` around `.drag(...)` (LDLib marks the key held, so `isCtrlDown()` sees it).
- Running two UI clients at once made a later install-aside check flake; alone it passed 60/60.
- zsh does not split `$IDS`; feed GameTest ids to `tools/dev_gametests.py` through `xargs`.

Image: `build/extract-wire-check/before-after.png`.
