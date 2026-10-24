---
navigation:
  parent: mechanics/index.md
  title: 能量
  icon: ae2federation:beam_converter
  position: 230
---

# 能量

联邦涉及两种能量。 **ME能量**是一条规则， 让网络之间共享AE能量。 **FE**是联邦域本身运行所用的能量， 由[光束转换器](../items/beam_converter.md)提供。 两者互不相通： AE能量不能驱动联邦域， 转换器里的FE也不会进入ME网络。

## ME能量： 共享AE能量

为两个网络打开ME能量规则后， 它们的能量合成一个双向流动的能量池， 和经过<ItemLink id="ae2:quartz_fiber" />一样。 没有自己能源的网络就可以靠对方的能量运行。 能量池会连成一片： A与B共享、B与C共享， 三者就共用一个能量池。 每个网络都需要一个交换机面或一个联邦桥； 只通过联邦样板供应器正面接入联邦域的网络无法加入能量池。 参见[规则](rules.md)。

## FE： 联邦域的运行能量

联邦域里的每个联邦方块每tick都消耗FE， 联邦域从它的光束转换器里取用。

| 方块 | 每个方块每tick的FE |
|---|---|
| <ItemLink id="ae2federation:cable" /> | 0.5 |
| <ItemLink id="ae2federation:switch" />、<ItemLink id="ae2federation:router" /> | 2 |
| <ItemLink id="ae2federation:pattern_provider" />、<ItemLink id="ae2federation:processing_endpoint" /> | 2 |
| 已成形的<ItemLink id="ae2federation:quantum_cube" /> | 44 |
| <ItemLink id="ae2federation:bridge" />、<ItemLink id="ae2federation:federation_p2p_tunnel" />、<ItemLink id="ae2federation:beam_converter" />、<ItemLink id="ae2federation:beam_former" /> | 0 |

* 更大的交换机或路由器按每个方块2计算， [粗线缆](bundled-cables.md)里的每根线缆按0.5计算。
* 一对已连接的光束成型器额外消耗2 ×（截面方块数）× L^1.05 FE， L是两个正面之间的方块数（至少为2）： 相距16格的一对单格成型器每tick消耗37 FE。
* 只由联邦桥组成的联邦域不消耗能量， 不需要转换器也能运行。

联邦界面会显示联邦域的消耗和转换器的充满程度， 它的[统计](statistics.md)标签页记录两者随时间的变化。

## FE耗尽时

转换器耗尽的联邦域会**停止**。 它的各个网络照常独立工作， 但不再共享存储、合成、处理和ME能量。 它的线缆发出细细的红光， 拓扑界面变成暗红色。 联邦域会继续取用输入的FE， 连续超过30 tick能量充足后重新运行， 就像AE2网络断电后重新启动一样。

## FE从哪里来

原版Minecraft和AE2都不产出FE。 因此由交换机、路由器或联邦线缆组成的联邦域需要以下其中一种：

* **其他模组的发电机**， 把FE输入光束转换器。 任何模组的FE线缆或发电机都可以。
* **创造ME联邦光束转换器**， 永远不会耗尽。 它是创造模式方块。
* 服务器选项`requireOperatingPower = false`， 位于`config/ae2federation-server.toml`的`[power]`部分（放在某个世界`serverconfig`文件夹里的同名文件只对那个世界生效）。 关闭后所有联邦域都不需要转换器。 同一部分的`operatingCostMultiplier`按比例调整上表中的所有消耗。

转换器的尺寸必须和联邦域里最大的线缆匹配； 尺寸、缓存和配方见它的页面。
