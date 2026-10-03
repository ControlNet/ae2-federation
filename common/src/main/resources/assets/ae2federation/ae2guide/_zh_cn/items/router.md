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

<ItemImage id="ae2federation:router" scale="4" />

路由器把ME网络接入一个[联邦域](../mechanics.md)。 它的六个面各自独立工作：

* 接触ME线缆或设备时， 这个面加入那个网络， 不占用频道， 也没有待机耗电；
* 接触<ItemLink id="ae2federation:cable" />、联邦样板供应器前面或处理端点前面时， 这个面把联邦域继续向外连接。

一个路由器上最多可以汇集六个不同的网络， 它的各个面不会把这些网络合成一个。 两个面接同一个网络也可以， 只算一次。 两个路由器面对面贴在一起不会连通， 请用联邦线缆连接。

右键路由器打开它所在联邦域的联邦界面， 在那里打开共享。 参见[入门](../getting-started.md)。

<RecipeFor id="ae2federation:router" />

一次合成产出四个路由器。
