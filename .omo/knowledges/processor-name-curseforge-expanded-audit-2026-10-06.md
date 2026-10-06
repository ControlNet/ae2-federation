# Broader CurseForge AE2 processor naming audit (2026-10-06)

## Result

No exact match was found for the four candidate processor names in the 33 successfully inspected source
repositories (58 English/Simplified Chinese language files), supplementing the earlier
[artifact audit](processor-name-collision-audit-2026-10-06.md).
The candidates are Collaborative Processor / 协作处理器, Nexus Processor / 联结处理器,
Mesh Processor / 互联处理器, and Protocol Processor / 协议处理器.
Names remain proposals; no implementation or final naming decision is authorized by this investigation.

## Method and limits

- Discovered projects through CurseForge's Applied Energistics 2 category, including returned listings from
  pages 1–7, plus targeted project and exact-name searches. Category responses were partial and different cached
  pages reported different totals; do not claim to have exhaustively reviewed all category projects.
- Followed each selected project's CurseForge Source link. Downloaded 34 public GitHub default-HEAD source
  archives into temporary storage and read only English and Simplified Chinese language resources.
- 33 repositories contained matching language resources, totaling 58 files. AE2-Additions' linked default
  archive contained none, so it is an unresolved entry, not a negative inventory result.
- Included legacy .lang resources and modern .json resources, case-insensitive locale filenames, Unicode-decoded
  JSON text, Chinese full names, English full names/IDs, and collaboration/cooperation spelling variants.
- Source snapshots can differ from published releases. No attempt was made to inspect every historical branch,
  binary, translation pack, or custom modpack script. Some repositories only contained one target language.
- Exact-name CurseForge searches in Chinese returned no results. English queries mostly returned irrelevant
  material; negative indexed search is supporting context, not proof of exhaustive absence.
- No mod JAR or downloaded project code was executed. No source/recipe/name changes were made in Federation.

## New naming context

