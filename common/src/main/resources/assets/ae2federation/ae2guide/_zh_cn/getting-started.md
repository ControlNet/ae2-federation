---
navigation:
  parent: index.md
  title: 入门
  icon: ae2federation:federation_logic_processor
  position: 10
---

# 入门

你需要两个各自有电的独立ME网络。 联邦不会取代AE2自己的线缆和控制器， 只是把本来就能单独工作的网络连起来。

## 1. 制作联邦逻辑处理器

每个联邦设备都需要<ItemLink id="ae2federation:federation_logic_processor" />。 在<ItemLink id="ae2:inscriber" />中用一个<ItemLink id="ae2:logic_processor" />和一份<ItemLink id="ae2:fluix_dust" />压制， 两者都会被消耗。

<RecipeFor id="ae2federation:federation_logic_processor" />

## 2. 连接两个网络

下面两种方式任选其一， 打开的都是同一个联邦界面。

### 两个网络紧挨着：桥接器

<RecipeFor id="ae2federation:bridge" />

把<ItemLink id="ae2federation:bridge" />装在第一个网络的线缆上， 让它的外侧接触第二个网络的线缆或设备。 两个网络仍然是分开的， 桥接器只是为联邦把它们连起来。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="assets/bridge.snbt" />
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

### 两个网络相距较远：路由器和联邦线缆

<Row>
  <RecipeFor id="ae2federation:cable" />
  <RecipeFor id="ae2federation:router" />
</Row>

让<ItemLink id="ae2federation:router" />的一个面接触第一个网络的ME线缆， 另一个面接触第二个网络的ME线缆。 一个路由器最多可接六个网络， 每面一个。 网络相距较远时， 给每个网络各放一个路由器， 再用<ItemLink id="ae2federation:cable" />把路由器连起来。 两个路由器面对面贴在一起时会直接连通， 中间不需要线缆。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="assets/router_cable.snbt" />
  <BoxAnnotation color="#915dcd" min="6 0 0" max="8 2 1">
    网络A
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="5 0 0" max="6 1 1">
    路由器： 一面接网络A的线缆， 另一面接联邦线缆
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0.3 0.3" max="5 0.7 0.7">
    两个路由器之间的联邦线缆
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 0 0" max="3 1 1">
    路由器： 一面接网络B的线缆， 另一面接联邦线缆
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="2 2 1">
    网络B
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 3. 打开共享

右键路由器或桥接器（站在八格以内）打开联邦界面。 界面把这个联邦域里的网络画成卡片。 点击两张卡片之间的连线， 或者先选中一张卡片， 再在右侧列表里点另一个网络。 右侧随即显示这对网络的规则， 分为“A使用B的”和“B使用A的”两组， 每条规则一个开关， 下面是共享能量的开关。 规则只在它的方向上生效。

* 左键开关向前切换（禁用、启用、启用（可转出））， 右键向后切换。
* **存储**： 让一个网络查看、存入和取出另一个网络的物品和流体。
* **合成**： 让一个网络使用另一个网络的样板供应器。 开启它会同时开启同方向的存储。
* **ME能量**： 把两个网络的能量合成一个能量池； 每对网络只有一个开关。

## 4. 检查是否生效

在使用对方存储的那个网络上打开ME终端： 对方网络的物品会列在里面， 并且可以取出。 规则的状态显示在开关旁边： 生效时为绿色， 尚未生效时为黄色， 被阻止时为红色， 下面一行写着原因。 参见[排错](troubleshooting.md)。

下一步： [联邦的工作方式](mechanics.md) 和 [远程合成](remote-processing.md)。
