# Release image locations and selection

- Latest GUI capture batch: `.omo/evidence/gui-bridge-bilingual-final/attempt-20260926T143352769Z/ldlib2/screenshots/`.
- Recommended GUI image: `ui.graph-controls/297_ui-graph-controls.png` (English topology overview, no tooltip). `143_ui-policy-direction.png` shows the permission editor in an unconfigured state.
- Mapping screenshots such as `ui.mapping/230_ui-provider-mapping.png` and `166_ui-mapping-accepted.png` contain test-only multi-billion input counts; avoid using them as normal gameplay examples. `ui.chinese-scales/70_ui-chinese-graph-scale-4.png` has a large tooltip obscuring the graph.
- Latest default block artwork: `tools/blockbench/versions/v07-isolated-cable/renders/03-family.png` (model render). Actual default in-game captures are under `docs/art/screenshots-v07/`, especially `02-block-faces.png`.
- Latest optional cable preview: `docs/art/cable-flow-prototype/15-junction-final.png`, `18-elbow-final.png`, and `19-elbow-final.gif`. Label the opt-in preview when using these; release 0.0.1 defaults to V07.
- Seven original assets were copied without editing into ignored `build/release-media/v0.0.1/`, with a source index in its README. GUI evidence and cable preview captures are local ignored files; do not assume they are available in a fresh clone.
- Existing in-game block scenes are visual test galleries; for a polished project cover, the family model render is the cleanest existing asset. These captures predate the release tag and are visual references for the same GUI/artwork, not newly captured release-JAR evidence.
