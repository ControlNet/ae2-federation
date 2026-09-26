# AE-inspired visual theme, 2026-09-26

The user explicitly emphasized NeoECO and Data Energistics as references for visual style, in addition to interaction ideas.
Reviewed the existing local source snapshots and viewed NeoECO's background/button PNGs. NeoECO uses light gray-violet
pixel bevels and centralized button/slot states (`NETextures`, `eco.lss`). Data Energistics' `CraftingPlanGraphPalette`
uses a gray-blue canvas, light material/process nodes, dark text, and blue selection. This corrected the previous
Federation direction, which had given a dark rounded shell too much weight. Neither reference client was launched.

## Implementation

- `FederationTheme` provides original code-drawn pixel bevels registered as LDLib2 built-in textures on the NeoForge
  **mod bus** `EditorResourceEvent.LoadBuiltin` event. No third-party textures were copied or dependency upgraded.
- Shared shell/buttons/fields use pale gray-violet, dark text, inset/raised edges and restrained blue selection.
  Release actions retain a muted red treatment. State text has dark success/warning/error colors suited to the light surface.
- Graph canvas uses gray-blue, light nodes, darker type borders, and the existing curved-wire renderer and stable view.
- Policy/endpoint browsers and release dialogs share surfaces with the workspace. Pattern and member list viewports
  replace default dark LDLib scroll backgrounds. Search text shadows are disabled and placeholders explicitly dark gray.
- Selector preview label height/vertical alignment now fits the 16-unit compact selectors inside their bevels.

## Important parser finding

LDLib2 2.2.34 `TextureValue.parseMainTexture` does **not** support `color(#...)` as a base texture function. The `color`
function is a modifier after another texture expression. Use a bare `#AARRGGBB` for a solid background. Existing invalid
expressions silently left backgrounds absent/default, which screenshots exposed. All such expressions in `domain.lss`
were replaced. Do not infer that a declared background rendered successfully without looking at the actual client.

## Verification

Final evidence: `.omo/evidence/gui-ae-visual-style-refined/attempt-20260926T133042577Z`.
`BUILD SUCCESSFUL`; 6/6 scenarios, 1189 steps, 109 checks, zero failed checks; 246 unit tests, zero failures/errors/skips.
The scenarios use real client/world/UI with synthetic input, not manual playthrough.

Visually inspected final screenshots: English graph object search; Chinese narrow mapping, diagnostics and rule browser.
Compared the first theme attempt's screenshots before correcting background parsing, residual dark list surfaces,
selector text position and input shadow/placeholder contrast. The first theme attempt also passed functional tests,
illustrating why those alone are insufficient for visual acceptance.

```sh
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict \
  -PevidenceDir=.omo/evidence/gui-ae-visual-style-refined \
  --dependency-verification=strict --no-configuration-cache --console=plain
```

Expected: BUILD SUCCESSFUL, all six scenarios and checks pass. Sources must stay unchanged during the run.
This is the first verified AE-style visual pass, not completion of the broader polish goal or every runtime diagnostic.
