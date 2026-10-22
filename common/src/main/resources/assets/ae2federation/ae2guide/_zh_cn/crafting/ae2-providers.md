---
navigation:
  parent: index.md
  title: 另一个网络的样板供应器
  icon: ae2:pattern_provider
  position: 310
---

# 另一个网络的样板供应器

假设机器在网络B上： 普通的AE2<ItemLink id="ae2:pattern_provider" />装着样板， 旁边是<ItemLink id="ae2:molecular_assembler" />或加工机器， 按AE2的常规方式布置。 网络A想向它们下单。

1. **连接两个网络**， 用联邦桥或交换机都可以， 参见[入门](../getting-started.md)。
2. **在联邦界面里打开“A使用B的”合成规则**。 不需要存储规则： A的合成CPU用A能看到的东西付账， 这里就是A自己的存储。
3. **打开这对网络的ME能量**。 网络B从此靠网络A的电源运行， 不需要自己的能源元件。
4. **在网络A下单**。 A的终端会把B的样板和A自己的可合成物品列在一起， 照常请求即可。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/native_projection.snbt" />
  <BoxAnnotation color="#915dcd" min="5 1 0" max="6 2 1">
    网络A的合成CPU： 规划并执行任务
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="5 0 0" max="6 1 1">
    网络A的能源元件： 通过ME能量给两个网络供电
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="4.125 0.125 0" max="4.875 0.875 0.2">
    网络A的合成终端： 列出网络B的样板， 可以直接下单
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0.25 0.25" max="3.375 0.75 0.75">
    联邦桥（或交换机）连接两个网络
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="1 0 0" max="2 1 1">
    网络B的AE2样板供应器： 接收网络A的CPU推送的原料
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="1 1 1">
    分子装配室： 产物回到网络A
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

联邦界面里是这样的：

<FederationTopology>
  <Network key="a" label="网络A" color="#915dcd" column="0" row="0" details="合成CPU、终端|存储、能源元件" />
  <Network key="b" label="网络B" color="#5CA7CD" column="1" row="0" details="样板供应器|分子装配室" />
  <Rule user="a" source="b" capability="crafting" />
  <Energy first="a" second="b" />
</FederationTopology>

网络A的合成CPU规划并执行任务， 使用网络A能看到的材料。 它把每一步的原料直接推送给B的样板供应器， 机器照常工作， 产物一进入网络B就回到A的CPU。 网络B不需要合成CPU。

* **网络A需要合成CPU**。 没有CPU时， AE2会照常提示没有可用的CPU。
* **链式需要转出**。 如果A使用B的合成， 而B以“启用（可转出）”使用C的合成， A也能向C的样板供应器下单。
* **优先级、阻挡模式和忙碌的供应器**都和单个网络里一样： A的CPU按AE2自己的规则在样板供应器之间选择。
* **网络B上装了合成卡的标准发信器**不会提供给网络A。
* **存储规则可选**。 再打开“A使用B的”存储规则， A的CPU也能用B的材料付账。
* **剩余物品留在网络B**： 样板没有列出的副产物， 以及在网络A取消的任务的产物。 只有“A使用B的”存储规则打开时， 网络A才能看到并取出它们。
* **任务进行中两个网络断开， 或网络A没有加载时**， 这期间到达的产物先留在网络B的存储里， A的CPU继续等待。 两个网络重新连接后， 网络B会把它们交给这个任务。 如果网络B上有人先用掉了它们， 任务会等到网络B再次存有这么多。

完整的搭建示例见[向装配工坊下单](../examples/remote-assembly.md)。
