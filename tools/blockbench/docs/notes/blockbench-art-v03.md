# V03 block refinement

2026-09-26: User preferred V02 over V01, especially cable and Bridge, but asked
for stronger AE2 styling on the full blocks after inspecting native AE2 art.
Read actual locked AE2 19.2.17 JAR textures for Provider (normal/alternate),
Interface, Energy Acceptor, Controller and IO Port. Native Provider/Interface
models are cube_all; the comparison scene uses that verified geometry. Other
reference faces are explicitly a flat texture study. Reference models/PNGs stay
in .local; the version stores only actual screenshots and provenance metadata.

Delivered tools/blockbench/versions/v03-ae2-blocks: 18 embedded-texture projects,
20 textures plus two animation metadata files, nine actual Blockbench images,
archived source and pixel plan, editor report, 59-file manifest. V01/V02 frozen
hashes remain intact. Future edits must use V04 or later.

Eight 16x16 tiles painted through native Blockbench MCP paint_with_brush. Cooler
neutral shell, fewer corner fragments, larger purple ME plates and clearer cyan
functional shapes. Router six faces identical; Provider three pattern plates;
Endpoint broad processing window. All geometry/UVs unchanged. Ten individual
Bridge/cable models and all unrelated textures preserved byte-for-byte from V02.

Validation: all 18 projects loaded in real Blockbench 5.2.1 without missing
textures; eight pixel grids exactly matched exported PNGs using the approved
Pillow pixi environment; embedded textures matched exported PNGs; geometry/UV
and preservation checks passed. V01/V02/V03 manifests verified. Actual project
MCP runs in isolated profile; no user-space MCP config changes. All evidence
is editor-only: no Minecraft acceptance, production replacement or game build
claimed. Read version README for exact repeatable commands. New editor checks
write .local/verification/v03-editor, never overwrite archived evidence.
