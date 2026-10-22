---
navigation:
  parent: items/index.md
  title: ME联邦量子立方
  icon: ae2federation:quantum_cube
  position: 434
categories:
- network infrastructure
item_ids:
- ae2federation:quantum_cube
---

# ME联邦量子立方

<BlockImage id="ae2federation:quantum_cube" scale="6" />

两个绑定的量子立方无论相隔多远、即使在不同维度， 都能连接各自的联邦线缆， 就像在两者之间铺了线缆一样。 与AE2的量子网络桥一样， 只有两端都已加载时才工作。

<GameScene zoom="3" interactive={true} background="transparent">
  <ImportStructure src="../assets/quantum_cube.snbt" />
  <BoxAnnotation color="#dddddd" min="4 0 0" max="7 3 3">
    一个3×3×3的立方。 线缆接在它一面的中心
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="10 0 0" max="13 3 3">
    与它绑定的立方； 也可以在另一个维度
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="13 1 1" max="17 3 2">
    网络A
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 1 1" max="4 2 2">
    网络B
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 建造

量子立方是由方块组成的实心立方体， 尺寸为3×3×3、4×4×4或5×5×5。 其他形状不会成形。 联邦线缆只能接在一面的中心区域： 3×3×3一面正中的1格、4×4×4一面正中的2×2、5×5×5一面正中的3×3。 每一面外圈的方块不连接任何东西， 所以碰到外圈的[粗线缆](../mechanics/bundled-cables.md)不会接入。

| 立方 | 面中心 | 能接的最大线缆 |
|---|---|---|
| 3×3×3 | 1×1 | 一根线缆 |
| 4×4×4 | 2×2 | 2×2粗线缆 |
| 5×5×5 | 3×3 | 3×3粗线缆 |

## 绑定

用AE2的<ItemLink id="ae2:memory_card" />绑定两个已成形的立方：

1. 潜行右键一个立方， 把它复制到内存卡上。
2. 手持内存卡右键另一个立方。

两者就成为一对。 每个立方只与另一个立方连接： 重新绑定一个立方会断开它原来的对端， 原来的对端变为未绑定。 两个立方的尺寸必须相同； 3×3×3不会与4×4×4连接。

一对立方在两者都成形且已加载时连接。 一端的区块卸载后连接断开， 两边各自独立工作； 区块重新加载后再次连接。 立方本身从不加载区块。

用Jade查看立方， 可以看到对端的位置， 或者没有连接的原因。

## 容量与能量

在[网络容量](../mechanics/capacity.md)中， 一对已连接的立方算作与其面中心相同截面的线缆： 3×3×3的一对承载一根线缆的上限， 4×4×4的一对承载六根线缆的上限， 5×5×5的一对没有上限。 两边的网络合在一起计数， 就像中间连着线缆一样。

已成形立方的每个方块每tick消耗44 FE（22 AE）， 无论是否连接， 与AE2的量子网络桥一样； 一对立方两端都要支付。 不属于已成形立方的方块不消耗能量。

| 立方 | 每个立方每tick的FE |
|---|---|
| 3×3×3 | 1,188 |
| 4×4×4 | 2,816 |
| 5×5×5 | 5,500 |

为联邦域的[光束转换器](beam_converter.md)确定尺寸时， 已连接的立方对也算作与其面中心相同截面的线缆： 5×5×5的一对需要3×3的转换器。

<RecipeFor id="ae2federation:quantum_cube" />
