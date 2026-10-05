---
navigation:
  parent: examples/index.md
  title: 天枢城区和铸造厂
  icon: ae2lt:tianshu_supercomputer_controller
  position: 72
---

# 天枢城区和铸造厂

**目标**： 一个城区网络唯一的CPU是AE2 Lightning Tech的天枢超算， 它向一个铸造厂网络下单木棍， 铸造厂唯一的合成设备是天枢物质扭曲矩阵。 两个多方块结构各在自己的网络上， 通过一个路由器协同工作。 这一页出现， 是因为安装了AE2 Lightning Tech。

**需要**： 一个城区网络， 带一台已成型的天枢超算（<ItemLink id="ae2lt:tianshu_supercomputer_controller" />）、合成终端、存储和能源元件； 一个铸造厂网络， 带一台已成型的天枢物质扭曲矩阵（<ItemLink id="ae2lt:matter_warping_matrix_controller" />）和一个从两块木板到四根木棍的合成样板； 两个网络共同接触的一个<ItemLink id="ae2federation:router" />。 怎么搭见AE2 Lightning Tech的指南： [天枢超算](ae2lt:tianshu/construction.md)和[物质扭曲矩阵](ae2lt:matrix/construction.md)。

<GameScene zoom="2" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/tianshu_foundry.snbt" />
  <BoxAnnotation color="#915dcd" min="9 0 0" max="17 8 7">
    城区： 天枢超算、合成终端、驱动器， 以及给两个网络供电的能源元件
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="8 11 7">
    铸造厂： 天枢物质扭曲矩阵， 木棍样板放在一个样板仓里
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="8 0 0" max="9 1 1">
    一个路由器： 每个面加入它接触的网络
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

联邦界面里会显示这两个网络和它们之间的规则：

<FederationTopology>
  <Network key="district" label="城区" color="#915dcd" column="0" row="0" details="天枢超算|能源元件" />
  <Network key="foundry" label="铸造厂" color="#5CA7CD" column="1" row="0" details="物质扭曲矩阵" />
  <Rule user="district" source="foundry" capability="crafting" />
  <Rule user="district" source="foundry" capability="storage" />
  <Energy first="district" second="foundry" />
</FederationTopology>

## 搭建

1. **把两个网络接到同一个路由器的两个面上**， 如场景所示： 一个面用ME线缆接到天枢超算底面中央的<ItemLink id="ae2lt:tianshu_supercomputer_port" />， 另一个面接到矩阵控制器对面的<ItemLink id="ae2lt:matter_warping_matrix_port" />。 每个端口都要等结构成型后才能接线缆。
2. **把木棍样板放进矩阵**： 用矩阵端口的样板管理界面放入。
3. **打开“城区使用铸造厂的”合成规则**。 同方向的存储规则会一起打开。
4. **打开两个网络之间的ME能量**， 矩阵就靠城区的能源元件运行： 空闲时8 AE/t， 每接受一次合成再消耗1 AE。
5. **下单木棍**： 在城区的合成终端里下单， 城区的存储里放好木板。

## 任务怎样运行

天枢超算像任何合成CPU一样计划并执行任务。 它把木板送到矩阵， 矩阵自己合成木棍， 不需要分子装配室， 再把木棍放进铸造厂的网络。 木棍一到， 联邦就把它直接交回城区。 铸造厂自己不需要CPU、 存储和能源。

## 试一试

打掉矩阵外壳的一个方块。 结构解体， 端口离开铸造厂的网络， 木棍从城区的可合成物品里消失， 联邦界面会把铸造厂显示为“分裂待定”。 把方块放回去： 矩阵重新成型， 两半重新接上， 木棍回到城区。
