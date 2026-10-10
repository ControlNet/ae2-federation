---
navigation:
  parent: examples/index.md
  title: 太阳能天文台
  icon: data_energistics:astronomical_observatory
  position: 74
---

# 太阳能天文台

**目标**： 一个天文台网络在夜里用一个太阳能前哨网络的电， 把AE能量变成Data Energistics的耀星流； 你的主网络从天文台的存储里读取耀星流。 能量和产物朝相反方向流动， 各走自己的规则。 这一页出现， 是因为安装了Data Energistics。

**需要**： 一个前哨网络， 带六块<ItemLink id="data_energistics:me_solar_panel" />和一个能源元件； 一个天文台网络， 带一台<ItemLink id="data_energistics:astronomical_observatory" />和一个装着<ItemLink id="data_energistics:digital_storage_cell_1k" />的<ItemLink id="ae2:drive" />； 你的主网络， 带一个合成终端； 三个网络共同接触的一个<ItemLink id="ae2federation:switch" />。 Data Energistics的指南里有[太阳能板](data_energistics:items-blocks-machines/6.1_me_solar_panel.md)和[天文观测台](data_energistics:items-blocks-machines/6.12_astronomical_observatories.md)的说明。

<GameScene zoom="5" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/solar_observatory.snbt" />
  <BoxAnnotation color="#5ccd78" min="4 0 1" max="7 2 3">
    前哨： 六块ME太阳能板拼成一个阵列， 放在一根ME线缆和给三个网络供电的能源元件上
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="3 0 0" max="4 1 1">
    你的主网络： 一个合成终端
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 1" max="3 1 2">
    天文台： 天文观测台， 以及一个装着数位化磁盘、存放耀星流的驱动器
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0 1" max="4 1 2">
    一个交换机： 每个面加入它接触的网络
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

联邦界面里会显示这三个网络和它们之间的规则：

<FederationTopology>
  <Network key="outpost" label="前哨" color="#5ccd78" column="0" row="0" details="太阳能板|能源元件" />
  <Network key="main" label="主网络" color="#915dcd" column="1" row="0" details="合成终端" />
  <Network key="observatory" label="天文台" color="#5CA7CD" column="2" row="0" details="天文观测台|数位化磁盘" />
  <Rule user="main" source="observatory" capability="storage" />
  <Energy first="outpost" second="main" />
  <Energy first="main" second="observatory" />
</FederationTopology>

## 搭建

1. **把三个网络接到同一个交换机的各个面上**， 如场景所示。
2. **搭前哨**： 在交换机的面上放一根ME线缆和能源元件， 太阳能板放在它们上面， 拼成一个阵列。 太阳能板只通过底面接入网络， 相邻的太阳能板共享能量， 所以压在线缆和能源元件上的那几块就能带动整个阵列。 每块太阳能板上方都要能看到天空。
3. **让天文观测台上方能看到天空**， 再把数位化磁盘放进天文台网络的驱动器。 耀星流是Data Energistics的一种资源， 存放在数位化磁盘里。
4. **打开ME能量**： 前哨和你的主网络之间， 以及你的主网络和天文台之间。 能量沿着这条链汇在一起， 天文观测台就靠前哨的太阳能板运行。
5. **打开“主网络使用天文台的”存储规则**。 你的主网络的终端里就能看到耀星流。

## 怎样运行

天文观测台只在夜里工作， 也就是每天第13,000到23,000刻， 把4,000 AE/t变成8耀星流/t。 夜里一块太阳能板发1,000 AE/t， 是白天的三分之一， 所以六块足够天文观测台用， 还给网络留了余量。 能源元件是关键： 天文观测台每刻要一次性取走4,000 AE， 而三个网络自己只能存几百AE。 下雨时天文观测台的产量降到四分之一， 打雷时停止工作。

## 试一试

关闭你的主网络和天文台之间的ME能量。 天文观测台熄灭， 终端里的耀星流不再增加， 你的主网络则继续靠前哨运行。 重新打开， 耀星流又开始增加。
