---
navigation:
  parent: examples/index.md
  title: Neo ECO仓库
  icon: neoecoae:storage_system_l4
  position: 80
---

# Neo ECO仓库

**目标**： 把Neo ECO存储子系统当作[共享仓库](shared-warehouse.md)里的仓库： 它留在自己的网络上， 其他网络上的工坊从里面取用、往里面存放。 这一页出现， 是因为安装了Neo ECO AE Extension。

**需要**： 带电源和一个已成形ECO存储子系统的仓库网络， 子系统包括一个<ItemLink id="neoecoae:storage_system_l4" />、装着<ItemLink id="neoecoae:eco_item_storage_cell_16m" />的<ItemLink id="neoecoae:eco_drive" />， 以及一个<ItemLink id="neoecoae:storage_interface" />； 带终端和电源的工坊网络； 一个<ItemLink id="ae2federation:router" />。

<GameScene zoom="3" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/eco_storage_system.snbt" />
  <BoxAnnotation color="#5CA7CD" min="3 0 0" max="9 3 2">
    仓库： 最小的ECO存储子系统， 背面的存储子系统通讯接口接在仓库网络的线缆上， 以及这个网络自己的电源
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 1 1" max="3 2 2">
    路由器： 每个网络占一个面
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="0 1 1" max="2 2 2">
    工坊： 一个终端和它自己的电源
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 搭建

1. **搭好存储子系统**， 方法见[Neo ECO自己的指南](neoecoae:neoecoae_intro/storage_system.md)， 在驱动器里放入ECO存储矩阵， 再把它的通讯接口接到仓库网络的线缆上。 最小的子系统长5格、高3格、深2格。
2. **把仓库网络和工坊分别接到路由器的不同面上**。
3. **在联邦界面里， 在“工坊使用仓库的”下打开存储规则**。
4. **检查一下**。 工坊的终端会列出ECO存储矩阵里的东西。 取出一些， 再放回一些： 它们会进入ECO存储矩阵。

成形的存储子系统是仓库网络ME存储的一部分， 所以规则会像共享驱动器一样共享它。

## 试一试

拆掉存储子系统的一块外壳。 结构散开， 它的物品从工坊的终端里消失。 把外壳放回去： 子系统重新成形， 物品也回来了。
