---
navigation:
  parent: examples/index.md
  title: 向装配矩阵下单
  icon: extendedae:assembler_matrix_frame
  position: 70
---

# 向装配矩阵下单

**目标**： 工坊网络用ExtendedAE的装配矩阵合成， 这是一个既保存样板又负责合成的多方块结构。 像[向装配工坊下单](remote-assembly.md)那样， 从主网络订购它的配方。 这一页出现， 是因为安装了ExtendedAE。

**需要**： 带一个已成形的<ItemLink id="extendedae:assembler_matrix_frame" />结构的工坊网络（网络B）； 带合成CPU、合成终端和存储的主网络（网络A）； 每个网络一个<ItemLink id="ae2federation:router" />， 它们之间用<ItemLink id="ae2federation:cable" />相连。

<GameScene zoom="3" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/assembler_matrix.snbt" />
  <BoxAnnotation color="#915dcd" min="9 0 0" max="11 2 1">
    网络A： 合成终端、合成CPU和存储
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="5 0 0" max="9 1 1">
    每个网络一个路由器， 用联邦线缆相连
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="4 3 3">
    网络B： 最小的装配矩阵， 棱上是框架， 面上是墙壁， 内部是一个样板核心和一个合成核心
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 搭建

1. **在网络B上搭好矩阵**， 方法见[ExtendedAE自己的指南](extendedae:epp_intro/assembler_matrix.md)， 再把合成样板放进去。 最小的矩阵长4格、高3格、深3格。 它的框架像其他AE2方块一样接入网络B。
2. **用路由器和联邦线缆连接两个网络**。
3. **在联邦界面里打开“A使用B的”合成规则**。
4. **从网络A下单**。 矩阵的配方会出现在A的可合成列表里， 由A的合成CPU运行任务， 矩阵负责合成。

矩阵像样板供应器一样把样板提供给网络B， 所以联邦也用同样的方式共享它们。 矩阵的设置仍然在网络B上进行。

## 试一试

拆掉矩阵的一块墙壁。 结构散开， 它的配方从A的终端里消失。 把墙壁放回去： 矩阵重新成形， 配方也回来了。
