---
navigation:
  parent: examples/index.md
  title: 化学品储罐区
  icon: mekanism:dynamic_tank
  position: 50
---

# 化学品储罐区

**目标**： 把Mekanism的化学品存放在一个储罐区网络里， 放在动态储罐和化学品元件中， 再通过存储规则从另一个网络的终端里使用它们。 这一页出现， 是因为安装了Mekanism和Applied Mekanistics。

**需要**： 一个储罐区网络， 带能源元件、装着<ItemLink id="appmek:chemical_storage_cell_1k" />的驱动器、一个动态储罐（<ItemLink id="mekanism:dynamic_tank" />和一个<ItemLink id="mekanism:dynamic_valve" />）和一个<ItemLink id="ae2:storage_bus" />； 一个带终端的工坊网络； 一个两者都接触的<ItemLink id="ae2federation:switch" />。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/chemical_tank_farm.snbt" />
  <BoxAnnotation color="#915dcd" min="6 0 0" max="7 1 1">
    工坊： 一个终端， 靠储罐区的电源运行
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="5 0 0" max="6 1 1">
    交换机： 每个网络占一个面
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="1 0 0" max="5 2 1">
    储罐区： 给两个网络供电的能源元件、装着化学品元件的驱动器， 以及一个存储总线
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 1" max="3 3 4">
    最小的动态储罐， 3×3×3， 存储总线装在它正面中央的动态储罐阀门上
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

在联邦界面里， 工坊使用储罐区的存储， 并与储罐区共享能量：

<FederationTopology>
  <Network key="workshop" label="工坊" color="#915dcd" column="0" row="0" details="终端" />
  <Network key="farm" label="储罐区" color="#5CA7CD" column="1" row="0" details="动态储罐、化学品元件|能源元件" />
  <Rule user="workshop" source="farm" capability="storage" />
  <Energy first="workshop" second="farm" />
</FederationTopology>

## 搭建

1. **在储罐区网络上搭动态储罐**： 最小的储罐每个方向3格， 动态储罐外壳围住中间一格空气， 某一面的中央换成动态储罐阀门。
2. **在阀门上装存储总线**， 装在储罐区的一段ME线缆上。 阀门不需要任何设置。 有了Applied Mekanistics， 存储总线会把储罐里的化学品当作AE2存储读取。
3. **加入化学品元件**， 存放不放在储罐里的化学品： 把Applied Mekanistics的化学品存储元件放进储罐区的驱动器。 Mekanism的化学品不是流体， 所以流体元件装不下它们。
4. **把两个网络接到交换机上**， 各用一段ME线缆碰到一个面。
5. **在联邦界面里打开“工坊使用储罐区的”存储规则**， 再打开这对网络的**ME能量**： 工坊从此靠储罐区的电源运行。
6. **检查**。 工坊的终端会列出储罐区的化学品。 取出一些， 再存回一些： 工坊自己没有存储， 存入的东西就进了储罐区。

这条规则共享储罐区的全部存储， 化学品和其他东西一起共享， 和[共享仓库](shared-warehouse.md)一样。

## 试一试

关闭存储规则。 储罐里的化学品和元件里的内容从工坊终端里消失； 它们仍然留在储罐区。 重新打开， 它们又回来了。
