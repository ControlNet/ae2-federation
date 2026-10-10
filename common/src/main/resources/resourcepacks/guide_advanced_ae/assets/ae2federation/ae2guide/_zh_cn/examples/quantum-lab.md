---
navigation:
  parent: examples/index.md
  title: 量子实验室
  icon: advanced_ae:quantum_core
  position: 76
---

# 量子实验室

**目标**： 一个实验室网络唯一的CPU是Advanced AE的量子计算机， 它同时向两个工坊网络下单： 锯木厂把原木做成木板， 细木坊把木板做成木棍。 一份订单要依次用到两个工坊， 第二份订单同时在同一台量子计算机上运行。 这一页出现， 是因为安装了Advanced AE。

**需要**： 一个实验室网络， 带一台以<ItemLink id="advanced_ae:quantum_core" />为核心、已成型的量子计算机， 以及合成终端、存储和能源元件； 两个工坊网络， 每个都有一个样板供应器和一台分子装配室； 三个网络共同接触的一个<ItemLink id="ae2federation:switch" />。 量子计算机怎么搭见[Advanced AE的指南](advanced_ae:aae_intro/quantum_computer.md)。

<GameScene zoom="3" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/quantum_lab.snbt" />
  <BoxAnnotation color="#915dcd" min="1 0 1" max="8 7 9">
    实验室： 量子计算机、合成终端， 以及给三个网络供电的能源元件
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="5 0 0" max="8 1 1">
    锯木厂： 样板供应器里有从一块原木到四块木板的样板， 旁边是分子装配室
  </BoxAnnotation>
  <BoxAnnotation color="#5ccd78" min="1 0 0" max="4 1 1">
    细木坊： 样板供应器里有从两块木板到四根木棍的样板， 旁边是分子装配室
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4 0 0" max="5 1 1">
    一个交换机： 每个面加入它接触的网络
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

联邦界面里会显示这三个网络和它们之间的规则：

<FederationTopology>
  <Network key="sawmill" label="锯木厂" color="#5CA7CD" column="0" row="0" details="木板样板" />
  <Network key="lab" label="实验室" color="#915dcd" column="1" row="0" details="量子计算机|能源元件" />
  <Network key="joinery" label="细木坊" color="#5ccd78" column="2" row="0" details="木棍样板" />
  <Rule user="lab" source="sawmill" capability="crafting" />
  <Rule user="lab" source="joinery" capability="crafting" />
  <Energy first="sawmill" second="lab" />
  <Energy first="lab" second="joinery" />
</FederationTopology>

## 搭建

1. **把三个网络接到同一个交换机的各个面上**， 如场景所示。 量子计算机通过它任何一个外层方块加入实验室的网络。
2. **给每个工坊放样板**： 锯木厂放从一块原木到四块木板的合成样板， 细木坊放从两块木板到四根木棍的合成样板， 各放在一台分子装配室旁边的样板供应器里。
3. **打开“实验室使用锯木厂的”和“实验室使用细木坊的”合成规则**。 实验室用自己的存储付账， 所以不需要存储规则。
4. **打开实验室和每个工坊之间的ME能量**， 两个工坊都靠实验室的能源元件运行。
5. **先下单木棍， 再下单木板**： 在实验室的合成终端里下单， 实验室的存储里放两块原木。

## 任务怎样运行

为了做木棍， 量子计算机先让锯木厂用一块原木做木板。 木板回到实验室， 再送到细木坊， 由细木坊做成木棍。 木板订单同时在同一台量子计算机上运行： 只要合成存储够用， 它能同时接任意多个任务。 AE2的合成CPU一次只能执行一个任务， 换成它的话第二份订单就得等待。

## 试一试

关闭“实验室使用细木坊的”合成规则。 木棍从实验室的可合成物品里消失， 木板还在， 因为它只来自锯木厂。
