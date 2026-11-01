# Third-Party Notices

AE2 Federation is an independent addon for Applied Energistics 2. It is not affiliated with Mojang, Microsoft, the
Applied Energistics 2 team or any other mod listed here. Each project listed here belongs to its authors.

This file covers what the released JAR and this repository contain and what they rely on:

- **Code**: AE2 Federation's own code is licensed under [AGPL-3.0-only](LICENSE). Code adapted from other projects
  keeps its original notice; see *Adapted code*.
- **Artwork**: textures adapted from Applied Energistics 2 are licensed under
  [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/), as AE2's own textures are; the project's
  original artwork is All Rights Reserved.
- **Other files**: the font subset, the README's teaser and the guide's images; see *Fonts* and *Images*.
- **Testing**: the mods and modpacks the test suite runs against, none of which is shipped; see
  *Compatibility testing*.

No dependency JAR is included in this repository or in a release. Players install each dependency from its official
download page.

## Dependencies

| Component | Mod ID | Use | Version range (built against) | Licence as published upstream |
|---|---|---|---|---|
| Minecraft | `minecraft` | required | `1.21.1` | Minecraft EULA; proprietary, owned by Mojang / Microsoft |
| NeoForge | `neoforge` | required | `[21.1.216,)` (`21.1.250`) | LGPL-2.1 |
| Applied Energistics 2 | `ae2` | required | `[19.2.9,)` (`19.2.17`) | API: MIT. Mod: LGPL-3.0. **Textures and models: CC BY-NC-SA 3.0.** Text and translations: CC0 |
| LDLib2 | `ldlib2` | required | `[2.2.34,)` (`2.2.34`) | LGPL-3.0 |
| GuideME | `guideme` | compile-time only; at run time we use the GuideME that AE2 requires | `21.1.1` | LGPL-3.0; some embedded libraries MIT and Apache-2.0 |
| Data Energistics | `data_energistics` | optional; only our Data Energistics plugin classes refer to it | `3.3.5` | AGPL-3.0-or-later (code); All Rights Reserved (resources), per its mod metadata |
| Jade | `jade` | optional; only our Jade plugin class refers to it | `15.10.6` | CC BY-NC-SA 4.0, per its mod metadata |
| Just Enough Characters | `jecharacters` | optional; looked up at run time for pinyin search, nothing compiled against it | — | MIT |

The in-game guide carries example variants for other mods, as resource packs under `resourcepacks/guide_*`. A
variant turns on only when its mods are installed, and refers to their blocks and items by ID in guide pages and
scenes. None of their code, assets or JARs is included:

| Guide variant | Mods it needs |
|---|---|
| `guide_mekanism` | Mekanism |
| `guide_mekanism_appmek` | Mekanism, Applied Mekanistics |
| `guide_appflux_mekanism`, `guide_induction_mekanism` | AppliedFlux, Mekanism |
| `guide_induction_createaddition` | AppliedFlux, Create, Create Crafts & Additions |
| `guide_induction_enderio` | AppliedFlux, Ender IO |
| `guide_induction_industrialforegoing` | AppliedFlux, Industrial Foregoing |
| `guide_advanced_ae` | Advanced AE |
| `guide_create` | Create |
| `guide_extendedae` | ExtendedAE |
| `guide_extendedae_plus` | ExtendedAE, ExtendedAE Plus |
| `guide_ae2lt` | AE2 Lightning Tech |
| `guide_data_energistics` | Data Energistics |
| `guide_neoecoae` | Neo ECO AE Extension |
| `guide_neoecoprototype` | Neo ECO Prototype, Neo ECO AE Extension |
| `guide_omnisequence` | OmniSequence: Transfinite |
| `guide_useless_mod` | Useless Mod |

Mods used only to test compatibility are listed under *Compatibility testing*.

Upstream projects: <https://github.com/neoforged/NeoForge> ·
<https://github.com/AppliedEnergistics/Applied-Energistics-2> · <https://github.com/Low-Drag-MC/LDLib2> ·
<https://github.com/AppliedEnergistics/GuideME> · <https://github.com/ModularMCLib/DataEnergistics> ·
<https://github.com/Snownee/Jade> ·
<https://github.com/Towdium/JustEnoughCharacters>

