---
navigation:
  parent: items/index.md
  title: ME联邦路由器
  icon: ae2federation:router
  position: 425
categories:
- network infrastructure
item_ids:
- ae2federation:router
---

# ME联邦路由器

<BlockImage id="ae2federation:router" scale="6" />

路由器给线缆分段， 让[联邦域](../mechanics/index.md)变大时线缆不至于满载（见[网络容量](../mechanics/capacity.md)）。 它的每个面都能继续连接<ItemLink id="ae2federation:cable" />、<ItemLink id="ae2federation:switch" />， 以及样板供应器前面或处理端点前面。 联邦线缆本身就能连接和分岔， 所以联邦域只在需要容量时才用路由器。

路由器的面只接联邦方块； ME网络通过交换机接入联邦域。 右键路由器可以打开它所在联邦域的联邦界面。

## 分段

路由器会把碰到它的每一段线缆分开。 只碰到一台路由器的一段线缆， 只计它自己的**子域**： 它上面交换机接入的网络， 以及接在它上面的处理端点和样板供应器。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/router_segments.snbt" />
  <BoxAnnotation color="#dddddd" min="4 0 0" max="5 1 1">
    路由器： 把三段线缆分开
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="0 0 0" max="1 2 1">
    网络A： 它的能源元件是唯一的电源
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 0 0" max="4 1 1">
    一段线缆： 计1（子域）， 网络A
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="8 0 0" max="9 1 1">
    网络B
  </BoxAnnotation>
  <BoxAnnotation color="#5dcd70" min="7 1 0" max="8 2 1">
    网络C
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="5 0 0" max="7 1 1">
    一段线缆： 计2（子域）， 网络B和C
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4 0 1" max="5 1 3">
    一段线缆： 计1（子域）， 处理端点
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

把路由器换成一根联邦线缆， 三条分支就连成一段， 四个都要计入。 连在两台路由器之间的一段线缆要为整个联邦域转运， 计入其中的全部网络和设备， 所以这样的干线尽量短， 或者用[粗线缆](../mechanics/bundled-cables.md)来建。

## 更大的路由器

彼此贴着放的路由器， 合起来填满一个实心长方体时， 就组成一台路由器。 每条边最多8格， 1×1×1也是一台路由器。 由多个方块搭成的路由器算作一台： 一段线缆碰到它的两个方块， 仍然只算碰到一台路由器。 它外侧的每个面都和单个路由器的面一样连接。

<GameScene zoom="5" interactive={true} background="transparent">
  <ImportStructure src="../assets/router_multiblock.snbt" />
  <BoxAnnotation color="#dddddd" min="3 0 0" max="6 2 1">
    已成形： 实心的3×2×1长方体， 是一台路由器
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="0 0 0" max="2 2 1">
    未成形： L形不是实心长方体， 所以它的方块都不连接任何东西
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

* 空心、L形、T形或有凸起的一组方块不会成形， 其中任何一部分也不会单独工作： 在你补全长方体或拿走多余的方块之前， 它的所有面都断开。
* 从外侧贴着一个已成形长方体的路由器方块， 也会让它这样失效； 只在棱或角上相碰的方块仍是各自独立的路由器。
* 路由器和交换机即使相贴也不会合成一个方块， 只会互相连接。
* 空手右键一个未成形的方块， 动作栏会说明原因； 手持方块右键则照常放置。 装了Jade时， 看向路由器会显示“结构： 3×2×1”， 或者未成形的原因。

[粗线缆](../mechanics/bundled-cables.md)要靠成形的路由器接到普通线缆上： 粗线缆只连接能盖住它整个端面或侧面的路由器或交换机面， 路由器的其他面再接普通联邦线缆。

<RecipeFor id="ae2federation:router" />
