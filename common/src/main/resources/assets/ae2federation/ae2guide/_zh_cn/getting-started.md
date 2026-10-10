---
navigation:
  parent: index.md
  title: 入门
  icon: ae2federation:nexus_core
  position: 10
---

# 入门

你需要两个独立的ME网络， 只要其中一个有电： 连起来以后， 另一个可以用它的电运行。 联邦不会取代AE2自己的线缆和控制器， 只是把网络连起来。

## 1. 制作联结核心

每个联邦设备都需要<ItemLink id="ae2federation:nexus_core" />。 先在<ItemLink id="ae2:inscriber" />中用<ItemLink id="ae2:logic_processor_press" />把末影珍珠压印成电路板， 再把电路板和红石粉、<ItemLink id="ae2:printed_silicon" />一起压制成<ItemLink id="ae2federation:nexus_processor" />。

<RecipeFor id="ae2federation:printed_nexus_circuit" />

<RecipeFor id="ae2federation:nexus_processor" />

在工作台的一排里放<ItemLink id="ae2:fluix_crystal" />、<ItemLink id="ae2:ender_dust" />和处理器， 得到两个核心。

<RecipeFor id="ae2federation:nexus_core" />

## 2. 连接两个网络

下面两种方式任选其一， 打开的都是同一个联邦界面。

### 两个网络紧挨着：桥接器

<RecipeFor id="ae2federation:bridge" />

把<ItemLink id="ae2federation:bridge" />装在第一个网络的线缆上， 让它的外侧接触第二个网络的线缆或设备。 两个网络仍然是分开的， 桥接器只是为联邦把它们连起来。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="assets/bridge.snbt" />
  <BoxAnnotation color="#915dcd" min="3.375 0 0" max="6 2 1">
    网络A： 它的能源元件给两个网络供电
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="3 1 1">
    网络B： 一个驱动器， 没有自己的电源
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0.25 0.25" max="3.375 0.75 0.75">
    桥接器： 装在网络A的线缆上， 外侧接触网络B的线缆
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

### 两个网络相距较远：交换机和联邦线缆

<Row>
  <RecipeFor id="ae2federation:cable" />
  <RecipeFor id="ae2federation:switch" />
</Row>

让<ItemLink id="ae2federation:switch" />的一个面接触第一个网络的ME线缆， 另一个面接触第二个网络的ME线缆。 一个交换机最多可接六个网络， 每面一个。 网络相距较远时， 给每个网络各放一个交换机， 再用<ItemLink id="ae2federation:cable" />把交换机连起来。 两个交换机面对面贴在一起时会直接连通， 中间不需要线缆。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="assets/switch_cable.snbt" />
  <BoxAnnotation color="#915dcd" min="6 0 0" max="8 2 1">
    网络A： 它的能源元件给两个网络供电
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="5 0 0" max="6 1 1">
    交换机： 一面接网络A的线缆， 另一面接联邦线缆
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0.3 0.3" max="5 0.7 0.7">
    两个交换机之间的联邦线缆
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 0 0" max="3 1 1">
    交换机： 一面接网络B的线缆， 另一面接联邦线缆
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="2 1 1">
    网络B： 一个驱动器， 没有自己的电源
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 3. 打开共享

右键交换机或桥接器（站在八格以内）打开联邦界面。 界面把这个联邦域里的网络画成卡片。 点击两张卡片之间的连线， 或者先选中一张卡片， 再在右侧列表里点另一个网络。 右侧随即显示这对网络的规则， 分为“A使用B的”和“B使用A的”两组， 每条规则一个开关， 下面是共享能量的开关。 规则只在它的方向上生效。

* 左键开关向前切换（禁用、启用、启用（可转出））， 右键向后切换。
* **存储**： 让一个网络查看、存入和取出另一个网络的物品、流体和其他资源。
* **合成**： 让一个网络使用另一个网络的样板供应器， 用它自己能看到的材料付账。 它不需要存储规则。
* **ME能量**： 把两个网络的能量合成一个能量池； 每对网络只有一个开关。

上面的场景里只有网络A有能源元件， 所以先打开**ME能量**： 网络B从此靠A的电运行， 它的驱动器随之上线。 然后再打开“A使用B的”存储规则。

## 4. 检查是否生效

在使用对方存储的那个网络上打开ME终端： 对方网络的物品会列在里面， 并且可以取出。 规则的状态显示在开关旁边： 生效时为绿色， 尚未生效时为黄色， 被阻止时为红色， 下面一行写着原因。 参见[排错](troubleshooting.md)。

下一步： [联邦的工作方式](mechanics.md) 和 [远程合成](remote-processing.md)。
