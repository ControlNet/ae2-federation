# ECO - N4/N6/N9 controller GUI on top of NeoECO's ECO - C GUI (2026-10-20)

Design only, nothing implemented yet. The canvas is "AE2 Federation GUI"
(https://claude.ai/artifact/RjetUoACLghDXje7FNzjfd). Its row is titled "Neo ECO 计算子系统主机 · ECO - N4/N6/N9 …" (y 67200),
with these boards:

- `EcoNeoNow`: the original panel.
- `EcoNA`: option A, interactive.
- `EcoNB`: option B, interactive.
- `EcoNBuilder`: the builder.

All of it was checked against NeoECO 21.2.1 source (tag 21.2.1) and in game.

## Why

Our `EcoComputationSystemBlock.useWithoutItem` opened our own 200x72 ID/alias screen. That screen hid NeoECO's controller GUI,
and the structure auto-build went with it. The user's rule is to start from the host mod's GUI and change it, never to
replace it.

## What NeoECO's controller GUI contains

**Opening path.** `ECOComputationSystem.useWithoutItem` calls `BlockUIMenuType.openUI(serverPlayer, pos)`. That calls
`ECOComputationSystem.createUI(holder)`, which calls `be.createUI(holder)`. `createUI` is public and can be overridden on
any `ECOComputationSystemBlockEntity`.

**Original `createUI`.**
- The private `buildPanel` builds `MultiblockBuilderUI.createFloatingPanel(new MultiblockBuilderUI.Config(...))`. The
  config carries length get/dec/inc, mirror get/set, `autoBuild`, formed, in-progress and `createLocalPreviewPlan`.
- `ComputationHostPanelUI.create(config, GuideButton.create(player, "neoecoae:neoecoae_intro/computation_system.md"),
  MultiblockBuilderUI.createInlineOpenButton(buildWindow), buildWindow)` builds the panel.
- The result is wrapped in `new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), player)`.

**Main panel** (`ComputationCpuPanel`, 261x214, `textures/gui/eco_cpu_controller.png` at 0,0). The background already holds
the dark box, the inventory slots, the 8 empty list rows and the scrollbar track.

| Part | Position and style |
|---|---|
| Title | x8 y5, width 157, scale .85, colour 3F3D52. It is clipped: "ECO - C4 Extensible Computation Sub". |
| Status | x8 y13, scale .6, `gui.neoecoae.host.network.connected/disconnected` plus " · x1" (network mode multiplier), in USED or ERROR colour |
| Dark box metrics | Rows at y27/39/51/63. Label at x13 (scale .8, MUTED); value right-aligned to x162 (VALUE). The four rows are CPU Storage, Thread Usage, Parallel Count and Free CPU Memory. |
| Separator | y72, x13-162, colour 0xFF484252 |
| Selected job | y76 name · status; y87 output; y99 amounts; y111 progress. "No Crafting Job Active" at y80. |
| CPU list | "CPU (n)" at x176 y7, scale .75. Rows 67x22 at (176, 21 + 23i). Row sprites are at (0,222) normal and (69,222) selected. |
| Row contents | Name at +3,+2 (scale .666, colour 413F54). Tier overlay at +57,+2. AE2 S_PROCESSOR/S_STORAGE icons at +2/+27, +9, with text at +14/+39, +13. Progress line at +19. |

**Left rail** (`HostSideButtonBar.left`): guide, structure builder (CRAFT_HAMMER), CPU mode (CRAFT_HAMMER for ANY), network
frequency (SCHEDULING_ROUND_ROBIN) and settings (COG).
- Segments are `button_slot_up/middle/down` (23x30/24/27) at tops 0, 23, then +22 each.
- Button backgrounds are ECOIcon TOOLBAR_BUTTON_BACKGROUND (176,128,18,20), with FOCUS at 194,128. They sit at rail x3 and
  y top+3 for the first button, top+2 for the rest.

**Other pages.**
- Settings page (`ComputationSettingsPanel`, 261x132): AE2 `guis/background.png` border 4, title
  `gui.neoecoae.computation.settings.title`. It has three AE2 planner toggles and a back tab (TAB_BUTTON_BACKGROUND 160,192,20,20
  plus BACK 96,16) at (237,-5).
- CPU status page (`ComputationCpuStatusPanel`, 326x254): pause and cancel controls.
- Neither the planner toggles nor the CPU status page apply to us.

**Visibility.** `ComputationCpuPanel`, `ComputationCpuStatusPanel` and `ComputationSettingsPanel` are package-private and
final. The following are public: `ComputationHostPanelUI`, `MultiblockBuilderUI`, `HostSideButtonBar`, `GuideButton`,
`HostElements`, `HostText`, `AETextures`, `ECOIcon`, `NETextures`, `NEStyleSheets`, `MultiBlockBuildController`
(public constructor taking `Host`), `MultiBlockDefinition`/`builder` and `MultiBlockContext`.

**HostText colours:** PRIMARY D6D0E0, VALUE 8377FF, USED 00FC00, MUTED AAA4B2, WARNING FFD65A, ERROR FF6A75.

## Auto-build

**Mechanism.**
- `MultiBlockBuildController.autoBuild` places blocks from `host.getBuildDefinition()`. That method is public. The
  original returns `NEMultiBlocks.getComputationSystemDefinition(tier)`.
- Length is the repeat count (`MultiBlockPlanContext(repeats)`), clamped to the definition's `expandMin`/`expandMax`.
  `getMin/MaxBuildLength` read the limits from `getBuildDefinition()`.
- In creative it builds instantly. In survival it needs the items and builds over time.
- The controller anchor is skipped.

**Why the original definition fails for us.** `createComputationSystem` places `COMPUTATION_INTERFACE` at (2,1,1). Our
calculator rejects an ME interface: option A puts a casing column there. NeoECO's definition would therefore build a
structure that never forms for us.

**Materials at length L (ours):**

| Block | Count |
|---|---|
| Casing | 22 (16 head + interface cell + 5 end cap) |
| Transmitter | L |
| Drive | 2L |
| Parallel core | 2L |
| Threading core | L |
| Cooler | 1 |

At L=11 that is 89 blocks.

**Fix (no layout copied).** Override `getBuildDefinition()` to return a derived definition:
`new MultiBlockDefinition(List.of(ctx -> original.createLevel(new SwappingContext(ctx))), ...)`.
- `SwappingContext` maps `computation_interface` to casing and NeoECO's controller block to ours.
- It copies `repeats` and delegates `getLevel`, `allBlocks` and `isFormed`.
- The derived definition must keep the original's `expandMin`/`expandMax`, or the Length -/+ clamp breaks.
- Verify when implementing whether the original `onFormed` (it sets NEBlock.FORMED) should run for our blocks, or whether
  our cluster already does this. Do not assume a no-op is right.

## Extension approach

**Approach A (recommended).**
- Delete our `useWithoutItem` override.
- In `EcoComputationSystemBlockEntity`, override `createUI` to build our own panel class from NeoECO's public pieces and
  sprites: the same 261x214 frame and sprite, the stylesheet `NEStyleSheets.ECO`, `HostSideButtonBar.left(guide, builder)`
  and `MultiblockBuilderUI`.
- Drive the builder with our own `MultiBlockBuildController(this)`. NeoECO's controller field is private. Tick ours from an
  overridden `tick`.
- Removed: CPU mode, network frequency and the planner settings.

**Alternative B (not recommended).** Mutate the tree returned by `super.createUI`. This is fragile, because the rail and the
builder are children of the package-private panel.

**Risks.**
- This uses NeoECO internals (21.2.1). NeoECO was compiled against LDLib2 2.2.40; the runtime is 2.2.41.
- NeoECO's Jade provider (`ECOComputationSystemProvider`) may still show CPU info for our block. Out of scope.

## Proposed content (the user decides A or B)

**Option A.** The dark box keeps its four rows in their places: ID (value plus a small Copy button), Alias (edited in
place), Compute "used / total CU" and Bandwidth "used / total B/s".
- Below the separator: the selected installation (name · Running/Idle, trigger, "n CU · n B/s").
- The right list becomes "Installed (n)" (string `ae2federation.ui.automation.summary` uses "Installed"). It is read-only.
  Line 2 of each row uses AE2's S_PROCESSOR and S_STORAGE icons for CU and B/s.
- Over capacity, the compute value turns red. No extra text.

**Option B.** The dark box shows only Compute and Bandwidth. ID and alias move to the cog page, in NeoECO's settings-page
style with the title "Computation System Settings".

**Status line.** Keep NeoECO's NETWORK ONLINE/OFFLINE strings and drop " · x1".

## Canvas asset ids (`/_blob/<id>`)

| Asset | ID |
|---|---|
| eco_bg | 1a753275d91d8cfa9816de68d46845b9 |
| row | aad8833d17aa41b514571f3c98dbd3cb |
| row_sel | c2c8923f84e419db45cdec28951d942d |
| toolbar btn | 95cf31fa155209b31a0e4d7f85eedc8f |
| toolbar btn focus | 94849461a6f75e434a78929bc7626b9e |
| help | f9b8baf3c4c15fdc57d0f8d244930fd5 |
| hammer | 971a7f1053f91c0c4f3120b5382d58e4 |
| round robin | 23759138bfc18bad39bec04b2e9d0693 |
| cog | ba8aff3109026f7e779be299a0ddbc64 |
| AE2 S_STORAGE | 3b77ddb3f8430dbf58d74047bf7e1cf4 |
| AE2 S_PROCESSOR | da25bc93ead862453e29be8e775833b4 |
| AE2 S_CRAFT | 9508f5c3bf8a33963691edd781de95c1 |
| slot up | 4f51b88d92207d77007c21e41939f6a9 |
| slot middle | 1d4e4ff037f9cd7a47e8f14a01164c31 |
| slot down | c08f3df653384dc93b3a7378da6afebe |
| eco slot | f29ee6c49793ede8caf06d27a1ad6e35 |
| tab btn | 659985a097ab11851f4d8bb198aa9221 |
| back | 3090fee76fa1350001817ea186179a5a |
| face casing | db22e539734b765d6222045a09f7e791 |
| face transmitter | a96f3a2ec2809361b4f73f53b95f5845 |
| face drive | 9bc2d74ff67f989bb7dbf368e2ce2acc |
| face parallel | bd76e06a08778da84cdd728e6107051e |
| face threading | 481cbfcff8c819f4d37af2de54309fd6 |
| face cooler | b78b034985a5c672dc2bcbc5e8ab02db |

The face sprites are flat block faces; the game draws 3D item icons.

**Nine-slice colours:**

| Surface | Colours |
|---|---|
| panel_bg | outline 413F54, highlight F2F2F2, body CBCCD4, foot 878FA5 |
| button | 9A9FB4 / ADB0C4 / 696D88 |
| focus | 9CD3FF / DAFFFF / 708CBA |
| builder headers | 236E7E |
| builder hint | 6C6980 |
