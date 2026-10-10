---
navigation:
  parent: index.md
  title: ME联邦线缆
  icon: ae2federation:cable
  position: 110
categories:
- network infrastructure
item_ids:
- ae2federation:cable
---

# ME联邦线缆

<GameScene zoom="5" interactive={true} background="transparent">
  <ImportStructure src="../assets/cable_connections.snbt" />
  <BoxAnnotation color="#dddddd" min="4 0 0" max="5 1 1">
    联邦样板供应器： 用前面连接
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 0 0" max="3 1 1">
    路由器： 任意一面都能连接
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="0 0 0" max="1 1 1">
    处理端点： 用前面连接
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

联邦线缆把交换机、路由器、联邦样板供应器和处理端点连成一个[联邦域](../mechanics.md)。 它的每一面都能连接另一根联邦线缆、<ItemLink id="ae2federation:switch" />或<ItemLink id="ae2federation:router" />的任意一面， 以及<ItemLink id="ae2federation:pattern_provider" />、<ItemLink id="ae2federation:processing_endpoint" />或<ItemLink id="ae2federation:federation_p2p_tunnel" />的前面。

它不是ME线缆： ME线缆和设备不会接到它上面， 它不传递频道， 也不耗电。 它没有长度限制。 线缆里的光脉冲只是装饰； 要看实际流量， 请使用联邦界面里的**实时流量**。

<RecipeFor id="ae2federation:cable" />

任意颜色的<ItemLink id="ae2:fluix_glass_cable" />（可以混用）都能做出16根。
