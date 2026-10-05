---
navigation:
  parent: index.md
  title: ME联邦样板供应器
  icon: ae2federation:pattern_provider
  position: 140
categories:
- devices
item_ids:
- ae2federation:pattern_provider
---

# ME联邦样板供应器

<BlockImage id="ae2federation:pattern_provider" p:facing="south" scale="6" />

通过<ItemLink id="ae2federation:processing_endpoint" />把处理样板发给其他网络上的机器。 参见[远程合成](../remote-processing.md)。

* **前面**： 联邦面。 放置时前面朝向你点击的方块， 所以请点击<ItemLink id="ae2federation:cable" />或路由器。 用扳手可以转动它； 潜行时用扳手可以拆下。
* **其他五个面**： 接入供应器自己的ME网络， 在那里占用一个频道。

右键它可以放入最多九个编码样板， 并把每个样板映射到端点。 它也和AE2的<ItemLink id="ae2:pattern_provider" />一样， 有阻挡模式、合成锁定、优先级以及在样板访问终端中是否显示的设置。 如果某个附属给AE2的样板供应器加了升级槽， 它在界面的**升级**一栏里也有同样的槽， 接受同样的升级卡。 例如Applied Flux的感应卡会把网络里的FE送进贴着供应器的机器， 也会经过联邦面送进贴着它所认领端点的机器， 前提是它此时能向这些端点发送样板。 破坏它会掉落其中的样板， 以及所有待发送或待返回的物品。

<RecipeFor id="ae2federation:pattern_provider" />

配方里的原版样板供应器必须是方块形态， 原来的样板不会带过来。
