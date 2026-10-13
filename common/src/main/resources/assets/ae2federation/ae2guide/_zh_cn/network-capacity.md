---
navigation:
  parent: index.md
  title: 网络容量
  icon: ae2federation:cable
  position: 22
---

# 网络容量

每一段<ItemLink id="ae2federation:cable" />都会计算自己承载了多少个网络和处理端点。 目前这只是计数： 线缆还没有任何上限， 计数再高也不会让任何东西停止工作。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="assets/network_capacity.snbt" />
  <BoxAnnotation color="#915dcd" min="9 0 0" max="11 1 1">
    网络A： 计1
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="8 1 0" max="9 2 1">
    网络B， 接在同一台交换机上： 再计1
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="7 0 0" max="8 1 1">
    左段： 2（子域）， 网络A和B
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="6 0 0" max="7 1 1">
    路由器： 在这里分段
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0 0" max="6 1 1">
    中段， 穿过交换机： 4（联邦域）， 因为它连在两台路由器之间
  </BoxAnnotation>
  <BoxAnnotation color="#5dcd70" min="4 1 0" max="5 2 1">
    网络C： 计1
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 0 0" max="3 1 1">
    路由器： 在这里分段
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="1 0 0" max="2 1 1">
    右段： 1（子域）， 处理端点
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="0 0 0" max="1 1 1">
    处理端点： 计1
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 计什么

* **交换机上的每个ME网络计1。** 同一个网络接在多个交换机面上， 仍然只计一次。
* **每个处理端点计1。**
* **联邦样板供应器不计。** 计的是它派活给的那些处理端点。

规则和能量都不影响计数： 网络一接入联邦域就会被计入。

## 分段

彼此相连的联邦线缆组成一段。 <ItemLink id="ae2federation:switch" />不会把线缆分段， 一对<ItemLink id="ae2federation:federation_p2p_tunnel" />也不会： 两边的线缆仍属于同一段。 只有<ItemLink id="ae2federation:router" />会分段； 由多个方块搭成的路由器算作一台。

* 一段线缆**没有碰到路由器或只碰到一台**时， 它连同它上面交换机接入的网络、接在它上面的处理端点， 组成一个**子域**： 联邦域里单独计算容量的一部分。 这段线缆只计它的子域。
* 一段线缆**碰到两台或更多路由器**时， 它要在路由器之间转运， 所以计[联邦域](mechanics.md)里的全部数量。
* 一个网络的交换机分布在两段上时， 在每一段里各计一次。

在上面的场景里， 左边的子域计网络A和B， 右边的子域计处理端点。 中段从一台路由器连到另一台， 所以四个都计， 包括接在它自己交换机上的网络C。

## 查看计数

服务端和客户端都装了Jade模组后， 看向一根联邦线缆， Jade会多显示一行容量计数， 后面标明计的是子域还是联邦域。 没有Jade时， 目前在别处还看不到这个计数。
