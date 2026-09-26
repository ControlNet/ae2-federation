# V06: return to V04; foreground is a material surface, not an icon

2026-09-26: User rejected V05 as worse than V04 and identified the central error:
AE2 uses substantial colored foreground, with white serving as background.
Our isolated symbol approach left excessive white. Explicitly requested branch
from V04. Preserve V04 white/dark exterior, but fill the functional panel.
Do not continue the V05 diamond/paper/bracket direction.

V06 design script loads V04 native projects. Entire 10x10 center of each of eight
revised tiles uses functional color; three accent values define full Router,
Provider and Endpoint panels and purple ME plates. All exterior pixels match
V04 exactly. No geometry/UV/palette or production changes. V02 Bridge/cable kept.

Measured non-neutral pixels in central 100-pixel field: V04 Router/Provider/Endpoint
64/84/66; rejected V05 48/78/44; V06 100/100/100. Actual locked AE2 Provider and
Interface both 100. Counts quantify area, not art quality or user acceptance.

Archive tools/blockbench/versions/v06-dense-panels: 18 models, eight actual
Blockbench screenshots, reference and coverage records, pixel plan/source,
59-file manifest. All 18 loaded in real Blockbench without missing textures.
Native MCP painting/export; pixel equality, embedded/external texture equality,
geometry/UV, exterior preservation, full coverage and ten unchanged Bridge/cable
project checks passed. V01–V05 hash checks still pass. V06 awaits visual judgment;
no production integration or Minecraft runtime acceptance. Future revisions V07+.
