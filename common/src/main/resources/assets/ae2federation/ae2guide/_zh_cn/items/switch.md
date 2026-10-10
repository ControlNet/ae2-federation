---
navigation:
  parent: index.md
  title: ME联邦交换机
  icon: ae2federation:switch
  position: 115
categories:
- network infrastructure
item_ids:
- ae2federation:switch
---

# ME联邦交换机

<BlockImage id="ae2federation:switch" scale="6" />

交换机把ME网络接入一个[联邦域](../mechanics.md)。 它的六个面各自独立工作：

* 接触ME线缆或设备时， 这个面加入那个网络， 不占用频道， 也没有待机耗电；
* 接触<ItemLink id="ae2federation:cable" />、<ItemLink id="ae2federation:router" />、另一个交换机、联邦样板供应器前面或处理端点前面时， 这个面把联邦域继续向外连接。

一个交换机上最多可以汇集六个不同的网络， 它的各个面不会把这些网络合成一个。 两个面接同一个网络也可以， 只算一次。 两个交换机面对面贴在一起会直接连通， 和用联邦线缆连接一样。

<GameScene zoom="5" interactive={true} background="transparent">
  <ImportStructure src="../assets/switch_hub.snbt" />
  <BoxAnnotation color="#915dcd" min="3 0 0" max="5 2 1">
    网络A： 它的能源元件给三个网络供电
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="2 1 1">
    网络B： 一个驱动器， 没有自己的电源
  </BoxAnnotation>
  <BoxAnnotation color="#5dcd70" min="2 1 0" max="3 3 1">
    网络C： 一个驱动器， 没有自己的电源
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 0 0" max="3 1 1">
    一个交换机， 三个网络： 每个面加入它接触的网络， 这些网络仍各自独立
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

联邦界面里每个网络都是一张单独的卡片。 这里网络A和网络C都使用网络B的存储， 三个网络通过ME能量共用网络A的能源元件：

<FederationTopology>
  <Network key="a" label="网络A" color="#915dcd" column="0" row="1" details="驱动器|能源元件" />
  <Network key="c" label="网络C" color="#5dcd70" column="1" row="0" details="驱动器" />
  <Network key="b" label="网络B" color="#5CA7CD" column="2" row="1" details="驱动器" />
  <Rule user="a" source="b" capability="storage" />
  <Rule user="c" source="b" capability="storage" />
  <Energy first="a" second="b" />
  <Energy first="c" second="b" />
</FederationTopology>

右键交换机打开它所在联邦域的联邦界面， 在那里打开共享。 参见[入门](../getting-started.md)。

<RecipeFor id="ae2federation:switch" />
