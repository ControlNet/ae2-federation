# AE2 Ender Dust

Verified against the locally available AE2 19.2.17 source on 2026-10-06.

- AE2 itself registers `ae2:ender_dust` as Ender Dust; its Simplified Chinese item name is `末影粉`.
- The generated `data/ae2/recipe/inscriber/ender_dust.json` converts one `minecraft:ender_pearl` to one `ae2:ender_dust`, with only a middle ingredient and no press.
- `ConventionTags.ENDER_PEARL_DUST` is `c:dusts/ender_pearl`, and AE2 includes its dust in that tag.
- `CraftingRecipes` uses this tag for the wireless booster recipe; `TransformRecipes` uses it with a singularity for quantum entanglement.
- Sources: `AEItems.java`, `ConventionTags.java`, `CraftingRecipes.java`, `TransformRecipes.java`, the generated inscriber recipe, and `assets/ae2/lang/zh_cn.json` in the AE2 source tree.
