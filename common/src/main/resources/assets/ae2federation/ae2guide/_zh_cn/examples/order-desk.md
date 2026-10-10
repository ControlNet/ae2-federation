---
navigation:
  parent: examples/index.md
  title: 接单的工厂
  icon: ae2:molecular_assembler
  position: 27
---

# 接单的工厂

**目标**： 让另一位玩家的网络向你的工厂下单， 却看不到工厂的库存。 合成规则只共享工厂的配方： 顾客用自己的物品付账， 工厂的驱动器保持私有。

**需要**： 顾客的网络， 带合成终端、自己的合成CPU和一个驱动器， 驱动器里放着它用来付账的原料； 你的工厂， 带一个<ItemLink id="ae2:pattern_provider" />， 旁边是<ItemLink id="ae2:molecular_assembler" />， 还有一个放私人库存的驱动器和一个能源元件； 两者挨着的地方放一个<ItemLink id="ae2federation:bridge" />。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/order_desk.snbt" />
  <BoxAnnotation color="#915dcd" min="3.375 0 0" max="6 2 1">
    顾客： 合成终端、合成CPU， 以及放着付账原料的驱动器
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0.25 0.25" max="3.375 0.75 0.75">
    桥接器： 装在顾客的线缆上， 外侧接触工厂的线缆
  </BoxAnnotation>
  <BoxAnnotation color="#5ccd78" min="0 0 0" max="3 2 1">
    工厂： 样板供应器， 旁边是分子装配室； 放私人库存的驱动器， 以及给两个网络供电的能源元件
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

联邦界面里会显示这两个网络和它们之间唯一的一条规则：

<FederationTopology>
  <Network key="customer" label="顾客" color="#915dcd" column="0" row="0" details="合成CPU、终端|驱动器" />
  <Network key="factory" label="工厂" color="#5ccd78" column="1" row="0" details="样板供应器|私人驱动器、能源元件" />
  <Rule user="customer" source="factory" capability="crafting" />
  <Energy first="customer" second="factory" />
</FederationTopology>

## 搭建

1. **放置桥接器**： 装在顾客的线缆上， 让它的外侧接触工厂的线缆。
2. **打开这对网络的ME能量**， 让顾客靠工厂的能源元件运行。
3. **打开“顾客使用工厂的”合成规则**， 存储规则保持关闭。
4. **在顾客的终端下单**。 工厂的配方列在顾客的可合成物品里。

## 订单怎样运行

顾客的合成CPU执行任务， 也由顾客付账： CPU从顾客自己的驱动器里取原料， 直接推送给工厂的样板供应器， 产物直接回到顾客这里。 顾客的终端里看不到工厂的任何库存， 它的CPU也用不到这些库存。

副产物、被取消的任务的产物等剩余物品会落在工厂里。 它们仍归你， 顾客够不到。

## 试一试

打开“顾客使用工厂的”存储规则。 工厂的库存出现在顾客的终端里。 再把它关闭， 库存又消失了， 而工厂的配方仍然列着， 顾客照样可以下单。
