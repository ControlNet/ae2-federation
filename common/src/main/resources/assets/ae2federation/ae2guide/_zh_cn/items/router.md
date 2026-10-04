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

路由器把ME网络接入一个[联邦域](../mechanics.md)。 它的六个面各自独立工作：

* 接触ME线缆或设备时， 这个面加入那个网络， 不占用频道， 也没有待机耗电；
* 接触<ItemLink id="ae2federation:cable" />、另一个路由器、联邦样板供应器前面或处理端点前面时， 这个面把联邦域继续向外连接。

一个路由器上最多可以汇集六个不同的网络， 它的各个面不会把这些网络合成一个。 两个面接同一个网络也可以， 只算一次。 两个路由器面对面贴在一起会直接连通， 和用联邦线缆连接一样。

<GameScene zoom="5" interactive={true} background="transparent">
  <ImportStructure src="../assets/router_hub.snbt" />
  <BoxAnnotation color="#915dcd" min="3 0 0" max="5 2 1">
    网络A
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="2 2 1">
    网络B
  </BoxAnnotation>
  <BoxAnnotation color="#5dcd70" min="2 1 0" max="3 3 1">
    网络C
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 0 0" max="3 1 1">
    一个路由器， 三个网络： 每个面加入它接触的网络， 这些网络仍各自独立
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

右键路由器打开它所在联邦域的联邦界面， 在那里打开共享。 参见[入门](../getting-started.md)。

<RecipeFor id="ae2federation:router" />

一次合成产出四个路由器。
