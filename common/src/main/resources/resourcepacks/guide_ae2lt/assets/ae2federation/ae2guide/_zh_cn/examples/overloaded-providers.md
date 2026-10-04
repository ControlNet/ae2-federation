---
navigation:
  parent: examples/index.md
  title: 升级工坊的供应器
  icon: ae2lt:overloaded_pattern_provider
  position: 72
---

# 升级工坊的供应器

**目标**： [向装配工坊下单](remote-assembly.md)里的工坊样板槽不够用了。 把它的样板供应器升级成AE2 Lightning Tech的过载样板供应器， 每台有36个样板槽， 同时主网络照常向它下单。 这一页出现， 是因为安装了AE2 Lightning Tech。

**需要**： 带<ItemLink id="ae2:pattern_provider" />、旁边是<ItemLink id="ae2:molecular_assembler" />的工坊网络（网络B）； 每台供应器一个<ItemLink id="ae2lt:overloaded_pattern_provider_upgrade" />； 带合成CPU、合成终端和存储的主网络（网络A）； 每个网络一个<ItemLink id="ae2federation:router" />， 它们之间用<ItemLink id="ae2federation:cable" />相连。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/overloaded_providers.snbt" />
  <BoxAnnotation color="#915dcd" min="7 0 0" max="9 2 1">
    网络A： 合成终端、合成CPU和存储
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0 0" max="7 1 1">
    每个网络一个路由器， 用联邦线缆相连
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="3 3 1">
    网络B： 升级后装着样板的过载样板供应器， 旁边是分子装配室
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 搭建

1. **从装配工坊的例子开始**： 两个网络已经相连， “A使用B的”合成规则已经打开。
2. **对B的每台样板供应器使用过载样板供应器升级**。 每台都会变成过载样板供应器， 并保留原来的样板和设置， 见[AE2 Lightning Tech自己的指南](ae2lt:overloaded-network/overloaded-pattern-provider.md)。
3. **在新的槽位里放入更多样板**。 它们的配方和其他配方一样出现在A的可合成列表里。
4. **像之前一样从网络A下单**。

过载样板供应器像AE2样板供应器一样把样板提供给网络B， 所以联邦也用同样的方式共享它们。 这个例子使用它的普通模式， 把材料推入旁边的装配室。

## 试一试

从网络A下单， 然后升级B的一台供应器。 A的终端仍然列出它的配方， 下一个订单经过这台过载样板供应器完成。
