---
navigation:
  parent: index.md
  title: ME联邦桥
  icon: ae2federation:bridge
  position: 435
categories:
- network infrastructure
item_ids:
- ae2federation:bridge
---

# ME联邦桥

<GameScene zoom="8" background="transparent">
  <ImportStructure src="../assets/bridge_part.snbt" />
</GameScene>

联邦桥直接连接两个紧挨着的网络， 不需要交换机。 把它装在一个网络的线缆上， 让它的外侧接触另一个网络的线缆或设备。 两侧仍是各自独立的网络； 联邦桥不占用频道， 没有待机耗电， 也不消耗FE， 所以联邦桥的联邦域不需要光束转换器。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/bridge.snbt" />
  <BoxAnnotation color="#915dcd" min="3.375 0 0" max="6 2 1">
    网络A： 它的能源元件给两个网络供电
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="3 1 1">
    网络B： 一个驱动器， 没有自己的电源
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0.25 0.25" max="3.375 0.75 0.75">
    联邦桥： 装在网络A的线缆上， 外侧接触网络B的线缆
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

联邦界面里会并排显示这两个网络， 打开规则后它们之间就会连上线。 这里网络A使用网络B的存储， 两个网络通过ME能量共用网络A的能源元件：

<FederationTopology>
  <Network key="a" label="网络A" color="#915dcd" column="0" row="0" details="驱动器|能源元件" />
  <Network key="b" label="网络B" color="#5CA7CD" column="1" row="0" details="驱动器" />
  <Rule user="a" source="b" capability="storage" />
  <Energy first="a" second="b" />
</FederationTopology>

联邦桥只和这两个网络组成一个小[联邦域](../mechanics/index.md)。 它不接联邦线缆； 联邦样板供应器和处理端点要通过交换机和线缆连到其他网络。

右键它打开这两个网络的联邦界面。 如果连接不正确， 右键会改为显示问题所在， 参见[排错](../troubleshooting.md)。 和AE2的其他线缆部件一样， 潜行时用扳手可以把它拆下。

<RecipeFor id="ae2federation:bridge" />
