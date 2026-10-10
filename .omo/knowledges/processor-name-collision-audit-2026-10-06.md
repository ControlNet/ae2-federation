# Processor candidate name collision audit (2026-10-06)

## Candidates and scope

User candidates, not approved renames:

| Chinese | English |
|---|---|
| 协作处理器 | Collaborative Processor |
| 联结处理器 | Nexus Processor |
| 互联处理器 | Mesh Processor |
| 协议处理器 | Protocol Processor |

No exact matching processor name was found in the checked sources or indexed web results.
This is a bounded observation, not a guarantee about every addon, historical release, translation pack, or modpack script.

## Artifact inspection

Read ZIP entries without executing artifacts. Inspected the compatibility cache's ATM10 pack
`0052a619f923007d` (464 top-level JARs), plus downloaded compatibility artifacts; deduplicated by filename,
yielding 483 JARs, including multiple releases of some mods. These are not 483 distinct mods or AE2 addons.
Successfully parsed 653 `assets/<namespace>/lang/{en_us,zh_cn}.json` files. Roots Classic's Chinese JSON failed
strict parsing; a raw-text fallback found no candidate name. All four Chinese strings and English names/IDs
were searched case-insensitively, including collaboration/cooperation variants and space/underscore/hyphen separators.
No matches. Embedded nested JARs, other languages, and resource-pack/script overrides were not scanned.

Representative processor names directly read from artifacts:

| Addon/version | Existing processor |
|---|---|
| ExtendedAE 2.2.38 and 2.2.39 | Concurrent Processor / 并发处理器 |
| AdvancedAE 1.6.12 | Quantum Processor / 量子处理器 |
| MEGA Cells 4.11.0 | Accumulation Processor / 累积处理器 |
| Applied Flux 2.1.5 | Energy Processor / 能量处理器 |
| NeoECO 21.2.0 | Superconducting Processor / ECO - SA 超导处理器 |
| AE2 Lightning Tech 2.1.1 | Overload Processor / 过载处理器 |
| Data Energistics 3.3.3 | Data Processor / 数据处理器 |

Scanned artifacts also include ExtendedAE Plus 1.6.3, ExpandedAE 2.1.3, OmniSequence: Transfinite 2.0.7,
Applied Mekanistics 1.6.3, AE2 Extras, Applied Enhancements, and other ATM10 integrations.
Local source language files were also checked for AdvancedAE, MEGA Cells, AE Additions, NeoECO,
Lightning Tech, Data Energistics and OmniSequence. Existing ExtendedAE checkout directories contained older
Energy Processor language entries; current ExtendedAE naming above comes from the specified release artifacts.

## Similar names, not exact collisions

- Collaborative and ExtendedAE's Concurrent are different words/functions, but both are multi-party computation
  terms ending in Processor. Do not translate Concurrent as 协作 or silently use it as the candidate's English name.
- Nexus is already used by OmniSequence's **Transfinite Compute Nexus**, verified in its local language file and
  [author release notes](https://www.curseforge.com/minecraft/mc-mods/omnisequence-transfinite/files/8877057/additional-files).
- UFO Future uses **Quantum Computation Nexus** and **Stellar Nexus** on the
  [author project page](https://www.curseforge.com/minecraft/mc-mods/ufo-future) and
  [official documentation](https://raishxn.github.io/UFO-Future-1.21.1/).
- GregTech Nexus Addon, an adjacent project with AE2 integration, uses **Nexus ME Hypercore** in its
  [author changelog](https://github.com/Raishxn/GregTech-Nexus-Addon/blob/main/CHANGELOG.md).
- No AE2 processor collision was located for Mesh or Protocol. Generic programming/graphics/network results
  are not Minecraft item-name collisions.

## Web search coverage

Searched all four complete English names with Minecraft/AE2 qualifiers, all four Chinese names,
snake_case IDs, and split concept/processor/Applied Energistics queries. Search engines returned substantial
irrelevant results; negative search results alone are not the evidence for artifact absence.
No item, translation, recipe, or registry changes were made.
