---
navigation:
  parent: index.md
  title: ME联邦桥
  icon: ae2federation:bridge
  position: 130
categories:
- network infrastructure
item_ids:
- ae2federation:bridge
---

# ME联邦桥

<ItemImage id="ae2federation:bridge" scale="4" />

桥接器直接连接两个紧挨着的网络， 不需要路由器。 把它装在一个网络的线缆上， 让它的外侧接触另一个网络的线缆或设备。 两侧仍是各自独立的网络； 桥接器不占用频道， 也没有待机耗电。

桥接器只和这两个网络组成一个小[联邦域](../mechanics.md)。 它不连接联邦线缆， 所以联邦样板供应器和处理端点不能通过它连到其他网络。

右键它打开这两个网络的联邦界面。 如果连接不正确， 右键会改为显示问题所在， 参见[排错](../troubleshooting.md)。 和AE2的其他线缆部件一样， 潜行时用扳手可以把它拆下。

<RecipeFor id="ae2federation:bridge" />
