---
navigation:
  parent: index.md
  title: 联结核心
  icon: ae2federation:nexus_core
  position: 100
categories:
- misc ingredients blocks
item_ids:
- ae2federation:nexus_core
- ae2federation:nexus_processor
- ae2federation:printed_nexus_circuit
---

# 联结核心

<ItemImage id="ae2federation:nexus_core" scale="4" />

所有联邦设备都要用到的材料。 做法和AE2的处理器与核心一样： 先压印电路板， 再压制成处理器， 最后用处理器合成一批核心。

## 联结电路板

在<ItemLink id="ae2:inscriber" />中： <ItemLink id="ae2:logic_processor_press" />放顶槽， 末影珍珠放中槽。 末影珍珠会变成<ItemLink id="ae2federation:printed_nexus_circuit" />， 压印模板会保留。

<RecipeFor id="ae2federation:printed_nexus_circuit" />

## 联结处理器

在压印器中： 联结电路板放顶槽， 红石粉放中槽， <ItemLink id="ae2:printed_silicon" />放底槽， 和AE2自己的处理器一样。 三种原料都会被消耗。 电路板和硅板上下对调也可以压制。

<RecipeFor id="ae2federation:nexus_processor" />

这两步都可以像AE2自己的处理器一样自动化： 从一侧给压印器送料， 每种原料会自动进入各自的槽位。

## 联结核心

在工作台的一排里放<ItemLink id="ae2:fluix_crystal" />、<ItemLink id="ae2:ender_dust" />和<ItemLink id="ae2federation:nexus_processor" />， 得到十六个联结核心， 和AE2的成型核心一样。 其他模组的末影珍珠粉也一样能用。

<RecipeFor id="ae2federation:nexus_core" />

用于： <ItemLink id="ae2federation:bridge" />、<ItemLink id="ae2federation:cable" />、<ItemLink id="ae2federation:router" />、<ItemLink id="ae2federation:pattern_provider" />和<ItemLink id="ae2federation:processing_endpoint" />。
