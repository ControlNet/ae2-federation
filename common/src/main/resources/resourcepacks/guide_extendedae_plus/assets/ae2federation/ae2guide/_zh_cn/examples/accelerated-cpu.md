---
navigation:
  parent: examples/index.md
  title: 加速自己的CPU
  icon: extendedae_plus:4x_crafting_accelerator
  position: 75
---

# 加速自己的CPU

**目标**： 像[向装配工坊下单](remote-assembly.md)那样， 向另一个网络上的装配工坊下单， 并在自己的合成CPU里装上ExtendedAE-Plus的4x并行处理单元。 这一页出现， 是因为安装了ExtendedAE-Plus。

**需要**： 带<ItemLink id="ae2:pattern_provider" />和<ItemLink id="ae2:molecular_assembler" />的工坊网络（网络B）； 带合成终端、存储， 以及由<ItemLink id="ae2:1k_crafting_storage" />和<ItemLink id="extendedae_plus:4x_crafting_accelerator" />组成的合成CPU的主网络（网络A）； 每个网络一个<ItemLink id="ae2federation:router" />， 它们之间用<ItemLink id="ae2federation:cable" />相连。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/accelerated_cpu.snbt" />
  <BoxAnnotation color="#915dcd" min="7 0 0" max="9 3 1">
    网络A： 合成终端、存储， 以及合成存储器上装着4x并行处理单元的合成CPU
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0 0" max="7 1 1">
    每个网络一个路由器， 用联邦线缆相连
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="3 3 1">
    网络B： 装着样板的样板供应器， 旁边是分子装配室
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 搭建

1. **搭建网络A的合成CPU**， 把这个处理单元当作其中一个方块。 它算作4个并行处理单元， 见[ExtendedAE-Plus自己的指南](extendedae_plus:introduction/devices/crafting_accelerator.md)。
2. **用路由器和联邦线缆连接两个网络**。
3. **在联邦界面里打开“A使用B的”合成规则**。
4. **从网络A下单**。 CPU运行任务时， 它的状态会显示4个并行处理单元。

## 处理单元该放在哪

任务总是在下单网络的CPU上运行， 所以要升级的是这个CPU。 并行处理单元让CPU同时开始更多的合成步骤， 但不会让网络B的机器变快。 如果B只有几台装配室， 它们才是瓶颈， 在B上多加装配室比在A上多加处理单元更有用。

## 试一试

把处理单元搬到网络B， 装在那里的一个合成存储器上。 A的下一个订单仍然在A自己的CPU上运行， 只是不再有并行处理单元： 这个处理单元只加速B的CPU， 而B的CPU不参与A的订单。
