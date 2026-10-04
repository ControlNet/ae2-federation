---
navigation:
  parent: examples/index.md
  title: 共享仓库
  icon: ae2:drive
  position: 10
---

# 共享仓库

**目标**： 把库存放在一个仓库网络里， 再从几个独立工坊网络的终端里取用和存入。 每个网络保留自己的频道和电力。

**需要**： 一个带存储的仓库网络， 一个或多个带终端的工坊网络， 每个网络各自有电， 再加一个<ItemLink id="ae2federation:router" />。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/shared_warehouse.snbt" />
  <BoxAnnotation color="#5CA7CD" min="1 1 0" max="4 4 1">
    仓库： 驱动器和自己的电源
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="3 0 0" max="5 1 1">
    工坊1： 一个终端和自己的电源
  </BoxAnnotation>
  <BoxAnnotation color="#5ccd78" min="0 0 0" max="2 1 1">
    工坊2： 一个终端和自己的电源
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 0 0" max="3 1 1">
    路由器： 每个网络占一个面
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 搭建

1. **把每个网络接到路由器的一个面上**， 让该网络的ME线缆碰到这个面。 一个路由器最多接六个网络。 相距较远的网络各放一个路由器， 再用<ItemLink id="ae2federation:cable" />连起来， 参见[入门](../getting-started.md)。
2. **打开联邦界面**： 右键路由器， 选中仓库和第一个工坊之间的连线。
3. **在“工坊使用仓库的”下打开存储规则**。 另一个方向保持关闭， 这样仓库看不到工坊的存储。
4. **检查**。 打开工坊的终端： 仓库的物品和工坊自己的物品列在一起。 取出一些， 再存入一些： 工坊自己没有存储， 存入的东西就进了仓库。
5. **用同样的方法加入第二个工坊**： 它自己的路由器面和它自己的规则。 两个工坊互相看不到， 除非你也打开它们这一对的规则。

## 共享了什么

这条规则共享仓库的全部ME存储， 包括流体和附属模组添加的其他资源， 并且允许工坊存入和取出。 它不按物品过滤， 也不能阻止其他玩家。 工坊不该动的库存， 应该放在一个不共享的网络上。

## 试一试

关闭这条规则。 仓库的物品从工坊终端里消失， 仓库自己照常工作。 重新打开， 物品又回来了。 如果规则一直是黄色或红色， 原因会显示在开关下面， 参见[排错](../troubleshooting.md)。