1. **NexusAE** is an existing AE2 addon on CurseForge:
   [project](https://www.curseforge.com/minecraft/mc-mods/nexusae),
   [file listing](https://www.curseforge.com/minecraft/mc-mods/nexusae/files/all).
   Indexed author description names Quantum Synthesis/Processing/Division Units. Its binary inventory was not
   obtained; no assertion is made that its complete inventory lacks Nexus Processor.
2. **AE2Enhanced** has Hyperdimensional Storage Nexus, visible in the
   [author page](https://www.curseforge.com/minecraft/mc-mods/ae2enhanced) and language resources.
3. **UFO Future** uses Nexus for controllers and storage tiers; **OmniSequence** has Transfinite Compute Nexus
   as documented in the earlier audit. These are related names, not exact processor collisions.
4. **OMNI Cells** defines Omni Link Processor / 全能链路处理器,
   Complex Link Processor / 复杂链路处理器, and Multidimensional Expansion Processor / 多维展开处理器.
   [Source language file](https://github.com/Frostbite-time/AE2OmniCells/blob/HEAD/src/main/resources/assets/ae2omnicells/lang/en_us.json).
   Link is conceptually near 联结/互联, but none is Nexus Processor or Mesh Processor.
5. **AE2 Crystal Science** defines Resonating Processor / 谐振处理器.
   [Chinese language file](https://github.com/ExtremelyFrozen/AE2-Crystal-Science/blob/HEAD/src/main/resources/assets/ae2cs/lang/zh_cn.json).
   This is an exact collision for the earlier rejected 谐振 candidate, not for the current four.
6. **Lazy AE2** has Massively Parallel Processor and Speculative Processor; **Lazier AE2** has Parallel Processor;
   **Prism Plan** has Deduction Processor / 演绎处理器; **Crazy AE** has Quantum, Mana and Energy processors.
   These broaden the existing processor reference set without matching the four candidates.

## Per-project source coverage

Default HEAD was read on the audit date; links below are moving references.
Language counts are file counts, not a promise that both languages exist for every project.
Forks are separate projects and are explicitly recognizable in the list.

| CurseForge project | Source repository | Language files | Result |
|---|---|---:|---|
| [AE2-Additions](https://www.curseforge.com/minecraft/mc-mods/ae2-additions) | [ADT-Team-com/AE2-Addition](https://github.com/ADT-Team-com/AE2-Addition) | 0 | Unresolved: no target language resources |
| [AE2: All Encompassing](https://www.curseforge.com/minecraft/mc-mods/ae2-all-encompassing) | [Zershyan/AE2-All-In-One](https://github.com/Zershyan/AE2-All-In-One) | 2 | No exact candidate hit |
| [ae2-crystal-science](https://www.curseforge.com/minecraft/mc-mods/ae2-crystal-science) | [ExtremelyFrozen/AE2-Crystal-Science](https://github.com/ExtremelyFrozen/AE2-Crystal-Science) | 2 | No exact candidate hit |
| [ae2-overclocked](https://www.curseforge.com/minecraft/mc-mods/ae2-overclocked) | [MOAKIEE/ae2overclocked](https://github.com/MOAKIEE/ae2overclocked) | 2 | No exact candidate hit |
| [AE2 Stuff](https://www.curseforge.com/minecraft/mc-mods/ae2-stuff) | [bdew-minecraft/ae2stuff](https://github.com/bdew-minecraft/ae2stuff) | 2 | No exact candidate hit |
| [AE2 Stuff Unofficial](https://www.curseforge.com/minecraft/mc-mods/ae2-stuff-unofficial) | [AE2-UEL/ae2stuff](https://github.com/AE2-UEL/ae2stuff) | 2 | No exact candidate hit |
| [ae2-uel-extended](https://www.curseforge.com/minecraft/mc-mods/ae2-uel-extended) | [beecupbe/ae2uel_extended](https://github.com/beecupbe/ae2uel_extended) | 2 | No exact candidate hit |
| [AE2 Utility](https://www.curseforge.com/minecraft/mc-mods/ae2-utility) | [lhy512103/AE2-Utility](https://github.com/lhy512103/AE2-Utility) | 2 | No exact candidate hit |
| [ae2enhanced](https://www.curseforge.com/minecraft/mc-mods/ae2enhanced) | [aeddddd/AE2Enhanced](https://github.com/aeddddd/AE2Enhanced) | 2 | No exact candidate hit |
| [Applied Extended Crafting](https://www.curseforge.com/minecraft/mc-mods/applied-extended-crafting) | [GaLicn/Applied-Extended-Crafting](https://github.com/GaLicn/Applied-Extended-Crafting) | 2 | No exact candidate hit |
| [applied-generators](https://www.curseforge.com/minecraft/mc-mods/applied-generators) | [sapporo1101/AppliedGenerators](https://github.com/sapporo1101/AppliedGenerators) | 2 | No exact candidate hit |
| [Applied Greg](https://www.curseforge.com/minecraft/mc-mods/applied-greg) | [ch1335/Applied-Greg](https://github.com/ch1335/Applied-Greg) | 2 | No exact candidate hit |
| [applied-integrations](https://www.curseforge.com/minecraft/mc-mods/applied-integrations) | [indiscrete-void/Applied-Integrations](https://github.com/indiscrete-void/Applied-Integrations) | 1 | No exact candidate hit |
| [Applied Replicatics](https://www.curseforge.com/minecraft/mc-mods/applied-replicatics) | [Lapis256/Applied-Replicatics](https://github.com/Lapis256/Applied-Replicatics) | 1 | No exact candidate hit |
| [Applied Soul](https://www.curseforge.com/minecraft/mc-mods/applied-soul) | [Y-Xiao233/AppliedSoul](https://github.com/Y-Xiao233/AppliedSoul) | 2 | No exact candidate hit |
| [AppliedE](https://www.curseforge.com/minecraft/mc-mods/appliede) | [62832/AppliedE](https://github.com/62832/AppliedE) | 2 | No exact candidate hit |
| [Applied Energistics Oddities](https://www.curseforge.com/minecraft/mc-mods/appliedenergisticsoddities) | [zhichaoxi2006/AppliedEnergisticsOddities](https://github.com/zhichaoxi2006/AppliedEnergisticsOddities) | 2 | No exact candidate hit |
| [Bigger AE2](https://www.curseforge.com/minecraft/mc-mods/bigger-ae2) | [DancingSnow0517/BiggerAE2](https://github.com/DancingSnow0517/BiggerAE2) | 2 | No exact candidate hit |
| [BloodMagic AE2 Addition](https://www.curseforge.com/minecraft/mc-mods/bmaddon) | [edgemq/BloodMagicAdditions](https://github.com/edgemq/BloodMagicAdditions) | 1 | No exact candidate hit |
| [Crazy AE](https://www.curseforge.com/minecraft/mc-mods/crazyae) | [beecupbe/crazyae](https://github.com/beecupbe/crazyae) | 2 | No exact candidate hit |
| [dynamistics](https://www.curseforge.com/minecraft/mc-mods/dynamistics) | [eutro/dynamistics](https://github.com/eutro/dynamistics) | 1 | No exact candidate hit |
| [EnderDrives](https://www.curseforge.com/minecraft/mc-mods/enderdrives) | [STS15/enderdrives](https://github.com/STS15/enderdrives) | 2 | No exact candidate hit |
| [extracells2](https://www.curseforge.com/minecraft/mc-mods/extracells2) | [ExtraCells/ExtraCells2](https://github.com/ExtraCells/ExtraCells2) | 2 | No exact candidate hit |
| [Extra CPUs](https://www.curseforge.com/minecraft/mc-mods/extracpus) | [rlnt/minecraft-extracpus](https://github.com/rlnt/minecraft-extracpus) | 1 | No exact candidate hit |
| [Lazier AE2](https://www.curseforge.com/minecraft/mc-mods/lazierae2) | [AlmostReliable/lazierae2-forge](https://github.com/AlmostReliable/lazierae2-forge) | 2 | No exact candidate hit |
| [lazy-ae2](https://www.curseforge.com/minecraft/mc-mods/lazy-ae2) | [phantamanta44/Lazy-AE2](https://github.com/phantamanta44/Lazy-AE2) | 2 | No exact candidate hit |
| [Logistics Bridge](https://www.curseforge.com/minecraft/mc-mods/logistics-bridge) | [tom5454/LogisticsBridge](https://github.com/tom5454/LogisticsBridge) | 1 | No exact candidate hit |
| [[Remake] Logistics Bridge](https://www.curseforge.com/minecraft/mc-mods/logistics-bridge-dmnedition) | [Domaman202/LogisticsBridge](https://github.com/Domaman202/LogisticsBridge) | 1 | No exact candidate hit |
| [Me-Beam-Former](https://www.curseforge.com/minecraft/mc-mods/me-beam-former) | [GaLicn/ME-Beam-Former](https://github.com/GaLicn/ME-Beam-Former) | 2 | No exact candidate hit |
| [nae2](https://www.curseforge.com/minecraft/mc-mods/nae2) | [AE2-UEL/NAE2](https://github.com/AE2-UEL/NAE2) | 2 | No exact candidate hit |
| [OMNI Cells](https://www.curseforge.com/minecraft/mc-mods/omni-cells) | [Frostbite-time/AE2OmniCells](https://github.com/Frostbite-time/AE2OmniCells) | 2 | No exact candidate hit |
| [prism-plan](https://www.curseforge.com/minecraft/mc-mods/prism-plan) | [GTQT/PrismPlan](https://github.com/GTQT/PrismPlan) | 2 | No exact candidate hit |
| [thaumic-energistics](https://www.curseforge.com/minecraft/mc-mods/thaumic-energistics) | [Nividica/ThaumicEnergistics](https://github.com/Nividica/ThaumicEnergistics) | 1 | No exact candidate hit |
| [ufo-future](https://www.curseforge.com/minecraft/mc-mods/ufo-future) | [Raishxn/UFO-Future-1.21.1](https://github.com/Raishxn/UFO-Future-1.21.1) | 2 | No exact candidate hit |

## Other gaps

Applied Oritech's author page was inspected but no Source link was exposed in the returned page.
NexusAE was verified through indexed author pages, but direct page fetches repeatedly failed.
Neither is counted among the 33 source inventory checks.
This is a broader sample, not a declaration that the entire CurseForge AE2 ecosystem has been cleared.

