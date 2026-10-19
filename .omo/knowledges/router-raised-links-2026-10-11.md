# Raised Router connection correction

User feedback: the Router's middle blue connections look recessed and should protrude. Work stays
in the isolated `design/multiblock-devices` worktree. The Switch contact correction is preserved.

The source Router body is inset one model unit while its core and short four-wide face tabs
reach the block boundary. The earlier multiblock preview extended the panel's blue texture across
seams but left those short tabs unchanged. This produced a recessed-looking middle connection.

Added optional `router_style='raised'` to `tools/multiblock/geometry.py`. It extends the actual
source tabs from 3..4 to 0..4 or 12..13 to 12..16 on connected tangent directions. Their original
height and width remain: top at core height, one model unit above the panel, with visible sidewall
geometry. UV mapping retains the original tab center and extends its endpoint pixels. No core,
outer-frame, source asset or Switch variant change is required.

Sixteen geometry tests pass, including new top-height/sidewall checks, six-face seam continuity,
core/frame preservation, and single-block/Switch invariance. The raised option is a visual
prototype, not completed Minecraft runtime integration or an approved final appearance.

`render_router_links.py` generates before/after and lower-angle close-up images, mesh export,
hash report and editable Blender scene under `build/multiblock-devices/router-raised-v2/`.
Reproduction commands are in `tools/multiblock/README.md`; prior render sets remain intact.

Both renders completed and were inspected. The revised links visibly rise to the core plane and
cast sidewall shadows on the inset backing, especially in the lower-angle comparison. The final
report confirms unchanged source art; PNG dimensions and the editable scene were verified. Prior
Router baseline and corrected Switch mesh exports were compared to current generation and match.
