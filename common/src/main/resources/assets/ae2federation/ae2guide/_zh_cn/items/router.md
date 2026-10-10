---
navigation:
  parent: index.md
  title: ME联邦路由器
  icon: ae2federation:router
  position: 120
categories:
- network infrastructure
item_ids:
- ae2federation:router
---

# ME联邦路由器

<BlockImage id="ae2federation:router" scale="6" />

路由器把[联邦域](../mechanics.md)继续向外延伸。 它的六个面各自连接接触到的东西： <ItemLink id="ae2federation:cable" />、<ItemLink id="ae2federation:switch" />、另一个路由器、联邦样板供应器前面或处理端点前面。 用它连接联邦线缆， 或者让线缆分岔。

它不接入ME网络： 接触它的ME线缆或设备不会连上。 要把网络接进来， 请用交换机。

<GameScene zoom="5" interactive={true} background="transparent">
  <ImportStructure src="../assets/cable_connections.snbt" />
  <BoxAnnotation color="#dddddd" min="2 0 0" max="3 1 1">
    路由器： 把两侧的线缆连成同一个联邦域
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

右键路由器打开它所在联邦域的联邦界面。

<RecipeFor id="ae2federation:router" />
