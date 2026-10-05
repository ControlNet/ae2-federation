---
navigation:
  parent: examples/index.md
  title: 合作工坊
  icon: ae2:pattern_provider
  position: 22
---

# 合作工坊

**目标**： 两个工坊各自保留自己的配方， 又各自向对方订购对方做的东西。 这里工坊A做木棍， 工坊B做木板： A向B订木板， B向A订木棍， 各用自己的合成CPU。

**需要**： 两个网络， 每个都有合成CPU、合成终端、存储， 以及挨着<ItemLink id="ae2:molecular_assembler" />的<ItemLink id="ae2:pattern_provider" />； 其中一个网络有电源； 两个网络挨着的地方放一个<ItemLink id="ae2federation:bridge" />。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/partner_workshops.snbt" />
  <BoxAnnotation color="#915dcd" min="4.375 0 0" max="9 2 1">
    工坊A： 合成终端、合成CPU、存储、木棍样板， 以及给两个工坊供电的能源元件
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4 0.25 0.25" max="4.375 0.75 0.75">
    桥接器： 装在A的线缆上， 外侧接触B的线缆
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="4 2 1">
    工坊B： 合成终端、合成CPU、存储和木板样板
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

联邦界面里会显示这两个工坊和双向的规则：

<FederationTopology>
  <Network key="a" label="工坊A" color="#915dcd" column="0" row="0" details="木棍样板|能源元件" />
  <Network key="b" label="工坊B" color="#5CA7CD" column="1" row="0" details="木板样板" />
  <Rule user="a" source="b" capability="crafting" />
  <Rule user="a" source="b" capability="storage" />
  <Rule user="b" source="a" capability="crafting" />
  <Rule user="b" source="a" capability="storage" />
  <Energy first="a" second="b" />
</FederationTopology>

## 搭建

1. **放置桥接器**： 装在一个工坊的线缆上， 外侧接触另一个工坊的线缆。
2. **打开“A使用B的”和“B使用A的”合成规则**。 每条都会打开同方向的存储规则。
3. **打开这对网络的ME能量**， 让B靠A的能源元件运行。
4. **在A下单木板**， 再**在B下单木棍**。 每个工坊都能在自己的可合成物品里看到对方的配方。

## 任务怎样运行

每个订单都由下单一方自己的合成CPU执行： 它把原料发给另一个工坊的样板供应器， 再收回产物。 另一个工坊的CPU不参与。 两个方向是两条独立的规则： 一个工坊可以停止向对方订购， 而对方照样向它订购。

## 试一试

关闭“B使用A的”合成规则。 B不能再订木棍， 但A仍然可以向B订木板。
