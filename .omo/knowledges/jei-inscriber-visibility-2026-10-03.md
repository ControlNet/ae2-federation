# JEI missing all Inscriber recipes

The user reported that all Inscriber recipes, including native AE2 recipes, are absent from JEI.
The project's AE2 version is 19.2.17 (Minecraft 1.21.1). Its source checkout at commit
`db95d25ccc79f7bd55b504cf71522b57d60bf4f7` has EMI and REI integrations, but no JEI plugin.
Its build declares EMI/REI compile-only dependencies and defaults its own development recipe viewer to EMI.

The author-maintained AE2 JEI Integration project explicitly restores the JEI compatibility removed in Minecraft 1.21:
https://www.curseforge.com/minecraft/mc-mods/ae2-jei-integration
Source: https://github.com/Tamaized/AE2-JEI-Integration

The Federation recipe at `common/src/main/resources/data/ae2federation/recipe/federation_logic_processor.json`
uses `ae2:inscriber`, a native Logic Processor in the middle, Fluix Dust on top, press mode and the correct result id.
Omitting the bottom ingredient is supported by AE2's recipe codec. No obvious schema issue was found; this is not
an in-game execution test. Missing native recipes as well points first to recipe-viewer integration, not this recipe.

The project build has no explicit JEI/EMI runtime dependency in the inspected files. Inspected development client
logs do not establish the user's actual JEI instance or installed integration version. Missing AE2 JEI Integration
is therefore the leading explanation, not a confirmed inventory of the user's installation. If the integration is
already installed, inspect its version and recipe-registration exceptions in that instance's latest.log.
Do not change the crafting recipe to compensate for a missing display integration.

No gameplay code, build dependencies or installed mods were changed during this investigation.
