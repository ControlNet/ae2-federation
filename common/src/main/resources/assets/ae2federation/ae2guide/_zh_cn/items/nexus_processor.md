---
navigation:
  parent: index.md
  title: 联结处理器
  icon: ae2federation:nexus_processor
  position: 100
categories:
- misc ingredients blocks
item_ids:
- ae2federation:nexus_processor
- ae2federation:nexus_core
---

# 联结处理器

<ItemImage id="ae2federation:nexus_processor" scale="4" />

所有联邦设备都要用到的材料。 分两步： 先合成一批联结核心， 再在<ItemLink id="ae2:inscriber" />中把每个核心压成处理器。

## 联结核心

在工作台里， 上排放三份红石粉， 下排放三份<ItemLink id="ae2:ender_dust" />， 得到十六个<ItemLink id="ae2federation:nexus_core" />。 末影粉可以在压印器里用末影珍珠磨出来， 其他模组的末影珍珠粉也一样能用。

<RecipeFor id="ae2federation:nexus_core" />

## 压制处理器

在压印器中： 联结核心放顶槽， 末影粉放中槽， <ItemLink id="ae2:printed_silicon" />放底槽。 三种原料都会被消耗。 核心和硅板上下对调也可以压制。

<RecipeFor id="ae2federation:nexus_processor" />

它可以像AE2自己的处理器一样自动化： 从一侧给压印器送料， 核心、末影粉和硅板会自动进入各自的槽位。

用于： <ItemLink id="ae2federation:bridge" />、<ItemLink id="ae2federation:cable" />、<ItemLink id="ae2federation:pattern_provider" />和<ItemLink id="ae2federation:processing_endpoint" />。
