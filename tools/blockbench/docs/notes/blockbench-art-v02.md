# Blockbench V02 art candidate

2026-09-26: User requested actual material redesign using Blockbench with visible
renders, then explicitly required separate versions for rollback/comparison.
Delivered independent snapshots under tools/blockbench/versions/v01-baseline and
tools/blockbench/versions/v02-ae2-ceramic. Do not overwrite a delivered version; future art
changes go to v03 or later. Production resources and business code are untouched.

V01 preserves all prior Blockbench files byte-for-byte, extracts embedded PNGs,
and adds matching-camera renders. V02 contains 21 device/review bbmodels, 20 PNG
textures with animated metadata, 15 review renders including a GIF, archived
pixel plan/scripts, editor validation and content-hash manifest.

Design: muted ceramic shell, local panel seats, cyan Router coupler, staggered
Provider plates, horizontal Endpoint processing window, compact purple ME side
contacts in place of three long stripes, pale Bridge seats and restrained cyan
coupling window. Cable retains real geometry and collars, with subdued glass and
one animated teal volume. No new ports or gameplay semantics. Geometry unchanged;
local depth is pixel shading, not added extrusions.

Actually authored with native MCP paint_with_brush; animation/glass via native
Blockbench Texture.edit inside Undo. Saved native project exports, actual MCP
viewport captures. No AI concept image or alternate renderer. GIF combines 16
unique actual Blockbench frames, 10 fps matching two game ticks/frame. All 17
painted tiles match planned pixels exactly. All 21 final projects actually loaded
in Blockbench 5.2.1 with zero missing textures. verify_art_versions.py validates
hashes, PNGs, dimensions, UVs and Java format; v01 and v02 passed.

A reference screenshot includes real AE2 19.2.17 Provider and Interface textures
read from the project's JAR, left-to-right native Provider / candidate Provider /
native Interface / candidate Endpoint. Native textures/models are not included
in candidate outputs, only the context render. Images are Blockbench previews,
not Minecraft acceptance. Transparent sorting/junction animation and runtime
integration remain to validate after design selection.

Open tools/blockbench/launch.sh with tools/blockbench/versions/v02-ae2-ceramic/models/v02_family.bbmodel.
Review guide: tools/blockbench/versions/v02-ae2-ceramic/README.md. Version guide:
tools/blockbench/versions/README.md. Work scripts write ignored .local/v02-work only; archived
source scripts are provenance copies. The actual editor used virtual display :82
and the project-local MCP profile; test application/display are stopped at end.
