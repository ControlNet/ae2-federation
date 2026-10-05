---
navigation:
  parent: examples/index.md
  title: Neo ECO生产区
  icon: neoecoae:computation_system_l4
  position: 80
---

# Neo ECO生产区

**目标**： 在两个网络之间同时用上Neo ECO的全部三种多方块结构， 而不合并网络。 你的主网络（网络A）用ECO存储系统存放物品， 用ECO计算系统运行任务； 工厂网络（网络B）把样板放在ECO合成系统里。 A向B的配方下单， B靠A的电源运行。 这一页出现， 是因为安装了Neo ECO AE Extension。

**需要**：

* 网络A上： 一个已成形的ECO存储系统， 包括主机（<ItemLink id="neoecoae:storage_system_l4" />）、 装着ECO存储矩阵（例如<ItemLink id="neoecoae:eco_item_storage_cell_16m" />）的驱动器（<ItemLink id="neoecoae:eco_drive" />）， 以及<ItemLink id="neoecoae:storage_interface" />； 一个已成形的ECO计算系统， 包括主机（<ItemLink id="neoecoae:computation_system_l4" />）、 一个装着闪存晶阵（例如<ItemLink id="neoecoae:eco_computation_cell_l4" />）的驱动器（<ItemLink id="neoecoae:computation_drive" />）， 以及<ItemLink id="neoecoae:computation_interface" />； 一个合成终端； 电源。
* 网络B上： 一个已成形的ECO合成系统， 包括主机（<ItemLink id="neoecoae:crafting_system_l4" />）、 装着样板的<ItemLink id="neoecoae:crafting_pattern_bus" />， 以及<ItemLink id="neoecoae:crafting_interface" />。 它不需要自己的电源。
* 一个<ItemLink id="ae2federation:router" />。

<GameScene zoom="2" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/eco_district.snbt" />
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="5 3 2">
    网络B的工厂： 最小的ECO合成系统（这里看到的是背面）， 通讯接口接在网络B的线缆上
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="5 1 2" max="6 2 3">
    路由器： 每个网络占一个面
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="6 0 0" max="11 3 2">
    网络A的CPU： 最小的ECO计算系统（这里看到的是背面）， 通讯接口接在网络A的线缆上
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="11 0 0" max="16 3 2">
    网络A的仓库： 最小的ECO存储系统（这里看到的是背面）， 通讯接口接在网络A的线缆上
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="6 1 2" max="17 2 3">
    网络A的线缆接着两个通讯接口， 还有一个合成终端和给两个网络供电的能源元件
  </BoxAnnotation>
  <IsometricCamera yaw="15" pitch="30" />
</GameScene>

联邦界面里会显示这两个网络和它们之间的规则：

<FederationTopology>
  <Network key="a" label="网络A" color="#915dcd" column="1" row="0" details="ECO计算系统|ECO存储系统" />
  <Network key="b" label="网络B" color="#5CA7CD" column="0" row="0" details="ECO合成系统" />
  <Rule user="a" source="b" capability="crafting" />
  <Rule user="a" source="b" capability="storage" />
  <Energy first="a" second="b" />
</FederationTopology>

## 搭建

1. **搭好三个系统**， 方法见Neo ECO自己的指南： [存储系统](neoecoae:neoecoae_intro/storage_system.md)、 [合成系统](neoecoae:neoecoae_intro/crafting_system.md)和[计算系统](neoecoae:neoecoae_intro/computation_system.md)。 每种最小的都长5格、高3格、深2格。 把存储系统和计算系统的通讯接口接到网络A的线缆上， 把合成系统的通讯接口接到网络B的线缆上。
2. **装满它们**。 在存储系统的驱动器里放入ECO存储矩阵， 并存好订单要用的原料； 在合成系统的样板总线里放入样板。 在计算系统的一个驱动器里放入闪存晶阵： 没有它， 计算系统就没有空间运行任务。
3. **把网络A和网络B分别接到路由器的不同面上**。
4. **在联邦界面里， 在“A使用B的”下打开合成规则**， 同一方向的存储规则也会一起打开； 再打开这对网络的**ME能量**： 合成系统从此靠网络A的电源运行。
5. **从网络A下单**。 B的配方会出现在A可合成的物品里。 计算系统规划任务， 从存储系统里取原料， 合成系统在网络B上合成， 产物回到A的计算系统， 再由它存进存储系统。

每个系统各司其职。 存储系统和计算系统是A自己的存储和CPU， 所以A只需要B的样板。 合成系统是B的一个样板供应器， 合成规则把它的样板提供给A。 网络B不需要自己的CPU， 也不需要自己的存储。

## 试一试

拆掉合成系统的一块外壳。 结构散开， 它的配方从A的终端里消失。 把外壳放回去： 系统重新成形， 配方也回来了。
