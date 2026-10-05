---
navigation:
  parent: examples/index.md
  title: 发电厂
  icon: mekanism:basic_induction_cell
  position: 43
---

# 发电厂

**目标**： 一个发电厂网络把基地的电存在Mekanism的感应矩阵里， 通过ME能量给另外两个网络供电： 城区和它的工坊。 三个网络都没有能源元件， 感应矩阵是唯一储存电力的地方。 这一页出现， 是因为安装了Mekanism。

**需要**： 一个发电厂网络， 带一台感应矩阵（<ItemLink id="mekanism:induction_casing" />、<ItemLink id="mekanism:induction_port" />、一个<ItemLink id="mekanism:basic_induction_cell" />和一个<ItemLink id="mekanism:basic_induction_provider" />）和一个<ItemLink id="ae2:energy_acceptor" />； 一个城区网络， 带合成CPU、合成终端和存储； 一个工坊网络， 带一个样板供应器和一台分子装配室； 三个网络共同接触的一个<ItemLink id="ae2federation:router" />。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/power_plant.snbt" />
  <BoxAnnotation color="#5CA7CD" min="2 0 1" max="5 4 5">
    发电厂： 感应矩阵， 它的端口设为输出， 接到ME线缆上的能源接收器
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="4 0 0" max="6 2 1">
    城区： 合成终端、合成CPU和驱动器
  </BoxAnnotation>
  <BoxAnnotation color="#5ccd78" min="0 0 0" max="3 1 1">
    工坊： 样板供应器里有从两块木板到四根木棍的样板， 旁边是分子装配室
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0 0" max="4 1 1">
    一个路由器： 每个面加入它接触的网络
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

联邦界面里会显示这三个网络和它们之间的规则：

<FederationTopology>
  <Network key="district" label="城区" color="#915dcd" column="0" row="1" details="合成CPU、终端|驱动器" />
  <Network key="plant" label="发电厂" color="#5CA7CD" column="1" row="0" details="感应矩阵|能源接收器" />
  <Network key="workshop" label="工坊" color="#5ccd78" column="2" row="1" details="木棍样板" />
  <Rule user="district" source="workshop" capability="crafting" />
  <Rule user="district" source="workshop" capability="storage" />
  <Energy first="plant" second="district" />
  <Energy first="plant" second="workshop" />
</FederationTopology>

## 搭建

1. **把三个网络接到同一个路由器的各个面上**， 如场景所示。
2. **搭发电厂**： 在路由器的面上接一根ME线缆， 上面放能源接收器， 再往外是感应矩阵， 它的端口贴着能源接收器。 拿着<ItemLink id="mekanism:configurator" />潜行右键端口， 把它设为输出： 端口就会把矩阵里的电推进能源接收器， 由接收器转成AE。
3. **给矩阵充电**： 用另一个设为输入的感应端口接上你的发电机。
4. **打开ME能量**： 发电厂和城区之间， 以及发电厂和工坊之间。 两个网络都靠感应矩阵运行。
5. **打开“城区使用工坊的”合成规则**， 同方向的存储规则会一起打开。 在城区的合成终端里下单木棍， 城区的存储里放好木板。

## 怎样运行

能源接收器从感应矩阵取电， 按三个网络的用电速度填满它们自带的小缓冲， 所以这些网络都不需要自己的能源元件。 网络运行时矩阵的电量下降， 由你的发电机补上。

## 试一试

关闭发电厂和工坊之间的ME能量。 工坊断电， 木棍从城区的可合成物品里消失， 城区则继续靠发电厂运行。
