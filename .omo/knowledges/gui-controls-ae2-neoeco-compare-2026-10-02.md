# GUI controls compared with AE2 and NeoECO, 2026-10-02

Compared at the same scale (6 board pixels per GUI pixel): AE2 19.2.17 jar textures, NeoECO c33f736 textures and
`NETextures`, and Federation's 2026-10-01 `ui.mapping` showcase screenshots (GUI scale 2).

NeoECO's frame, buttons and switch are AE2's own art: its `background.png`/`button*.png` repeat AE2's colours
(`guis/background.png`, `gui/sprites/button*.png`), and its switch, priority text field and toolbar icons load AE2
textures directly through `AppEng.makeId`. Matching AE2 is matching NeoECO.

| Control | AE2 / NeoECO | Federation | Match |
|---|---|---|---|
| Window frame | `background.png`: outline `#413F54`, white highlight, face `#CBCCD4`, bottom band | painted `FRAME`, same colours | yes |
| Button | 9-slice, face `#9A9FB4`, highlight `#ADB0C4`, 3 px lip `#696D88` | painted `BUTTON`, 2 px lip | close |
| Hover / selected | cyan `#9CD3FF`, `#DAFFFF` highlight, `#708CBA` lip | painted `HOVER`/`PRESSED`, same colours | yes |
| Side tab rail buttons | `states.png` 176/194/212,128 18x20 | the same AE2 sprites (`TOOLBAR*`) | exact |
| Text field | AE2 `guis/text_field.png`: gray-blue `#9A9FB4` inset, dark top shadow, white rim, light text `#F2F2F2` | painted `FIELD`: white fill, dark outline, dark text | no |
| Switch | AE2 `guis/checkbox.png` (0,28)/(0,40) 22x12, ring off, bar on | painted `slider`, 28x15, extra dark rim, square off mark | similar, redrawn |
| Dark panel | NeoECO `HOST_PANEL_BORDER` = LDLib2 `BORDER_THICK_RT1` (`gdp_styles.png` 205,154 16x16, 6 px border); AE2 has no dark panel, it uses gray-blue insets | painted `DARK_PANEL`, thinner bevel, inner `#2F2A34` | close |
| Scrollbar | AE2 `small_scroller.png` 7x15 button-like thumb; NeoECO track `CARD_BACKGROUND`, thumb `BUTTON` | thin painted rail | no |
| Text colours | AE2 palette: text `#413F54`, muted `#878FA5` | `#3F3D52`, `#6D6A82` | close |

Federation paints everything except the toolbar buttons in `FederationTheme`, so small drift (lip height, switch size,
text colours) comes from hand-copied values. Using AE2's sprites the way NeoECO does (AE2 is a hard dependency) would
give exact parity for button, text field, switch and scroller. The comparison board was rendered once into
`build/gui-compare/controls-board.png` (not tracked).
