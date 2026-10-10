---
navigation:
  parent: index.md
  title: 联邦P2P通道
  icon: ae2federation:federation_p2p_tunnel
  position: 135
categories:
- network infrastructure
item_ids:
- ae2federation:federation_p2p_tunnel
---

# 联邦P2P通道

<GameScene zoom="8" background="transparent">
  <ImportStructure src="../assets/p2p_tunnel_part.snbt" />
</GameScene>

联邦P2P通道借助ME网络传送<ItemLink id="ae2federation:cable" />。 同一频率的通道就像正面之间连着联邦线缆， 它们正面的交换机、 路由器、 样板供应器和处理端点同属一个[联邦域](../mechanics.md)。 它和AE2自己的[P2P通道](ae2:items-blocks-machines/p2p_tunnels.md)一样： 不用拉线就能跨越基地， 也能通过量子网络桥进入另一个维度， 见[下界前哨](../examples/nether-outpost.md)。

## 获得方法

它没有合成配方。 手持联邦线缆右键任意P2P通道（比如<ItemLink id="ae2:me_p2p_tunnel" />）， 它就会变成联邦P2P通道， 并保留原来的频率。

## 连接通道

配对方法和AE2的通道相同： 手持<ItemLink id="ae2:memory_card" />潜行右键输入端， 再用它右键每个输出端。 一个输入端可以带多个输出端。 连接是双向的， 只要输入端正常工作， 输出端之间也互相连通。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/federation_p2p_tunnel.snbt" />
  <BoxAnnotation color="#915dcd" min="10 0 0" max="12 2 1">
    网络A： 它的能源元件也给网络B供电
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="2 1 1">
    网络B： 一个驱动器， 没有自己的电源
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="7.75 0.125 0.125" max="8 0.875 0.875">
    通道输入端： 装在网络A的线缆上， 正面接着通往网络A交换机的联邦线缆
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4 0.125 0.125" max="4.25 0.875 0.875">
    通道输出端： 正面接着通往网络B交换机的联邦线缆
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="5 0.3 0.3" max="7 0.7 0.7">
    网络A的线缆承载这条通道横跨基地； 要经过量子网络桥， 请给通道单独一个网络
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

这里网络A使用网络B的存储， 两个网络通过ME能量共用网络A的能源元件：

<FederationTopology>
  <Network key="a" label="网络A" color="#915dcd" column="0" row="0" details="驱动器|能源元件" />
  <Network key="b" label="网络B" color="#5CA7CD" column="1" row="0" details="驱动器" />
  <Rule user="a" source="b" capability="storage" />
  <Energy first="a" second="b" />
</FederationTopology>

和AE2的通道一样， 每个通道都需要所在网络的电力和一个频道。 这个网络只负责承载连接， 不会因为这些通道加入联邦域。 任何一端断电、 失去频道或所在区块被卸载时， 连接会断开； 恢复后会自动重新连上。
