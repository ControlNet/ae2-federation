---
navigation:
  parent: index.md
  title: 入门
  icon: ae2federation:nexus_core
  position: 100
---

# 入门

本页把两个ME网络连起来， 让其中一个使用另一个的存储。 两个网络里只需要一个有能源： 共享ME能量以后， 另一个就靠它运行。

## 1. 制作联结核心

每个联邦设备都要用<ItemLink id="ae2federation:nexus_core" />制作。 在<ItemLink id="ae2:inscriber" />中把<ItemLink id="ae2:engineering_processor_press" />、<ItemLink id="ae2:logic_processor_press" />和一颗末影珍珠压成<ItemLink id="ae2federation:nexus_processor_press" />。 这个压印模板把末影珍珠压印成电路板， 电路板再和红石粉、<ItemLink id="ae2:printed_silicon" />一起压制成<ItemLink id="ae2federation:nexus_processor" />。

<Recipe id="ae2federation:nexus_processor_press" />

<RecipeFor id="ae2federation:printed_nexus_circuit" />

<RecipeFor id="ae2federation:nexus_processor" />

在工作台的一排里放<ItemLink id="ae2:fluix_crystal" />、<ItemLink id="ae2:ender_dust" />和处理器， 得到两个核心。

<RecipeFor id="ae2federation:nexus_core" />

## 2. 连接两个网络

相邻的网络只需要一个联邦桥。 相距较远的网络用交换机和联邦线缆连接， 它们靠光束转换器提供的FE运行。 两种方式打开的都是同一个联邦界面。

### 两个网络紧挨着：联邦桥

<RecipeFor id="ae2federation:bridge" />

把<ItemLink id="ae2federation:bridge" />装在第一个网络的线缆上， 让它的外侧接触第二个网络的线缆或设备。 两者仍是各自独立的网络， 通过联邦桥为联邦连在一起。 联邦桥不消耗FE。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="assets/bridge.snbt" />
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

### 两个网络相距较远：交换机、联邦线缆和光束转换器

<Row>
  <RecipeFor id="ae2federation:switch" />
  <RecipeFor id="ae2federation:cable" />
  <RecipeFor id="ae2federation:beam_converter" />
</Row>

给每个网络放一个<ItemLink id="ae2federation:switch" />， 让这个网络的一根ME线缆接触它， 再用<ItemLink id="ae2federation:cable" />把交换机连起来。 交换机每个面接一个网络， 最多六个， 所以挨着同一个交换机的几个网络可以共用它。

交换机和联邦线缆每刻消耗少量FE。 把两个<ItemLink id="ae2federation:beam_converter" />方块排成一列， 一端放在线缆上， 再用其他模组的发电机给它们输入FE； AE2本身不产出FE。 转换器有FE之前， 两个网络不会连通。 参见[能量](mechanics/power.md)。

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
  <BoxAnnotation color="#dddddd" min="4 1 0" max="5 3 1">
    光束转换器， 两个方块： 下端放在线缆上， FE从其他任意面输入
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

站在八格以内右键交换机或联邦桥， 打开联邦界面。 界面把每个网络画成一张卡片。 点击两张卡片之间的连线： 右侧列出这对网络的规则， 分为“A使用B的”和“B使用A的”两组， 下面是ME能量开关。

* 左键规则向前切换（禁用、启用、启用（可转出））， 右键向后切换。
* **存储**： 让一个网络查看、存入和取出另一个网络的物品、流体和其他资源。
* **合成**： 让一个网络向另一个网络的样板供应器下单。
* **ME能量**： 把两个网络的AE能量合成一个能量池， 双向流动。

场景里只有网络A有能源元件， 所以先打开**ME能量**： 网络B从此靠A的电运行， 它的驱动器随之上线。 然后打开“A使用B的”下的存储。

## 4. 检查是否生效

在网络A上打开ME终端： 网络B的物品会和A自己的物品列在一起， 并且可以取出。 规则的状态显示在它旁边： 生效时为绿色， 正在启动时为黄色， 被阻止时为红色， 下面一行写着原因。 [排错](troubleshooting.md)解释了每种原因。

下一步： [联邦的工作方式](mechanics/index.md)和[跨网络合成](crafting/index.md)。
