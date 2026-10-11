# Switch multiblock surface-density follow-up

User feedback: Router is acceptable for now, but the Switch has too much blank space between its
central elements. Work remains in `design/multiblock-devices` at the isolated multiblock worktree.

Implemented optional `links` and `infill` styles in the existing offline geometry builder. Both
use actual Switch texture pixels: cyan connectors join purple centers; the denser option also
fills surrounding pale strips with dark blue-gray panels and adds small junction grooves.
The original center motif, overall white frame and cyan perimeter pieces remain recognizable.
This is code-generated geometry/UV work, not raster editing or replacement artwork.

The baseline `plain` output remains the default. Both Router and Switch baseline geometry were
compared against the prior exported 3x2x2 meshes and match exactly. Eleven geometry tests pass,
including new six-face link-continuity, exterior bounds, and single-block/Router invariance tests.
Python syntax and whitespace checks pass. Runtime Minecraft integration is still outside this
offline visual prototype; user feedback does not select either new variant as final.

`tools/multiblock/render_switch_fill.py` produces a separate three-image comparison set, quad
exports, hash report and packed Blender scene under `build/multiblock-devices/switch-fill-v2/`.
Original previews are retained. Reproduction commands are in `tools/multiblock/README.md`.

All three images were rendered and inspected. The filled option removes the broad white field
and reads more like a continuous chassis; the traces-only option keeps more of the original light
appearance but retains large pale junction spaces. Recommending the filled option is an assistant
assessment, not a user decision. Front and 1x1x4 views were inspected as well. The final report
confirms unchanged source artwork, and all three PNG headers/dimensions were verified.
