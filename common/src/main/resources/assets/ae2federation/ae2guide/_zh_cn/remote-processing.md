---
navigation:
  parent: index.md
  title: 远程合成
  icon: ae2federation:processing_endpoint
  position: 30
---

# 远程合成

让另一个网络的机器替你完成合成任务， 有两种方式：

* **使用另一个网络的AE2样板供应器**。 打开合成规则后， 你的终端会列出另一个网络的样板， 就像那些样板供应器装在你自己的网络上一样。 另一个网络保持它原本的AE2布置即可。
* **通过联邦样板供应器发送样板**。 处理样板放在你自己的联邦样板供应器里， 由它发给你选定的处理端点。 这种方式不需要规则， 也不共享存储。

## 使用另一个网络的AE2样板供应器

假设机器在网络B上： 普通的AE2<ItemLink id="ae2:pattern_provider" />装着样板， 旁边是<ItemLink id="ae2:molecular_assembler" />或加工机器， 按AE2的常规方式布置。 网络A想向它们下单。

1. **连接两个网络**， 用桥接器或路由器都可以， 参见[入门](getting-started.md)。
2. **在联邦界面里打开“A使用B的”合成规则**。 它会同时打开同方向的存储规则， 因为A的合成CPU要从A能看到的存储里取原料。
3. **打开这对网络的ME能量**。 网络B从此靠网络A的电源运行， 不需要自己的能源元件。
4. **在网络A下单**。 A的终端会把B的样板和A自己的可合成物品列在一起， 照常请求即可。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="assets/native_projection.snbt" />
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
    桥接器（或路由器）连接两个网络
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
  <Rule user="a" source="b" capability="storage" />
  <Energy first="a" second="b" />
</FederationTopology>

网络A的合成CPU规划并执行任务， 使用网络A能看到的材料， 其中包括网络B的存储。 它把每一步的原料推送给B的样板供应器， 机器照常工作， 产物一进入网络B就回到A的CPU。 网络B不需要合成CPU。

* **网络A需要合成CPU**。 没有CPU时， AE2会照常提示没有可用的CPU。
* **链式需要转出**。 如果A使用B的合成， 而B以“启用（可转出）”使用C的合成， A也能向C的样板供应器下单。
* **优先级、阻挡模式和忙碌的供应器**都和单个网络里一样： A的CPU按AE2自己的规则在样板供应器之间选择。
* **网络B上装了合成卡的标准发信器**不会提供给网络A。
* **剩余物品留在网络B**： 样板没有列出的副产物， 以及在网络A取消的任务的产物。 网络A仍可以通过存储规则看到它们。
* **任务进行中两个网络断开时**， 这期间到达的产物留在网络B， A的CPU会一直等待； 请在网络A取消这个任务。

完整的搭建示例见[向装配工坊下单](examples/remote-assembly.md)。

## 联邦样板供应器和处理端点

一个网络上的<ItemLink id="ae2federation:pattern_provider" />可以通过放在机器旁的<ItemLink id="ae2federation:processing_endpoint" />， 把处理样板发给属于另一个网络的机器。 产物会回到供应器所在的网络。 加工不需要在联邦界面里设置规则。

<Row>
  <RecipeFor id="ae2federation:pattern_provider" />
  <RecipeFor id="ae2federation:processing_endpoint" />
</Row>

### 搭建

1. **机器所在的网络**。 按AE2的常规做法搭一个小ME网络（加工子网络）， 用它的存储给机器供料， 例如让存储总线或接口对着机器。 放置端点， 让它的一个ME面接入这个子网络。 子网络不能是供应器自己的网络。 供应器使用端点期间， 端点会用供应器所在网络的电给子网络供电， 所以子网络不需要自己的电源。
2. **两者的前面都朝向联邦一侧**。 这两个方块各有一个联邦面， 就是它们的前面； 放置时前面朝向你点击的方块。 点击联邦线缆（或路由器）来放置， 或者让两者的前面直接相对。 供应器的其他五个面像普通样板供应器一样接入它自己的ME网络， 并在那里占用一个频道。
3. **放入样板**。 右键供应器， 把编码好的处理样板放进它的九个槽位。
4. **映射每个样板**。 在接线图中把样板拖到端点上， 或者先点样板再点端点。 一个样板可以映射到多个端点， 一个端点也可以接收多个样板。 只有映射过的样板才能被请求。

一个小例子： 供应器所在的网络通过另一个子网络上的熔炉加工。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="assets/remote_processing.snbt" />
  <BoxAnnotation color="#915dcd" min="5 0 0" max="7 2 1">
    供应器所在的网络
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4 0 0" max="5 1 1">
    联邦样板供应器： 前面接联邦线缆， 背面接自己的网络
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 0.3 0.3" max="4 0.7 0.7">
    联邦线缆
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="1 0 0" max="2 1 1">
    处理端点： 前面接联邦线缆， 顶面接机器子网络
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="1 1 0" max="2 3 1">
    加工子网络， 由端点供电
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="0.125 2 0.125" max="0.875 2.3 0.875">
    存储总线： 子网络的存储， 原料直接进入熔炉
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="0 0 0" max="1 1 1">
    漏斗： 把产物推入端点的侧面， 而不是前面
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

在联邦界面里， 端点挂在供应器所在的网络下面， 它的子网络用这个网络的电运行：

<FederationTopology>
  <Network key="main" label="供应器所在的网络" color="#915dcd" column="0" row="0" details="联邦样板供应器|存储、能源元件" />
  <Endpoint key="furnace" label="端点 · 熔炉" owner="main" energy="true" details="熔炉子网络" />
</FederationTopology>

供应器所在的网络发起合成时， 原料会进入子网络的ME存储， 再从那里送到机器。 机器（或管道）必须把产物推入端点前面以外的某个面。 产物先进入供应器中对应这个端点的回流缓冲， 再进入供应器所在的网络。 留在子网络存储里的产物不会返回。 包含燃料、阻挡模式和第二座熔炉的完整搭建见[外包熔炉](examples/endpoint-furnaces.md)。

### 一个端点只属于一个供应器

端点同一时间只属于一个供应器。 映射样板时会占用端点； 其他供应器会把它显示为虚线且只读， 往它上面拖样板会被拒绝， 并显示它的所有者。 要把端点交给别的供应器， 先在拥有它的供应器里删除映射并释放它。 释放要等到没有正在发送的内容、并且回流缓冲已清空。

同一个供应器映射的两个端点必须位于不同的子网络。

### 本地模式

端点也可以为另一个网络上的普通样板供应器服务： 把AE2的<ItemLink id="ae2:pattern_provider" />贴在端点的前面即可， 方块或装在线缆上的面板都可以。 附属模组的样板供应器也一样。 此时端点工作在本地模式， 在移走这个样板供应器之前不能接收联邦样板。
