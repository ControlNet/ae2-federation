---
navigation:
  parent: examples/index.md
  title: 下界前哨
  icon: ae2federation:federation_p2p_tunnel
  position: 15
---

# 下界前哨

**目标**： 下界里的前哨网络使用你在主世界基地的存储， 并靠基地的电源运行。 量子网络桥在两个维度之间承载一对联邦P2P通道， 前哨仍是独立的网络， 保留自己的频道。

**需要**： 一个带存储和电源的基地网络； 一个带终端的前哨网络； 每个网络一个<ItemLink id="ae2federation:switch" />； 一座量子网络桥， 即每端八个<ItemLink id="ae2:quantum_ring" />和一个<ItemLink id="ae2:quantum_link" />， 外加一对<ItemLink id="ae2:quantum_entangled_singularity" />； 两个<ItemLink id="ae2:me_p2p_tunnel" />； 一张<ItemLink id="ae2:memory_card" />； 一个<ItemLink id="ae2:quartz_fiber" />； ME线缆和<ItemLink id="ae2federation:cable" />。

主世界这一端：

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/nether_outpost_overworld.snbt" />
  <BoxAnnotation color="#915dcd" min="5 1 0" max="7 2 1">
    基地： 驱动器， 以及给这里所有网络供电的能源元件
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="5 2 0" max="6 3 1">
    基地的交换机， 放在驱动器上面
  </BoxAnnotation>
  <BoxAnnotation color="#5ccd78" min="0 0 0" max="4 3 1">
    承载网络： 量子网络桥和通道所在的线缆， 自成一个网络， 没有能源元件
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4.125 1.75 0.125" max="4.875 2 0.875">
    通道输入端： 装在承载网络的线缆上， 正面上方是通往基地交换机的联邦线缆
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4.75 1.375 0.375" max="5 1.625 0.625">
    石英纤维： 把基地的电力传给承载网络， 但不把两个网络连成一个
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

下界这一端：

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/nether_outpost_nether.snbt" />
  <BoxAnnotation color="#5ccd78" min="2 0 0" max="6 3 1">
    承载网络的另一半： 量子网络桥连通后， 它和主世界那一半是同一个网络， 也用那边的电力
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 1.125 0.125" max="2.25 1.875 0.875">
    通道输出端： 正面接着通往前哨交换机的联邦线缆
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="0 1 0" max="1 2 1">
    前哨的交换机
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="1 1 1">
    前哨： 一个终端， 没有自己的电源
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

在联邦界面里， 基地和前哨同属一个域， 就像两个交换机之间连着联邦线缆。 承载网络不在这个域里。 前哨使用基地的存储， 并与基地共享能量：

<FederationTopology>
  <Network key="base" label="基地" color="#915dcd" column="0" row="0" details="驱动器|能源元件" />
  <Network key="outpost" label="前哨" color="#5CA7CD" column="1" row="0" details="终端" />
  <Rule user="outpost" source="base" capability="storage" />
  <Energy first="base" second="outpost" />
</FederationTopology>

## 搭建

1. **搭建量子网络桥**： 两个维度各放一半， 每个链接仓里放这对奇点中的一个， 参见AE2的[量子网络桥](ae2:items-blocks-machines/quantum_bridge.md)。 桥的线缆自成一个网络， 即承载网络。 不要让它碰到基地或前哨： 否则量子网络桥会把一个已经存在的另一半并入那个网络， 联邦会暂停这个网络的规则（“合并待定”）。
2. **用基地给承载网络供电**： 在承载网络的线缆和基地之间放一个石英纤维。 下界那一半不需要能源元件： 量子网络桥连通后， 基地的电力就能到达那里。
3. **放置通道**。 在两端承载网络的线缆上各放一个ME P2P通道， 手持联邦线缆分别右键它们， 把它们变成[联邦P2P通道](../items/federation_p2p_tunnel.md)。 然后用内存卡配对： 潜行右键主世界的通道， 再右键下界的通道。
4. **把每个网络接到它的交换机上， 再用联邦线缆把每个交换机接到它那一端通道的正面**。
5. **打开联邦界面**： 右键任意一个交换机， 选中基地和前哨之间的连线。 **在“前哨使用基地的”下打开存储规则**， 同时打开这对网络的**ME能量**： 前哨从此跨越维度靠基地的电源运行。
6. **检查**。 打开前哨的终端： 基地的物品都列在里面。 这条规则和[共享仓库](shared-warehouse.md)里的相同。

## 让两端保持加载

和AE2自己的量子网络桥一样， 这条连接需要两端都处于加载状态， 比如用一个<ItemLink id="ae2:spatial_anchor" />。 如果前哨所在的区块被卸载， 前哨会立刻离开这个域， 失去基地的存储。 区块重新加载后， 它会自动回来。

## 试一试

从主世界的链接仓里取出奇点。 前哨的终端变暗、 变空： 通道断开了， 基地的存储和电力也随之断开。 基地自己照常工作。 把奇点放回去， 前哨又拥有了这两样。
