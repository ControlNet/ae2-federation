---
navigation:
  parent: index.md
  title: ME联邦处理端点
  icon: ae2federation:processing_endpoint
  position: 150
categories:
- devices
item_ids:
- ae2federation:processing_endpoint
---

# ME联邦处理端点

<BlockImage id="ae2federation:processing_endpoint" p:facing="south" scale="6" />

让另一个网络上的<ItemLink id="ae2federation:pattern_provider" />使用你的机器。 参见[远程合成](../remote-processing.md)。

* **前面**： 联邦面。 放置时朝向你点击的方块， 所以请点击<ItemLink id="ae2federation:cable" />或路由器。 用扳手可以转动它。
* **其他五个面**： 接入机器自己的小ME网络（加工子网络）。 供应器送来的原料进入这个网络的存储； 机器必须把产物推回这几个面之一。

端点同一时间只属于一个供应器。 如果有一个普通AE2样板供应器方块贴在它的前面， 它会改为为那个供应器工作在本地模式。

被供应器使用时， 端点会像石英纤维一样把该供应器网络的ME能量与子网络共享， 子网络因此不需要自己的能源。 端点面板里的“ME 能量”开关可以关闭共享。

右键它打开其前面所在联邦域的联邦界面。

<RecipeFor id="ae2federation:processing_endpoint" />

配方里的ME接口必须是方块形态。
