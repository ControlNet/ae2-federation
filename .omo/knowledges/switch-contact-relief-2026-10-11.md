# Switch contact relief correction

The user rejected the V2 flat interior connector treatment because it ignored the original
uneven surface. They clarified that the overall direction is satisfactory: only make the inner
connections consistent with the blue perimeter contacts, including material and recess design.
Preserve that narrow scope rather than redesigning the whole body or enlarging the purple motif.

Implemented optional `edge-matched` offline geometry in the isolated multiblock worktree.
`tools/multiblock/switch_connectors.py` directly reuses Switch elements 15 (cyan contact), 16 and
17 (recessed side pieces), transforming geometry and original UVs to all six faces. The source
0/1/2-model-unit depth profile is preserved. New caps close newly exposed sides; one-unit-deep
necks join contacts to modules. Socket footprints are removed from both original skin and V2
infill before adding two-unit pocket floors and actual walls. V2 layout and outer framing remain.

Thirteen geometry tests pass, including exact contact UV/depth matching, real socket openings,
single-block/Router preservation and prior seam checks. Python syntax and whitespace checks pass.
This remains an offline visual prototype, not Minecraft runtime integration.

`render_switch_contacts.py` renders V2/V3 comparison, original/connected close-up and grazing-angle
detail to `build/multiblock-devices/switch-contacts-v3/`, alongside quad data, a packed Blender
scene and an original-artwork hash report. Prior V1/V2 images are retained. No appearance approval
for V3 is inferred from the user's approval of the overall direction.

All three renders completed and were visually inspected. The middle contacts visibly match the
outer cyan parts; the close-up and grazing view show the stepped recesses and vertical walls.
The final report confirms unchanged source artwork, and all PNG headers/dimensions plus the
editable scene's presence were verified.
