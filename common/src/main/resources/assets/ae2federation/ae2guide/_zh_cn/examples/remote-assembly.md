---
navigation:
  parent: examples/index.md
  title: 向装配工坊下单
  icon: ae2:molecular_assembler
  position: 20
---

# 向装配工坊下单

**目标**： 一个工坊网络已经有装着样板的<ItemLink id="ae2:pattern_provider" />和<ItemLink id="ae2:molecular_assembler" />。 在不搬动样板、不合并网络的前提下， 从你的主网络向它下单。

**需要**： 按AE2常规方式搭好的工坊网络（网络B）， 它不需要自己的电源； 带合成CPU、合成终端、存储和电源的主网络（网络A）； 每个网络一个<ItemLink id="ae2federation:switch" />， 中间用<ItemLink id="ae2federation:cable" />相连， 两个网络挨着的话也可以用一个<ItemLink id="ae2federation:bridge" />。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/remote_assembly.snbt" />
  <BoxAnnotation color="#915dcd" min="7 0 0" max="9 2 1">
    网络A： 合成终端、合成CPU、存储， 以及给两个网络供电的能源元件
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0 0" max="7 1 1">
    每个网络一个交换机， 用联邦线缆相连
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="3 3 1">
    网络B： 装着样板的样板供应器， 旁边是分子装配室
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

联邦界面里会显示这两个网络和它们之间的规则：

<FederationTopology>
  <Network key="a" label="网络A" color="#915dcd" column="0" row="0" details="合成CPU、终端|存储、能源元件" />
  <Network key="b" label="网络B" color="#5CA7CD" column="1" row="0" details="样板供应器|分子装配室" />
  <Rule user="a" source="b" capability="crafting" />
  <Rule user="a" source="b" capability="storage" />
  <Energy first="a" second="b" />
</FederationTopology>

## 搭建

1. **工坊保持原样**。 样板供应器里的样板不动， 装配室还在它们旁边。
2. **用交换机和联邦线缆连接两个网络**。
3. **在联邦界面里打开“A使用B的”合成规则**。 它会同时打开同方向的存储规则， 因为A的CPU要从A能看到的存储里取原料， 而现在A能看到B的存储。
4. **打开这对网络的ME能量**。 两个网络从此共用一个能量池， 网络B靠网络A的电源运行。
5. **在网络A下单**。 B的配方和A自己的可合成物品列在一起， 照常请求即可。

## 任务怎样运行

网络A的合成CPU规划并执行任务。 它把每一步的原料发给B的样板供应器， B的装配室照常合成， 产物一进入网络B就回到A的CPU。 网络B不需要自己的合成CPU， 它的CPU也不会共享给A。

机器所在的网络已经有样板时， 用这种方式。 如果想把样板留在自己的网络上， 再发给别处的机器， 请改用联邦样板供应器， 参见[外包熔炉](endpoint-furnaces.md)。

## 试一试

关闭合成规则： B的配方从A的终端里消失， A不能再下单。 拆掉A的合成CPU再下单： AE2会提示没有可用的CPU， 和普通网络一样， 因为CPU永远属于下单的一方。
