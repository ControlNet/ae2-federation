---
navigation:
  parent: examples/index.md
  title: 共享的电力库
  icon: appflux:flux_accessor
  position: 45
---

# 共享的电力库

**目标**： 把FE存在一个电力库网络里， 让另一个网络上的Mekanism粉碎机用它运行。 工坊网络自己没有存储， 也没有能源元件。 这一页出现， 是因为安装了Applied Flux和Mekanism。

**需要**： 一个电力库网络， ME驱动器里装着<ItemLink id="appflux:fe_1k_cell" />， 再加一个能源元件； 一个工坊网络， 带一个终端和一个<ItemLink id="appflux:flux_accessor" />； 一台<ItemLink id="mekanism:crusher" />； 两个网络共同接触的一个<ItemLink id="ae2federation:router" />。 FE怎样存进元件见[Applied Flux的指南](appflux:appflux/flux_cells.md)。

<GameScene zoom="5" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/power_bank.snbt" />
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="3 2 1">
    电力库： ME驱动器里的ME能源存储元件， 以及给两个网络供电的能源元件
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="4 0 0" max="7 1 1">
    工坊： 一个终端、一个通量访问点和粉碎机， 自己没有存储， 也没有能源元件
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="5 0 0" max="6 1 1">
    通量访问点： 把它所在网络能取到的FE送进它接触的机器
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0 0" max="4 1 1">
    一个路由器： 每个面加入它接触的网络
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

联邦界面里会显示这两个网络和它们之间的规则：

<FederationTopology>
  <Network key="workshop" label="工坊" color="#915dcd" column="0" row="0" details="通量访问点|粉碎机" />
  <Network key="bank" label="电力库" color="#5CA7CD" column="1" row="0" details="ME能源存储元件|能源元件" />
  <Rule user="workshop" source="bank" capability="storage" />
  <Energy first="workshop" second="bank" />
</FederationTopology>

## 搭建

1. **把两个网络接到同一个路由器的两个面上**， 如场景所示。
2. **打开“工坊使用电力库的”存储规则**。 电力库的FE就会和它存的其他东西一起出现在工坊的终端里。
3. **打开两个网络之间的ME能量**， 工坊靠电力库的能源元件运行。
4. **设置粉碎机的各面**： 在它的侧面配置里， 能量方面把朝向通量访问点的一面设为输入； 物品方面把顶面设为输入， 底面设为输出。
5. **给粉碎机放圆石**。 它用电力库的FE把圆石粉碎成沙砾， 这些FE由通量访问点送过去。

通量访问点从它所在网络能取到的任何存储里取FE， 所以它通过这条存储规则取到电力库的FE。 任何接受FE的机器都可以这样用。

## 试一试

在粉碎机还有圆石时关闭“工坊使用电力库的”存储规则。 电力库的FE从工坊的终端消失， 通量访问点不再送电， 粉碎机用完自己存的能量就停下。
