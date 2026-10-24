---
navigation:
  parent: examples/index.md
  title: 奇点造币厂
  icon: neoecoprototype:simplify_singularity_cell
  position: 585
---

# 奇点造币厂

**目标**： 在一个网络上产出奇点， 在其他网络上取用。 Neo ECO Prototype的L1奇点元件每30秒凭空产出120个奇点。 它放在一个造币厂网络上， 这个网络唯一的电源是一个盈能超导接口； 两个街区网络取用它的奇点， 也靠它的电源运行。 这一页出现， 是因为安装了Neo ECO Prototype。

**需要**：

* 造币厂上： 一个已成形的L1存储系统， 包括主机（<ItemLink id="neoecoprototype:simplify_storage_controller" />）、驱动器（<ItemLink id="neoecoprototype:simplify_drive" />）和接口； 一个<ItemLink id="neoecoprototype:simplify_singularity_cell" />， 装在其中一个驱动器里； 造币厂线缆上的一个<ItemLink id="neoecoprototype:superconductive_interface" />。 除了存储系统自带的能量元件， 不需要别的能源元件。
* 两个街区网络， 各有一个终端。 它们不需要自己的电源。
* 一个<ItemLink id="ae2federation:switch" />。

<GameScene zoom="3" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/singularity_mint.snbt" />
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="5 3 2">
    造币厂： 最小的L1存储系统， 奇点元件装在中间的驱动器里
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="5 1 1" max="7 3 2">
    造币厂的线缆和盈能超导接口： 4,000 AE/t， 这里唯一的电源
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="7 1 1" max="8 2 2">
    交换机： 每个网络占一个面
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="8 1 1" max="9 2 2">
    街区A： 一个终端， 靠造币厂的电源运行
  </BoxAnnotation>
  <BoxAnnotation color="#5ccd78" min="7 0 1" max="8 1 2">
    街区B： 一个终端， 靠造币厂的电源运行
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

在联邦界面里， 每个街区使用造币厂的存储， 并与它共享能量：

<FederationTopology>
  <Network key="a" label="街区A" color="#915dcd" column="0" row="0" details="终端" />
  <Network key="m" label="造币厂" color="#5CA7CD" column="1" row="0" details="L1存储系统|奇点元件|超导接口" />
  <Network key="b" label="街区B" color="#5ccd78" column="2" row="0" details="终端" />
  <Rule user="a" source="m" capability="storage" />
  <Rule user="b" source="m" capability="storage" />
  <Energy first="a" second="m" />
  <Energy first="m" second="b" />
</FederationTopology>

## 搭建

1. **搭建L1存储系统**， 做法见Neo ECO Prototype指南中的[L1存储系统](neoecoprototype:neoecoprototype_intro/storage_system.md)。 最小的一台长5格、高3格、深2格。 把奇点元件放进其中一个驱动器： 驱动器第一次读取它时， 它开始计数， 参见[L1奇点元件](neoecoprototype:neoecoprototype_intro/singularity_cell.md)。
2. **把超导接口放在造币厂的线缆上**， 挨着存储系统的接口。 它不消耗任何燃料， 产出4,000 AE/t， 参见[盈能超导接口](neoecoprototype:neoecoprototype_intro/superconductive_interface.md)。
3. **把造币厂和两个街区分别接到交换机的不同面上**。
4. **在“街区A使用造币厂的”和“街区B使用造币厂的”下打开存储规则**， 再打开这两对网络的**ME能量**： 街区从此靠超导接口运行。
5. **检查**。 30秒后， 两个街区的终端里都列出元件的第一批120个奇点。 它们是同一批120个： 街区A取走多少， 街区B就少看到多少。 之后每30秒， 两边同时多出120个。

两个街区共用一份库存， 而不是各自拥有一个元件。 元件在造币厂上， 所以哪个街区都不需要自己的存储系统或电源， 就能从中取用。

## 试一试

把奇点元件从驱动器里取出来。 奇点从两个街区的终端里消失。 把它放回去： 奇点又回来了。
