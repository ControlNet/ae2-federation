---
navigation:
  parent: mechanics/index.md
  title: 联邦粗线缆
  icon: ae2federation:cable
  position: 245
---

# 联邦粗线缆

一根<ItemLink id="ae2federation:cable" />最多承载8个网络和设备（见[网络容量](capacity.md)）。 **粗线缆**把一整块联邦线缆合成一根承载更多的粗线缆： 外壳只有一个， 大小和截面相同， 中间流动着一道粗光束。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/bundled_cables.snbt" />
  <BoxAnnotation color="#dddddd" min="0 0 2" max="1 2 4">
    2×2路由器： 盖住整个端面， 所以粗线缆接上了
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="1 0 2" max="7 2 4">
    粗线缆， 2×2， 长6： 容量48
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0 4" max="5 2 6">
    分叉： 同样是2×2， 横跨整个侧面， 占两层
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="5 0 0" max="7 2 2">
    转弯： 从最后两层分出的分叉
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 合成粗线缆

先用普通联邦线缆填满粗线缆要占的整个方块区域。 然后拿着AE2扳手（或其他扳手）， 依次右键其中三根：

1. 截面的一个角；
2. 同一平面内对角的那根： 围成2×2或3×3的正方形；
3. 截面正前方或正后方的一根线缆， 它决定长度（最长64）。

整个区域会一次变成一根粗线缆。 区域里每个位置都必须是还没合成的普通联邦线缆。 只要一层长的粗线缆时， 第三下点在截面里、朝向粗线缆走向的那一面上。 右键空气可以重新开始。

## 容量

粗线缆和普通线缆一样计算它承载的网络和设备（见[网络容量](capacity.md)）， 只是上限更大：

| 截面 | 相当于几根线缆 | 默认每根8时 |
|---|---|---|
| 2×2 | 6 | 48 |
| 3×3 | 无上限 | 无上限 |

服务端修改一根线缆的容量时， 每根粗线缆的上限都会跟着变。

## 延伸、分叉和转弯

拿着联邦线缆右键粗线缆， 轮廓会显示新线缆放在哪里。

* 右键**端面**会加一层， 截面每格用一根线缆： 2×2用4根。
* 右键**侧面**会开出一个截面相同的分叉。 它横跨整个侧面， 沿粗线缆占它需要的层数： 2×2的分叉占两层， 3×3的分叉占三层。 分叉以点击的位置为中心。 再从分叉的端面延伸它。
* **转弯**就是从最后几层分出的分叉。

潜行右键则照常放下一根普通线缆。

## 连接

* 粗线缆只能通过两者都接上的同一台成形设备接到普通联邦线缆， 例如<ItemLink id="ae2federation:router" />： 把两者都接到它上面。 两根并排的粗线缆互不相连。
* 粗线缆只连接由足够多方块搭成、能接联邦线缆的设备面： 路由器或<ItemLink id="ae2federation:switch" />的面、<ItemLink id="ae2federation:beam_former" />的背面、<ItemLink id="ae2federation:beam_converter" />的前面， 或<ItemLink id="ae2federation:quantum_cube" />一面的中心。 它的**端面**要被一台设备整个盖住才会连接： 2×2的粗线缆需要至少2×2的路由器面。
* **侧面**连接盖住侧面全宽的设备： 2×2粗线缆的侧面可以连接横跨它的1×2路由器面， 但不能连接单个路由器方块。

装了Jade时， 看向粗线缆会显示它的截面、容量和接上的端面与侧面数量， 并指出只盖住一部分的设备。

## 拆开粗线缆

破坏一根粗线缆会拆下这一根并掉落出来； 用扳手潜行右键它， 则直接收进背包。 它那一层其余的线缆变回普通联邦线缆， 粗线缆也从这里分成两根。 要修复它， 把线缆放回去， 再用扳手把这一层重新合成： 截面相同、端面对齐相接的粗线缆会合成一根。
