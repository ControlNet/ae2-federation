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

<GameScene zoom="8" background="transparent">
  <ImportStructure src="../assets/bridge_part.snbt" />
</GameScene>

桥接器直接连接两个紧挨着的网络， 不需要路由器。 把它装在一个网络的线缆上， 让它的外侧接触另一个网络的线缆或设备。 两侧仍是各自独立的网络； 桥接器不占用频道， 也没有待机耗电。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/bridge.snbt" />
  <BoxAnnotation color="#915dcd" min="3.375 0 0" max="6 2 1">
    网络A
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="3 2 1">
    网络B
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0.25 0.25" max="3.375 0.75 0.75">
    桥接器： 装在网络A的线缆上， 外侧接触网络B的线缆
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

联邦界面里会并排显示这两个网络， 打开规则后它们之间就会连上线。 这里网络A使用网络B的存储：

<FederationTopology>
  <Network key="a" label="网络A" color="#915dcd" column="0" row="0" details="驱动器|能源元件" />
  <Network key="b" label="网络B" color="#5CA7CD" column="1" row="0" details="驱动器|能源元件" />
  <Rule user="a" source="b" capability="storage" />
</FederationTopology>

桥接器只和这两个网络组成一个小[联邦域](../mechanics.md)。 它不连接联邦线缆， 所以联邦样板供应器和处理端点不能通过它连到其他网络。

右键它打开这两个网络的联邦界面。 如果连接不正确， 右键会改为显示问题所在， 参见[排错](../troubleshooting.md)。 和AE2的其他线缆部件一样， 潜行时用扳手可以把它拆下。

<RecipeFor id="ae2federation:bridge" />
