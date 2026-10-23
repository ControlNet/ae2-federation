---
navigation:
  parent: mechanics/index.md
  title: 网络容量
  icon: ae2federation:cable
  position: 240
---

# 网络容量

每一段<ItemLink id="ae2federation:cable" />最多承载8个网络和设备。 联邦域里任何一段线缆超过这个数， **这个联邦域就会停止**： 各个网络照常独立工作， 在计数回到上限以内之前不共享任何东西。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/network_capacity.snbt" />
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

* **交换机上的每个ME网络计1**， 无论它接触多少个交换机面， 都只计一次。
* **每个处理端点和每个联邦样板供应器计1。** 样板供应器作为一台设备单独计数， 即使它的网络也接在交换机上。

网络一接入联邦域就会被计入， 与它的规则和能量无关。

## 分段与子域

彼此相连的联邦线缆组成一**段**。 只有<ItemLink id="ae2federation:router" />会分段， 由多个方块搭成的路由器算作一台。 <ItemLink id="ae2federation:switch" />位于所在的段之内， [远距离连接](long-distance.md)也一样： 一对P2P通道、光束成型器或量子立方把两边的线缆连成同一段。 每种连接都算作相应尺寸的线缆， 所以一对P2P通道即使直接连着两台路由器， 也只能承载一根线缆的上限。

* 一段线缆**没有碰到路由器或只碰到一台**时， 它和它上面交换机接入的网络、接在它上面的设备组成一个**子域**。 这段线缆只计它的子域。
* 一段线缆**碰到两台或更多路由器**时， 它要在路由器之间转运， 所以计整个[联邦域](index.md)。
* 一个网络的交换机分布在两段上时， 在每一段里各计一次。

在上面的场景里， 左边的子域计网络A和B， 右边的子域计处理端点。 中段从一台路由器连到另一台， 所以四个都计， 包括接在它自己交换机上的网络C。

## 腾出容量

停止的联邦域不会改变世界里的任何方块， 只要没有线缆超过上限， 它就会自己重新运行。 做法有：

* **移走网络或设备**， 让它离开满载的那一段。
* **用路由器分段。** 路由器后面的东西， 不再计入只碰到这一台路由器的线缆。
* **把干线换成粗线缆。** 连在路由器之间的线缆计整个联邦域， 所以最先满载。 用[粗线缆](bundled-cables.md)来建： 2×2的粗线缆能承载48， 3×3没有上限。 粗线缆需要一台同样尺寸的[光束转换器](../items/beam_converter.md)。

## 查看计数

每根线缆里的光显示它有多满： 承载得少时又细又暗， 接近上限时更粗更亮。 超过上限的线缆会闪烁红光。 停止的联邦域的拓扑界面变成暗红色， 其中每条规则都显示容量超限； 显示相连的域时， 只有停止的那个联邦域的底板变红。

服务端和客户端都装了Jade模组后， 看向一根联邦线缆， Jade会多显示一行容量计数， 写成“计数/上限”， 后面标明计的是子域还是联邦域， 超过上限时变红。

## 服务器设置

`config/ae2federation-server.toml`的`[capacity]`部分有两个选项： `cableCapacity`是一根线缆的上限， `enforceCableCapacity`设为false时关闭上限。 放在某个世界`serverconfig`文件夹里的同名文件只对那个世界生效。 每种粗线缆的上限都随`cableCapacity`变化。
