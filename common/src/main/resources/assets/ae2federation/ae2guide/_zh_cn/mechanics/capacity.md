---
navigation:
  parent: mechanics/index.md
  title: 网络容量
  icon: ae2federation:cable
  position: 40
---

# 网络容量

每一段<ItemLink id="ae2federation:cable" />都会计算自己承载了多少个网络和设备， 最多承载8个。 联邦域里任何一段线缆超过这个数， **这个联邦域就会停止**： 各个网络自己照常工作， 但不再共享存储、合成、处理和能量， 直到计数回到上限以内。

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

* **交换机上的每个ME网络计1。** 同一个网络接在多个交换机面上， 仍然只计一次。
* **每个处理端点和每个联邦样板供应器计1。** 样板供应器作为一台设备单独计数， 即使它的网络也接在交换机上。

规则和能量都不影响计数： 网络一接入联邦域就会被计入。

## 分段

彼此相连的联邦线缆组成一段。 <ItemLink id="ae2federation:switch" />不会把线缆分段， 一对<ItemLink id="ae2federation:federation_p2p_tunnel" />也不会： 两边的线缆仍属于同一段。 不过这对隧道本身也算作线缆： 它计入这一段的容量计数， 最多只能承载一根线缆的上限， 即使它直接连着两台路由器、 中间没有线缆。 一对已连接的<ItemLink id="ae2federation:beam_former" />也一样， 算作相同截面的线缆； 一对已连接的<ItemLink id="ae2federation:quantum_cube" />算作与其面中心相同截面的线缆。 只有<ItemLink id="ae2federation:router" />会分段； 由多个方块搭成的路由器算作一台。

* 一段线缆**没有碰到路由器或只碰到一台**时， 它连同它上面交换机接入的网络、接在它上面的处理端点和样板供应器， 组成一个**子域**： 联邦域里单独计算容量的一部分。 这段线缆只计它的子域。
* 一段线缆**碰到两台或更多路由器**时， 它要在路由器之间转运， 所以计[联邦域](index.md)里的全部数量。
* 一个网络的交换机分布在两段上时， 在每一段里各计一次。

在上面的场景里， 左边的子域计网络A和B， 右边的子域计处理端点。 中段从一台路由器连到另一台， 所以四个都计， 包括接在它自己交换机上的网络C。

## 线缆满载时

联邦域停止不会改变世界里的任何东西： 方块都留在原处， 只要没有线缆超过8， 联邦域就会自己重新运行。 要做到这一点， 可以从满载的那段线缆上拿走一个网络或设备， 或者用路由器把这段线缆分开： 路由器后面的东西， 不再计入只碰到这一台路由器的线缆。 连在两台路由器之间的线缆计入联邦域里的全部网络和设备， 所以路由器之间的长干线最先满载。 这样的干线可以用[粗线缆](bundled-cables.md)来建： 2×2的粗线缆能承载48， 3×3没有上限。 联邦域的[光束转换器](../items/beam_converter.md)必须和它最大的线缆匹配， 所以新加的粗线缆需要一台同样尺寸的转换器。

每根线缆里的光显示它有多满： 承载得少时又细又暗， 接近上限时更粗更亮。 超过上限的线缆会闪烁红光， 方便找到满载的那一段。 这个联邦域的拓扑界面会变成暗红色， 其中每条规则都显示容量超限。 显示相关联邦域时， 只有停止的那个联邦域的底板变红。

服务端可以在 `config/ae2federation-server.toml` 里修改一段线缆的容量， 或者关闭这个上限； 放在某个世界 `serverconfig` 文件夹里的同名文件只对那个世界生效。

## 查看计数

服务端和客户端都装了Jade模组后， 看向一根联邦线缆， Jade会多显示一行容量计数， 写成"计数/上限"， 超过上限时计数变红， 后面标明计的是子域还是联邦域。 没有Jade时， 线缆里的光也能看出它有多满。
