# Chinese mod name and the places it shows (2026-10-08)

The mod's Chinese name is 「AE2 联邦」, with a space; the owner chose it after comparing AE2 addons, whose usage is
split (ExtendedAE 「AE2扩展」, AE2 Lightning Tech 「AE2 闪电科技」, AE2WTLib 「AE2 无线终端」). AE2's own Chinese never
puts a space between Latin letters and Chinese; the spaced name is a deliberate choice for the mod name only, and item
names such as 「ME联邦线缆」 are unchanged. On 2026-10-08 these still said "AE2 Federation" in Chinese:

| Where | Key / file | Now |
|---|---|---|
| Creative tab | `itemGroup.ae2federation.main` | AE2 联邦 |
| Mod name key | `mod.ae2federation.name` | AE2 联邦 |
| Guide sidebar and index heading | `ae2guide/_zh_cn/index.md` | `title: AE2 联邦`, `# AE2 联邦（AE2 Federation）` |
| Guide example pack names | `ae2federation.pack.<id>` | AE2 联邦指南：… |
| Guide example pack descriptions | `pack.mcmeta` was English only | `{"translate": "ae2federation.pack.<id>.description", "fallback": …}` with a key in both languages |
| Mod list description | new `fml.menu.mods.info.description.ae2federation` | translated in both languages |

## How NeoForge and Minecraft translate these

- NeoForge 21.1's mod list translates a mod's description through `fml.menu.mods.info.description.<modid>`. The
  prefix was found in `ModListScreen`'s strings. There is no key for the display name, so it stays "AE2 Federation"
  from `neoforge.mods.toml`.
- The pack screen shows a pack's translated title (the `AddPackFindersEvent` component) and its `pack.mcmeta`
  description. The description can be a component. Vanilla's own `data/minecraft/datapacks/bundle/pack.mcmeta`
  uses `{"translate": ...}`.
- Two `MEStorage.getDescription()` literals still say "AE2 Federation…": `FederationStorageView` and
  `AuthorizedStorageProjection`. AE2 shows such a description only in a Storage Bus "connected to" line, and no
  Storage Bus faces these internal views, so they were left as is.

## Tests

`LanguageContractTest` checks three things:

- both languages have the same keys;
- the Chinese name is AE2 联邦, and no Chinese value or Chinese guide page contains "AE2 Federation" or the unspaced
  「AE2联邦」;
- the mod-list description key exists.

`GuidePagesContractTest.optionalPacksMatchTheirFolders` checks each pack's translated description key.

Parsing `pack.mcmeta` with `PackMetadataSection.CODEC` does not work in JUnit. It needs `Bootstrap`, and that fails
under NeoForge because there is no `LoadingModList`.