## Compatibility testing

The test suite runs AE2 Federation together with the mods and modpacks below to check that they work together. The
compatibility runs download each JAR from its official download page, at the URL and SHA-512 pinned in
`tests/compat/mods.json`. Gradle resolves the test source sets' mods from Modrinth or the mod's own Maven. Both land
in git-ignored build directories, so no tested mod is committed to this repository or included in a release.

Licences are as each mod's Modrinth page publishes them; ¹ marks a mod not on Modrinth, whose licence is the one in
its JAR's metadata.

| Mod | Versions tested | Licence as published upstream |
|---|---|---|
| Advanced AE | 1.6.12 | LGPL-3.0-or-later |
| AE All Pattern ¹ | 0.2.6 | MIT |
| AE2 JEI Integration ¹ | 1.2.1 | LGPL-3.0 |
| AE2 Lightning Tech | 2.1.1 | LGPL-3.0-only |
| AE2 Pattern Disk ¹ | 0.8.0 | LGPL-3.0 |
| AE2 WCWT | 1.3.10 | MIT |
| AE2 Wireless Terminals (AE2WTLib) | 19.5.1 | MIT |
| AE2Extras ¹ | 0 | CC0-1.0 |
| Applied Energistics 2 | 19.2.17, 19.2.18 | see *Dependencies* |
| Applied Enhancements ¹ | 1.1.0 | MIT |
| Applied Flux | 1.21-2.1.5 | LGPL-3.0-only |
| Applied Mekanistics | 1.6.3 | LGPL-3.0-only |
| Create | 6.0.10 | Create Mod License (custom) |
| Create Crafts & Additions | 1.7.2 | MIT |
| Data Energistics ¹ | 3.3.3 | AGPL-3.0-or-later (code); All Rights Reserved (resources) |
| Ender IO | 8.2.12-beta | Unlicense (its JAR's metadata says CC0) |
| ExtendedAE ¹ | 2.2.39 | LGPL-3.0 |
| ExtendedAE Plus | 1.6.3 | LGPL-3.0-or-later |
| Functional Storage | 1.21-1.5.7 | MIT |
| GeckoLib | 4.9.3 | MIT |
| Glodium | 1.21-2.2 | MIT |
| GuideME | 21.1.19 | LGPL-3.0; some embedded libraries MIT and Apache-2.0 |
| Industrial Foregoing | 1.21-3.6.39 | MIT |
| Jade | 15.10.6 | CC BY-NC-SA 4.0 |
| Just Enough Items (JEI) | 19.57.0.449 | MIT |
| LDLib2 | 2.2.34, 2.2.41 | LGPL-3.0-only |
| MEGA Cells | 4.11.0 | LGPL-3.0-only |
| Mekanism | 10.7.19.85 | MIT |
| Neo ECO AE Extension | 21.2.0, 21.2.1, 21.2.9, 21.2.10 | GPL-3.0-only |
| Neo ECO Prototype | 1.3.3, 1.3.9, 1.4.0 | GPL-3.0-only |
| OmniSequence: Transfinite ¹ | 2.0.7 | MIT |
| Sophisticated Core | 1.21.1-1.5.5.2363 | All Rights Reserved |
| Sophisticated Storage | 1.21.1-1.6.1.2147 | All Rights Reserved |
| Thunderbolt Core | 2.0.1 | LGPL-3.0-only |
| Titanium | 1.21-4.0.50 | LGPL-3.0 (its repository and JAR; Modrinth lists MIT) |
| Useless Mod ¹ | 2.4.5.10 | MIT |

The Gradle test source sets also compile against these mods, which only those tests use:

| Mod | Version | Licence as published upstream |
|---|---|---|
| Applied Flux, with Glodium and GuideME | 1.21-2.1.4, 1.21-2.2, 21.1.1 | LGPL-3.0-only; MIT; LGPL-3.0 |
| Functional Storage, with Titanium | 1.21-1.3.3, 1.21-4.0.12 | MIT; LGPL-3.0 |
| Pretty Pipes, with Pretty Pipes: Fluids | 1.21.1, 3.1.0 | MIT; MIT |
| GregTech CEu Modern | 7.0.2 | LGPL-3.0 |

Three modpacks are run whole, from the server packs their authors publish on CurseForge. Every mod inside keeps its
own licence:

| Modpack | Version |
|---|---|
| All the Mods 10 | 8.3 (`ServerFiles-8.3`) |
| Create Ultimate Selection 2 | 12.9.0 (server pack) |
| New Age Science and Technology | 2.2.1 (`nast server 2.2.1`) |

## Adapted code

One source file ships code adapted from other projects. It keeps their notice in its header.

| File | Adapted from | Licence |
|---|---|---|
| `common/src/main/java/space/controlnet/ae2federation/client/FederationCableBuilder.java` | Applied Energistics 2's `appeng.client.render.cablebus.CableBuilder` (AE2 19.2.17), Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved; and AE2 Lightning Tech Reborn's `com.moakiee.ae2lt.client.render.OverloadedCableRenderHelper` | GNU LGPL, version 3 or later |

Changes: dense cable paths only, the artist's core and line textures, a 12-voxel core with 10-voxel dense arms,
connection kinds taken from Federation ports instead of AE2's `AECableType`, and the faces a translucent shell would
show inside itself left out. The collars are the artist's, not AE2's. The adapted parts stay under the LGPL, and AE2
Federation as a whole is distributed under the AGPL-3.0-only, which the LGPL-3.0 allows.

## Fonts

`assets/ae2federation/font/canvas_cjk.ttf` draws Chinese on the topology canvas. It is a subset of **Droid Sans
Fallback**, digitized data copyright Google Corporation (c) 2006, licensed under the
[Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0). Droid is a trademark of Google.

Changed for AE2 Federation: cut down to the GB2312 hanzi, the CJK characters of the mod's Chinese strings and the
CJK and full-width punctuation, with the layout, vertical-metrics and hinting tables removed.
`tools/visual/build_canvas_font.py` builds it. The licence text and this change note ship next to the font, in
`assets/ae2federation/font/canvas_cjk_license.txt`.

`assets/ae2federation/font/canvas.json` puts the subset in front of Minecraft's own `minecraft:default` font, which
it references by ID.

## Images

| File | What it is | What it shows |
|---|---|---|
| `docs/teaser.webp` (README, not in the JAR); `ae2guide/assets/teaser.png`, `ae2guide/_zh_cn/assets/teaser.png` (the same render as a palette PNG, in the guide) | an in-game render of a powered build, exported as a GuideME scene on a transparent background; adapted from Applied Energistics 2's image, see below | AE2 Federation's blocks and cables, Applied Energistics 2's blocks and cables, and vanilla Minecraft blocks |
| `ae2guide/assets/statistics.png`, `ae2guide/_zh_cn/assets/statistics.png` | screenshots of the Federation screen's Statistics tab from our UI test run, cropped by `tools/visual/build_guide_statistics.py` | our screen, drawn with Applied Energistics 2's button, text field and scrollbar sprites, and vanilla Minecraft item icons and font |

**The teaser is adapted from Applied Energistics 2's subnetwork image.** That image is the scene of AE2's guide page
`ae2-mechanics/subnetworks` (structure `guidebook/assets/assemblies/subnet_demonstration.snbt` in the
[AE2 repository](https://github.com/AppliedEnergistics/Applied-Energistics-2)), which
[AE2's website](https://appliedenergistics.org/) shows on its home page. We rebuilt that structure in the same layout,
with AE2 Federation's devices and Federation Cable in place of some of AE2's blocks and cables, and rendered it in
game from the same angle, so the teaser reuses no pixels of AE2's image. The design and composition of the build are
the Applied Energistics 2 authors'.

All of these are images of the game. The Applied Energistics 2 artwork in them belongs to its authors under
CC BY-NC-SA 3.0 (see *Dependencies*), and the Minecraft content belongs to Mojang, shown under Minecraft's usage
guidelines. Every other guide scene is a structure the game renders at run time from the installed mods; the guide
ships no picture of it.
