---
navigation:
  parent: items/index.md
  title: ME联邦自动化接口
  icon: ae2federation:automation_interface
  position: 460
categories:
- devices
item_ids:
- ae2federation:automation_interface
---

# ME联邦自动化接口

<BlockImage id="ae2federation:automation_interface" p:facing="south" scale="6" />

联邦程序接触世界的地方： 它前面的方块， 以及它发出的红石信号。

* **前面**： 放置时朝向你点击的方块， 所以请点击它要操作的箱子、机器或作物。 用扳手可以转动它。
* **其他五个面**： 只接<ItemLink id="ae2federation:cable" />。 接口通过这几个面加入联邦域， 并像处理端点一样在线缆的[容量](../mechanics/capacity.md)里计1。 它每tick消耗2 FE（[能量](../mechanics/power.md)）。

每个接口都有一个ID， 例如`interface-k3m9qa`， 在被拆掉之前一直不变。 右键它可以查看、复制ID， 或者给接口起一个别名。 别名在它的联邦域里不能重复， 也不能写成ID的样子。

<RecipeFor id="ae2federation:automation_interface" />

配方用的是方块形态的ME接口， 不是面板形态的ME接口。
