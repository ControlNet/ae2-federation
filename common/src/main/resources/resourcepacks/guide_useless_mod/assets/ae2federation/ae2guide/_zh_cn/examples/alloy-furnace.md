---
navigation:
  parent: examples/index.md
  title: 向万象合金炉下单
  icon: useless_mod:advanced_alloy_furnace_block
  position: 71
---

# 向万象合金炉下单

**目标**： UselessMod的万象合金炉把合成样板放在自己的样板槽里， 并亲自完成合成， 不需要分子装配室。 让它单独组成一个网络， 再从主网络订购它的配方。 这一页出现， 是因为安装了UselessMod。

**需要**： 合金炉所在的网络（网络B）： 一台装在ME线缆上的<ItemLink id="useless_mod:advanced_alloy_furnace_block" />， 没有自己的存储和电源； 带合成CPU、合成终端、存储和电源的主网络（网络A）； 一个装在A的线缆上、外侧接触B的线缆的<ItemLink id="ae2federation:bridge" />。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/alloy_furnace.snbt" />
  <BoxAnnotation color="#915dcd" min="2.375 0 0" max="5 2 1">
    网络A： 合成终端、合成CPU、存储， 以及给两个网络供电的能源元件
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 0.25 0.25" max="2.375 0.75 0.75">
    桥接器： 装在网络A的线缆上， 外侧接触网络B的线缆
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="2 1 1">
    网络B： 只有装着合成样板的万象合金炉
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

联邦界面里会显示这两个网络和它们之间的规则：

<FederationTopology>
  <Network key="a" label="网络A" color="#915dcd" column="0" row="0" details="合成CPU、终端|存储、能源元件" />
  <Network key="b" label="网络B" color="#5CA7CD" column="1" row="0" details="万象合金炉" />
  <Rule user="a" source="b" capability="crafting" />
  <Rule user="a" source="b" capability="storage" />
  <Energy first="a" second="b" />
</FederationTopology>

## 搭建

1. **把合金炉接到网络B的线缆上**， 再把合成样板放进它的样板槽。 它和其他AE2设备一样需要一个频道。
2. **连接两个网络**： 把桥接器装在A的线缆上， 让它的外侧接触B的线缆。 两个网络相距较远时， 改为每个网络放一个路由器， 中间用联邦线缆相连。
3. **在联邦界面里打开“A使用B的”合成规则**， 再打开这对网络的ME能量， 让网络B靠网络A的电源运行。 合金炉合成这些样板时不需要自己的FE。
4. **从网络A下单**。 合金炉的配方会出现在A的可合成列表里， 由A的合成CPU运行任务， 合金炉负责合成。

网络B没有存储。 合金炉做出的物品直接回到A正在等待的CPU， 最后进入A的存储。

## 试一试

从合金炉里取出一张样板。 它的配方从A的终端里消失。 把样板放回去： 配方回来了， A又可以订购它。
