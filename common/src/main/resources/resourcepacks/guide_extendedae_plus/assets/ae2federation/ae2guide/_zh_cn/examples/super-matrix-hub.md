---
navigation:
  parent: examples/index.md
  title: 一个工厂供两个街区
  icon: extendedae_plus:super_assembler_matrix_frame
  position: 75
---

# 一个工厂供两个街区

**目标**： 一个工厂网络有一台装满合成样板的超级装配矩阵， 两个街区网络同时向它下单， 各用自己的合成CPU。 工厂还给两个街区供电。 这一页出现， 是因为安装了ExtendedAE-Plus。

**需要**： 工厂网络上一台已成型的超级装配矩阵， 装好样板， 再加一个能源元件； 两个街区网络， 每个都有合成CPU、合成终端和存储； 三个网络共同接触的一个<ItemLink id="ae2federation:router" />。 矩阵怎么搭见[ExtendedAE-Plus的指南](extendedae_plus:introduction/devices/super_assembler_matrix.md)。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/super_matrix_hub.snbt" />
  <BoxAnnotation color="#5CA7CD" min="1 0 1" max="6 3 7">
    工厂： 装着样板的超级装配矩阵， 以及给三个网络供电的能源元件
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="4 0 0" max="6 2 1">
    第一个街区： 合成终端、合成CPU和存储
  </BoxAnnotation>
  <BoxAnnotation color="#5ccd78" min="1 0 0" max="3 2 1">
    第二个街区： 合成终端、合成CPU和存储
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0 0" max="4 1 1">
    一个路由器： 每个面加入它接触的网络
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

联邦界面里会显示这三个网络和它们之间的规则：

<FederationTopology>
  <Network key="first" label="第一个街区" color="#915dcd" column="0" row="0" details="合成CPU|终端、存储" />
  <Network key="factory" label="工厂" color="#5CA7CD" column="1" row="0" details="超级装配矩阵|能源元件" />
  <Network key="second" label="第二个街区" color="#5ccd78" column="2" row="0" details="合成CPU|终端、存储" />
  <Rule user="first" source="factory" capability="crafting" />
  <Rule user="first" source="factory" capability="storage" />
  <Rule user="second" source="factory" capability="crafting" />
  <Rule user="second" source="factory" capability="storage" />
  <Energy first="first" second="factory" />
  <Energy first="factory" second="second" />
</FederationTopology>

## 搭建

1. **把三个网络接到同一个路由器的各个面上**， 如场景所示。 矩阵通过它任何一个外层方块加入工厂的网络。
2. **打开“第一个街区使用工厂的”和“第二个街区使用工厂的”合成规则**。 每条都会打开同方向的存储规则。
3. **打开工厂和每个街区之间的ME能量**， 两个街区都靠工厂的能源元件运行。
4. **在两个街区同时下单**。 矩阵的配方列在每个街区的可合成物品里。

## 任务怎样运行

每个街区自己的合成CPU执行它的订单， 把原料发给矩阵。 矩阵同时合成两份订单， 再把每份产物送回下单的那个CPU。 工厂不需要CPU， 两个街区也看不到彼此的存储： 每个街区只和工厂有规则。

## 试一试

关闭“第二个街区使用工厂的”合成规则。 矩阵的配方只从第二个街区消失， 第一个街区照样下单。
