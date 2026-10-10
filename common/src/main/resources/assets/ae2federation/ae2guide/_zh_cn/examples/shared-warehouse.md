---
navigation:
  parent: examples/index.md
  title: 共享仓库
  icon: ae2:drive
  position: 10
---

# 共享仓库

**目标**： 把库存放在一个仓库网络里， 再从几个独立工坊网络的终端里取用和存入。 每个网络保留自己的频道， 只有仓库需要电源。

**需要**： 一个带存储和电源的仓库网络， 一个或多个带终端的工坊网络， 再加一个<ItemLink id="ae2federation:switch" />。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/shared_warehouse.snbt" />
  <BoxAnnotation color="#5CA7CD" min="1 1 0" max="4 4 1">
    仓库： 驱动器， 以及给这里所有网络供电的能源元件
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="3 0 0" max="4 1 1">
    工坊1： 一个终端， 靠仓库的电源运行
  </BoxAnnotation>
  <BoxAnnotation color="#5ccd78" min="1 0 0" max="2 1 1">
    工坊2： 一个终端， 靠仓库的电源运行
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 0 0" max="3 1 1">
    交换机： 每个网络占一个面
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

在联邦界面里， 每个工坊都使用仓库的存储， 并与仓库共享能量：

<FederationTopology>
  <Network key="w1" label="工坊1" color="#915dcd" column="0" row="0" details="终端" />
  <Network key="wh" label="仓库" color="#5CA7CD" column="1" row="0" details="驱动器|能源元件" />
  <Network key="w2" label="工坊2" color="#5ccd78" column="2" row="0" details="终端" />
  <Rule user="w1" source="wh" capability="storage" />
  <Rule user="w2" source="wh" capability="storage" />
  <Energy first="w1" second="wh" />
  <Energy first="wh" second="w2" />
</FederationTopology>

## 搭建

1. **把每个网络接到交换机的一个面上**， 让该网络的ME线缆碰到这个面。 一个交换机最多接六个网络。 相距较远的网络各放一个交换机， 再用<ItemLink id="ae2federation:cable" />连起来， 参见[入门](../getting-started.md)。
2. **打开联邦界面**： 右键交换机， 选中仓库和第一个工坊之间的连线。
3. **在“工坊使用仓库的”下打开存储规则**。 另一个方向保持关闭， 这样仓库看不到工坊的存储。 同时打开这对网络的**ME能量**： 工坊从此靠仓库的电源运行。
4. **检查**。 打开工坊的终端： 仓库的物品和工坊自己的物品列在一起。 取出一些， 再存入一些： 工坊自己没有存储， 存入的东西就进了仓库。
5. **用同样的方法加入第二个工坊**： 它自己的交换机面、它自己的存储规则和ME能量。 两个工坊互相看不到对方的存储， 除非你也打开它们这一对的规则。 但它们的能量照样经由仓库连成一片： 与同一个网络共享能量的网络共用一个能量池。

## 共享了什么

这条规则共享仓库的全部ME存储， 包括流体和附属模组添加的其他资源， 并且允许工坊存入和取出。 它不按物品过滤， 也不能阻止其他玩家。 工坊不该动的库存， 应该放在一个不共享的网络上。

## 试一试

关闭这条规则。 仓库的物品从工坊终端里消失， 仓库自己照常工作。 重新打开， 物品又回来了。 如果规则一直是黄色或红色， 原因会显示在开关下面， 参见[排错](../troubleshooting.md)。
