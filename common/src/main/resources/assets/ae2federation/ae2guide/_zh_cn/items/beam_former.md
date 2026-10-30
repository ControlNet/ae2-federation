---
navigation:
  parent: items/index.md
  title: ME联邦光束成型器
  icon: ae2federation:beam_former
  position: 433
categories:
- network infrastructure
item_ids:
- ae2federation:beam_former
---

# ME联邦光束成型器

<BlockImage id="ae2federation:beam_former" scale="6" />

两台面对面的光束成型器隔空连接各自的联邦线缆， 就像在两者正面之间铺了线缆一样。 在一条直线的两端各建一台， 中间没有实心方块， 两端就会加入同一个[联邦域](../mechanics/index.md)。

<GameScene zoom="2.5" interactive={true} background="transparent">
  <ImportStructure src="../assets/beam_former.snbt" />
  <BoxAnnotation color="#dddddd" min="4 0 0" max="5 1 1">
    一台朝东的光束成型器。 背面像路由器的面一样接线缆
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="7 0 0" max="8 1 1">
    玻璃不会挡住光束
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="12 0 0" max="15 2 1">
    网络A
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="3 1 1">
    网络B
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 建造

光束成型器可以是单个方块， 也可以是由方块组成、厚度为1的2×2或3×3正方形。 放置时它朝向你看的方向。 正面发出光束； 背面像路由器的面一样， 连接联邦线缆、截面不大于它的[粗线缆](../mechanics/bundled-cables.md)或任意联邦方块。

用扳手右键已成形的2×2或3×3， 可以把它前后调转。 单个方块则转向你点击的那一面。

## 连接

光束成型器与它正前方同一直线上**最近**的成型器连接， 前提是对方朝向这边、截面相同。 两者正面之间最多相隔256格， 服务器最多可以设为1024格。 正面贴着正面的两台也会连接。

光束路径上任何有碰撞箱的方块都会挡住光束， 整个截面都算。 玻璃、玻璃板、水和AE2的石英玻璃可以透过。 挡住路径的方块放下一秒后连接断开， 路径通畅一秒后连接恢复。 只要两端都已加载， 连接就保持； 中间的区块可以不加载。

用Jade查看成型器， 可以看到光束的状态： 已连接及长度、被挡住及位置， 或者没有对端的原因。

## 容量与能量

在[网络容量](../mechanics/capacity.md)中， 一对已连接的成型器算作相同截面的线缆： 单个方块的一对承载一根线缆的上限， 2×2的一对承载六根线缆的上限， 3×3的一对没有上限。 光束像线缆一样显示负载。

联邦域为每对已连接的成型器按截面和长度支付一次能量， 另加其他方块的消耗： 2 ×（截面方块数）× L^1.05 FE每tick， L是两者正面之间的格数（至少按2计算）。

| 成型器对 | 相隔格数 | 每tick的FE |
|---|---|---|
| 1×1 | 16 | 37 |
| 1×1 | 64 | 158 |
| 2×2 | 64 | 630 |
| 3×3 | 64 | 1,418 |

成型器方块本身不消耗能量。 为联邦域的[光束转换器](beam_converter.md)确定尺寸时， 已连接的成型器对也算作相同截面的线缆： 3×3的一对需要3×3的转换器。

<RecipeFor id="ae2federation:beam_former" />
