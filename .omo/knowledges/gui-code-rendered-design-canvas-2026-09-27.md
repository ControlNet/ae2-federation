# Code-rendered GUI design canvas, 2026-09-27

The user asked to render the current GUI as a design baseline **from source code, not client screenshots**.
Canvas (private Design artifact): https://claude.ai/artifact/RjetUoACLghDXje7FNzjfd — 8 artboards: Overview graph,
Bridge diagnostic, Policies (+ rules dialog), Pattern mapping (+ release dialog), Diagnostics (+ endpoint dialog).

## Mapping from code to HTML (1 GUI unit = 2 px)

- Root size comes from `FederationDomainPolicyMenu.register`: Router/device 640×400, endpoint 440×280, bridge
  diagnostic 360×160, clamped to `screen - 8`; `compact` class when screen height < 280. The 396×236 in `domain.lss`
  is overwritten at init. Screenshots at 1600×960 / GUI scale 4 are therefore the compact variant, not the default.
- Bevels (`FederationTheme.bevel`): 1 px `#535669` outline, 1 px light top/left, 1 px shadow bottom/right, fill.
  FRAME `#c8c8d2/#f5f5f8/#73758a`, FIELD `#e0e0e6/#77798d/#f4f4f7` (inset), BUTTON `#afb2c5/#e9eaf1/#686c83`,
  HOVER/primary `#c5deed/#f4faff/#638aa3`, PRESSED/selected `#acd3e5/#638aa3/#edf8ff`, DANGER `#d5b6bd/#ffe6e9/#916271`.
  Disabled buttons fall back to BUTTON with text `#747589`, even when `.primary-action`.
- Font: LDLib2 `modern.lss` uses `ldlib2:jetbrains_mono_bold` → JetBrains Mono 700. Sizes 10/8/7 → 20/16/14 px.
- Graph: columns x = 20/150/280, rows y = 20 + 38·row, nodes 96×31, then `fitToChildren(12)`. Wires are cubic
  Béziers right-mid → left-mid with tangent `max(20, dx/2)`, colored from→to kind (member `#28667c`, provider
  `#69508d`, endpoint `#875d28`), 3 px (5 px highlighted), dimmed to alpha 0x44 when another node is selected.
  Grid background `#999fb3`, minor 24 u, accent every 96 u.
- Dialogs (LDLib `Dialog`): overlay FRAME, title bar INSET, content PANEL. Widths: endpoint browser 420, rule browser
  360 (height `max(130, 88 + min(6,n)·30)`), release 300.

Item icons in pattern rows are hand-drawn SVG stand-ins for `ItemStackTexture`; IDs/coordinates are sample data from
the UI test fixtures (some UUID tails are illustrative).
