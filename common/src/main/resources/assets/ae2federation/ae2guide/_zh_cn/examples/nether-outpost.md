---
navigation:
  parent: examples/index.md
  title: 下界前哨
  icon: ae2federation:federation_p2p_tunnel
  position: 15
---

# 下界前哨

**目标**： 下界里的前哨网络使用你在主世界基地的存储， 并靠基地的电源运行。 量子网络桥在两个维度之间承载一对联邦P2P通道， 前哨仍是独立的网络， 保留自己的频道。 之后前哨还能订购一个用桥接器连到基地的工厂所做的东西， 自己和工厂之间不需要任何规则、线缆或桥接器。

**需要**： 一个带存储和电源的基地网络； 一个带合成终端和合成CPU的前哨网络； 每个网络一个<ItemLink id="ae2federation:switch" />； 一座量子网络桥， 即每端八个<ItemLink id="ae2:quantum_ring" />和一个<ItemLink id="ae2:quantum_link" />， 外加一对<ItemLink id="ae2:quantum_entangled_singularity" />； 两个<ItemLink id="ae2:me_p2p_tunnel" />； 一张<ItemLink id="ae2:memory_card" />； 一个<ItemLink id="ae2:quartz_fiber" />； ME线缆和<ItemLink id="ae2federation:cable" />。 工厂部分还需要： 一个用<ItemLink id="ae2:pattern_provider" />和<ItemLink id="ae2:molecular_assembler" />把原木做成木板的网络， 以及一个<ItemLink id="ae2federation:bridge" />。

主世界这一端：

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/nether_outpost_overworld.snbt" />
  <BoxAnnotation color="#915dcd" min="5 1 0" max="7.625 2 1">
    基地： 驱动器、给这里所有网络供电的能源元件， 以及装桥接器的一段线缆
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="7.625 1.25 0.25" max="8 1.75 0.75">
    基地和工厂之间的桥接器： 第二个联邦域， 由这两个网络组成
  </BoxAnnotation>
  <BoxAnnotation color="#cdc35c" min="8 1 0" max="10 3 1">
    工厂： 一个样板供应器和一台分子装配室， 用自己的线缆， 靠基地的电运行
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="5 2 0" max="6 3 1">
    基地的交换机， 放在驱动器上面
  </BoxAnnotation>
  <BoxAnnotation color="#5ccd78" min="0 0 0" max="4 3 1">
    承载网络： 量子网络桥和通道所在的线缆， 自成一个网络， 没有能源元件
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4.125 1.75 0.125" max="4.875 2 0.875">
    通道输入端： 装在承载网络的线缆上， 正面上方是通往基地交换机的联邦线缆
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4.75 1.375 0.375" max="5 1.625 0.625">
    石英纤维： 把基地的电力传给承载网络， 但不把两个网络连成一个
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

下界这一端：

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/nether_outpost_nether.snbt" />
  <BoxAnnotation color="#5ccd78" min="2 0 0" max="6 3 1">
    承载网络的另一半： 量子网络桥连通后， 它和主世界那一半是同一个网络， 也用那边的电力
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 1.125 0.125" max="2.25 1.875 0.875">
    通道输出端： 正面接着通往前哨交换机的联邦线缆
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="0 1 0" max="1 2 1">
    前哨的交换机
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="2 1 1">
    前哨： 一个合成终端和一个合成CPU， 没有自己的电源
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

在联邦界面里， 基地和前哨同属一个域， 就像两个交换机之间连着联邦线缆。 承载网络不在这个域里。 前哨使用基地的存储， 并与基地共享能量：

<FederationTopology>
  <Network key="base" label="基地" color="#915dcd" column="0" row="0" details="驱动器|能源元件" />
  <Network key="outpost" label="前哨" color="#5CA7CD" column="1" row="0" details="合成终端|合成CPU" />
  <Rule user="outpost" source="base" capability="storage" />
  <Energy first="base" second="outpost" />
</FederationTopology>

## 搭建

