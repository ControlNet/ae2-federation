# Automatic and wildcard AE2 patterns (2026-10-23)

Scope: discovery for Minecraft 1.21.x, with confirmed candidates targeting NeoForge 1.21.1. This is a documentation and release-metadata survey, not a runtime qualification.

## Closest matches

- **AE All Pattern**: machine recipe catalogs become an Aggregate Pattern through a Generator. The current repository also describes Linker/Binder virtual processing patterns and dynamic routing. The repository README identifies 0.2.7, Minecraft 1.21.1, NeoForge 21.1.233+, and AE2 19.2.17. The inspected CurseForge listing exposes 0.2.6; do not assume all current README features exist in that older artifact. Sources: https://github.com/LangQi99/ae-all-pattern and https://www.curseforge.com/minecraft/mc-mods/ae-all-pattern .
- **Smart Pattern System**: wildcard expansion, markers, recipe search, filters, and mod exclusions. The author repository explicitly lists a NeoForge 1.21.1 migration track at 1.0.8. Modrinth's version API confirms release ID `9WQGh0d9`, version 1.0.8, loader `neoforge`, game version `1.21.1`, filename `sampleintegration-1.0.8+1.21.1.jar`. The project description uses older ore-dictionary examples such as `ingot*` to `plate*`; exact modern tag syntax needs version-specific inspection. Sources: https://github.com/wu-xiao-ya/AE2-Smart-Pattern-System , https://modrinth.com/mod/ae2uel-smart-pattern-system , https://api.modrinth.com/v2/project/ae2uel-smart-pattern-system/version .

## Other generation candidates

- **Easy Patterns**: author description says automatic pattern creation along a crafting chain for AE2 and Refined Storage. CurseForge lists `easy-patterns-1.0.7.jar`, NeoForge 1.21.1. The description is too short to establish supported processing-machine recipes or chain-selection behavior. Source: https://www.curseforge.com/minecraft/mc-mods/easypatterns .
- **AE2 AI Pattern Forge**: author describes a powered block converting selected blocks and blank patterns into encoded crafting patterns. CurseForge lists `aipatternforge-1.3.3.jar`, NeoForge 1.21.1. No verified claim of wildcard expansion or recursive whole-chain generation. Source: https://www.curseforge.com/minecraft/mc-mods/ae2-ai-pattern-forge .

## Existing local compatibility evidence

`tests/compat/mods.json` already pins AE All Pattern 0.2.6. The Smart Doubling section in `compatibility-targets-2026-10-04.md` records its cancellable HEAD injection preventing ExtendedAE-Plus' TAIL hook from marking patterns for scaling, including on native providers. Since 2026-10-05, `addons-all` excludes AE All Pattern. This is historical evidence for the pinned version, not proof that newer releases retain the conflict.

## Follow-up: encoding and uploading patterns

- **AE2 Auto Pattern Upload**, GaLicn: dedicated upload button beside encoding in the terminal; sends encoded patterns to suitable providers or supported pattern storage on the same network. Extracted from ExtendedAE-Plus. CurseForge confirms 2.0.0 for NeoForge 1.21.1, although the repository README still describes 1.12.2. Source: https://www.curseforge.com/minecraft/mc-mods/ae2-auto-pattern-upload .
- **Pattern Uploader**, linkfgfgui: distinct project, with `pattern_uploader-0.11.0.jar` for NeoForge 1.21.1. Alt-clicking Encode uploads compatible processing patterns from the player's inventory. Author describes provider matching by existing recipe category first, then connected machine; EMI recipe transfer records recipe IDs for routing and tooltips. Source: https://www.curseforge.com/minecraft/mc-mods/pattern-uploader .
- **ExtendedAE-Plus**: terminal-to-provider upload alongside pattern scaling and other utility features. CurseForge lists 1.6.3 for NeoForge 1.21.1. Sources: https://github.com/GaLicn/ExtendedAE_Plus and https://www.curseforge.com/minecraft/mc-mods/extendedae-plus .
- **AE2 Utility**: author README identifies 1.7.4 for Minecraft 1.21.1 NeoForge; JEI/EMI single and batch encoding, current-page or machine-category batches, duplicate checks, recipe filtering. Upload to matching providers integrates with ExtendedAE-Plus; also describes assembly matrix and Neo ECO crafting subsystem targets. Do not describe provider upload as wholly independent of EAEP. Source: https://github.com/lhy512103/AE2-Utility .

These upload capabilities are author documentation claims, not newly tested Federation compatibility. In particular, same-network upload does not establish cross-Federation provider discovery or delivery.

## Release-history context

Follow-up inspection of https://www.curseforge.com/minecraft/mc-mods/ae2-utility/files/all shows 18 published files, from 1.2.6 on 2026-04-23 through 1.8.0 on 2026-09-25; the previously inspected README's 1.7.4 is not the newest published version. Pattern Uploader 0.11.0 is dated 2026-04-17, AE All Pattern 0.2.6 is dated 2026-09-17, and Easy Patterns 1.0.7 is dated 2026-10-19 on their publisher pages. These dates establish recent releases, not necessarily project inception. The search intentionally targets narrow pattern-workflow utilities and cannot establish ecosystem-wide growth rates or a causal role for AI coding. Standalone extraction (Auto Pattern Upload from EAEP) also means project counts are not independent feature counts.
