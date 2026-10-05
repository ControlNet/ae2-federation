---
navigation:
  parent: examples/index.md
  title: 把供应器装进一个方块
  icon: data_energistics:adaptive_pattern_provider
  position: 74
---

# 把供应器装进一个方块

**目标**： 把[向装配工坊下单](remote-assembly.md)里工坊的样板供应器换成Data Energistics的自适应样板供应器， 每台都能在供应器槽位里装下好几台样板供应器， 同时主网络照常向它下单。 这一页出现， 是因为安装了Data Energistics。

**需要**： 带<ItemLink id="ae2:pattern_provider" />、旁边是<ItemLink id="ae2:molecular_assembler" />的工坊网络（网络B）； 每台供应器一个<ItemLink id="data_energistics:adaptive_pattern_provider_upgrade" />； 带合成CPU、合成终端、存储和电源的主网络（网络A）； 每个网络一个<ItemLink id="ae2federation:router" />， 它们之间用<ItemLink id="ae2federation:cable" />相连。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/adaptive_providers.snbt" />
  <BoxAnnotation color="#915dcd" min="7 0 0" max="9 2 1">
    网络A： 合成终端、合成CPU、存储， 以及给两个网络供电的能源元件
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0 0" max="7 1 1">
    每个网络一个路由器， 用联邦线缆相连
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="3 3 1">
    网络B： 各装着一台AE2样板供应器的自适应样板供应器， 旁边是分子装配室
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

联邦界面里会显示这两个网络和它们之间的规则：

<FederationTopology>
  <Network key="a" label="网络A" color="#915dcd" column="0" row="0" details="合成CPU、终端|存储、能源元件" />
  <Network key="b" label="网络B" color="#5CA7CD" column="1" row="0" details="自适应样板供应器|分子装配室" />
  <Rule user="a" source="b" capability="crafting" />
  <Rule user="a" source="b" capability="storage" />
  <Energy first="a" second="b" />
</FederationTopology>

## 搭建

1. **从装配工坊的例子开始**： 两个网络已经相连， “A使用B的”合成规则和这对网络的ME能量已经打开。
2. **潜行时对B的每台样板供应器使用自适应样板供应器升级件**。 每台都会变成自适应样板供应器， 并保留原来的样板， 但暂时不提供其中任何一个。
3. **在每台的供应器槽位里放入一台AE2样板供应器**。 每装入一台供应器就增加它的样板槽， 保留的样板也会重新提供出来。 见[Data Energistics自己的指南](data_energistics:items-blocks-machines/6.17_adaptive_pattern_provider.md)。
4. **像之前一样从网络A下单**。

自适应样板供应器像AE2样板供应器一样把槽里的样板提供给网络B， 所以联邦也用同样的方式共享它们。

## 试一试

从网络A下单， 然后升级B的一台供应器。 它的配方从A的终端里消失， 因为新方块在装入供应器之前不提供任何样板。 在它的供应器槽位里放入一台AE2样板供应器： 配方回来了， 下一个订单经过这台自适应样板供应器完成。
