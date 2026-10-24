---
navigation:
  parent: mechanics/index.md
  title: 远距离连接
  icon: ae2federation:beam_former
  position: 250
---

# 远距离连接

有三种方式把联邦域从一处延伸到另一处。 每一种都相当于在两端之间铺了联邦线缆， 所以两侧的方块属于同一个联邦域， 共用它的规则和[容量](capacity.md)。

| 连接 | 范围 | 承载 |
|---|---|---|
| <ItemLink id="ae2federation:cable" /> | 任意长度， 逐格铺设 | 一根线缆的上限； [粗线缆](bundled-cables.md)更多 |
| <ItemLink id="ae2federation:federation_p2p_tunnel" /> | ME网络能到达的任何地方， 经由AE2的量子网络桥也能到其他维度 | 一根线缆的上限 |
| 一对<ItemLink id="ae2federation:beam_former" /> | 一条笔直无遮挡的直线， 最长256格； 服务器最多可设为1024 | 按截面计算， 和粗线缆一样 |

## 如何选择

* **联邦线缆**最便宜， 也最简单。 能走到的地方就能铺。
* **P2P通道**借用一个已经跨越这段距离的ME网络。 这个网络照常支付AE2的能量， 每条通道占用一个频道， 它只负责承载连接， 不会加入联邦域。 参见[下界前哨](../examples/nether-outpost.md)。
* **光束成型器**不用在中间铺任何东西， 就能跨过空地、峡谷和水面。 光束能穿过玻璃和水， 遇到实心方块会被挡住。

光束成型器的连接只要两端都已加载就能保持， 中间是什么都没有关系。 线缆， 以及P2P通道所依托的ME网络， 必须整段都已加载。
