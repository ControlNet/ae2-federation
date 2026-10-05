---
navigation:
  parent: examples/index.md
  title: 向物质构筑井下单
  icon: molecularmanipulator:matter_fabrication_pattern_assembly
  position: 90
---

# 向物质构筑井下单

**目标**： 把OmniSequence的物质构筑井留在它自己的生产网络（网络B）上， 从你的主网络（网络A）向它的产物下单， 而不合并网络。 这一页出现， 是因为安装了OmniSequence: Transfinite。

**需要**： 带一座已成形物质构筑井的网络B， 构筑井的控制器（<ItemLink id="molecularmanipulator:matter_fabrication_controller" />）接在B的线缆上， 它的一个服务位置上装着<ItemLink id="molecularmanipulator:matter_fabrication_pattern_assembly" />； 带合成CPU、合成终端、存储和电源的主网络（网络A）； 一个<ItemLink id="ae2federation:router" />。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/matter_fabrication_well.snbt" />
  <BoxAnnotation color="#915dcd" min="2 1 0" max="5 3 1">
    网络A： 合成终端、合成CPU、存储， 以及给两个网络供电的能源元件
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 2 1" max="4 3 2">
    路由器： 每个网络占一个面
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="3 2 2" max="5 3 8">
    网络B的线缆， 沿着服务通道接到控制器的正面
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="5 2 4" max="6 3 5">
    装在通道前沿台阶服务位置上的样板总成， 里面放着处理样板
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 4" max="7 4 10">
    已成形构筑井在控制器周围的一角； 整座构筑井有41×41格
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

联邦界面里会显示这两个网络和它们之间的规则：

<FederationTopology>
  <Network key="a" label="网络A" color="#915dcd" column="0" row="0" details="合成CPU、终端|存储、能源元件" />
  <Network key="b" label="网络B" color="#5CA7CD" column="1" row="0" details="物质构筑井|样板总成" />
  <Rule user="a" source="b" capability="crafting" />
  <Rule user="a" source="b" capability="storage" />
  <Energy first="a" second="b" />
</FederationTopology>

## 搭建

1. **在网络B上搭好构筑井**， 方法见[OmniSequence自己的指南](molecularmanipulator:items-blocks-machines/matter_fabrication_well.md)， 控制器接在B的线缆上。
2. **在构筑井的一个服务位置上装一台样板总成**。 制作样板总成需要这座构筑井完成一阶研究， 见[构筑研究](molecularmanipulator:items-blocks-machines/matter_fabrication_research.md)。
3. **在样板总成里放入一个处理样板**， 它的输入、 输出和数量都必须与一个构筑井配方完全一致， 见[物质构筑井样板总成](molecularmanipulator:items-blocks-machines/matter_fabrication_pattern_assembly.md)。 4个下界石英加4个骨粉做成8个方解石， 这个配方不需要研究。
4. **把网络A和网络B分别接到路由器的不同面上**。
5. **在联邦界面里， 在“A使用B的”下打开合成规则**， 再打开这对网络的ME能量： 构筑井从此靠网络A的电源运行。
6. **从网络A下单方解石**。 A的合成CPU把石英和骨粉送到样板总成， 构筑井做出方解石， 样板总成把它放进网络B， 再从那里回到A的CPU。

研究属于网络B上构筑井的控制器。 规则共享的是B的样板总成已经提供的配方； 它不会替网络A做任何研究。

## 试一试

拆掉构筑井的一块外壳。 构筑井散开， 方解石从A可合成的物品里消失。 把外壳放回去： 构筑井重新成形， 方解石也回来了。