1. **搭建量子网络桥**： 两个维度各放一半， 每个链接仓里放这对奇点中的一个， 参见AE2的[量子网络桥](ae2:items-blocks-machines/quantum_bridge.md)。 桥的线缆自成一个网络， 即承载网络。 不要让它碰到基地或前哨： 否则量子网络桥会把一个已经存在的另一半并入那个网络， 联邦会暂停这个网络的规则（“合并待定”）。
2. **用基地给承载网络供电**： 在承载网络的线缆和基地之间放一个石英纤维。 下界那一半不需要能源元件： 量子网络桥连通后， 基地的电力就能到达那里。
3. **放置通道**。 在两端承载网络的线缆上各放一个ME P2P通道， 手持联邦线缆分别右键它们， 把它们变成[联邦P2P通道](../items/federation_p2p_tunnel.md)。 然后用内存卡配对： 潜行右键主世界的通道， 再右键下界的通道。
4. **把每个网络接到它的交换机上， 再用联邦线缆把每个交换机接到它那一端通道的正面**。
5. **打开联邦界面**： 右键任意一个交换机， 选中基地和前哨之间的连线。 **在“前哨使用基地的”下打开存储规则**， 同时打开这对网络的**ME能量**： 前哨从此跨越维度靠基地的电源运行。
6. **检查**。 打开前哨的终端： 基地的物品都列在里面。 这条规则和[共享仓库](shared-warehouse.md)里的相同。

## 让两端保持加载

和AE2自己的量子网络桥一样， 这条连接需要两端都处于加载状态， 比如用一个<ItemLink id="ae2:spatial_anchor" />。 如果前哨所在的区块被卸载， 前哨会立刻离开这个域， 失去基地的存储。 区块重新加载后， 它会自动回来。

## 试一试

从主世界的链接仓里取出奇点。 前哨的终端变暗、 变空： 通道断开了， 基地的存储和电力也随之断开。 基地自己照常工作。 把奇点放回去， 前哨又拥有了这两样。

## 向基地的工厂下单

基地还可以是前哨通往工厂的路。 这里的工厂是基地旁边一个独立的网络： 一个样板供应器和一台分子装配室， 把原木做成木板， 用桥接器连到基地。 桥接器形成第二个联邦域， 由基地和工厂组成。 前哨不在这个域里， 哪个界面里都没有“前哨使用工厂的”规则。 基地的规则开启转发后， 工厂的配方照样会转给前哨， 和[跨域联动](../across-domains.md)里一样。

在交换机的联邦界面里显示相连的域时：

<FederationTopology>
  <Network key="outpost" label="前哨" color="#5CA7CD" column="0" row="0" details="合成终端|合成CPU" />
  <Network key="base" label="基地" color="#915dcd" column="1" row="0" details="驱动器|能源元件" />
  <Network key="factory" label="工厂" color="#cdc35c" column="2" row="0" details="样板供应器|分子装配室" />
  <Domain key="here" label="本域" networks="outpost,base" opened="true" />
  <Domain key="bridge" label="域 3F1C" networks="base,factory" />
  <Rule user="outpost" source="base" capability="crafting" />
  <Rule user="outpost" source="base" capability="storage" />
  <Rule user="base" source="factory" capability="crafting" state="reexport" />
  <Energy first="outpost" second="base" />
  <Energy first="base" second="factory" />
</FederationTopology>

### 搭建

1. **用桥接器把工厂连到基地**： 把桥接器装在基地的一段线缆上， 让它的外侧接触工厂的线缆， 如场景所示。 工厂的线缆用自己的颜色， 这样两个网络的线缆永远不会连到一起。
2. **右键桥接器**， 打开“基地使用工厂的”合成规则， 再往前切一次， 切到开启并转发。 这里的工厂不存放东西， 所以不需要存储规则。 如果以后给它加上存储， 再把“基地使用工厂的”存储规则也打开并转发， 前哨就能看到它的库存和任务的剩余物品。
3. **在同一个界面里打开基地和工厂之间的ME能量**。 工厂从此和前哨一样靠基地的能源元件运行： 能量跨两个联邦域汇到一起， 不需要转发。
4. **右键任意一个交换机**， 打开“前哨使用基地的”合成规则， 开启就够了。 这个方向在第一部分打开的存储规则保持打开： 前哨要通过它用基地的原木付账。
5. **在前哨的合成终端下单木板**。 工厂的配方列在前哨的可合成物品里。

### 订单怎样运行

前哨自己的合成CPU执行任务。 前哨没有自己的存储， 所以CPU从基地的驱动器取出原木， 直接发给工厂的样板供应器。 木板直接回到CPU， CPU把它们存进基地的驱动器， 前哨的终端就会列出它们。 基地不需要CPU： 它的规则只决定谁能用到谁。

### 联邦界面里

交换机的界面打开时是**当前域**： 基地和前哨， 以及它们之间的规则。 点击另一个范围按钮， 说明文字变成**含所有相连的域（只读）**： 桥接器的联邦域放在自己的底板上加进来， 带着工厂和“基地使用工厂的”合成规则， 用转发的颜色显示。 它在这里不能修改， 要到桥接器上编辑。

### 试一试

在桥接器的界面里把“基地使用工厂的”合成规则切回开启。 木板从前哨的可合成物品里消失， 而基地仍保留着工厂的配方。 再切到转发， 它们就回来了。
