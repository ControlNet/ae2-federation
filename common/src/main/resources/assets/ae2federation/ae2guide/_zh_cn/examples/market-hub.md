---
navigation:
  parent: examples/index.md
  title: 市场枢纽
  icon: ae2federation:switch
  position: 25
---

# 市场枢纽

**目标**： 几个基地通过一个市场网络交易。 每个基地只和市场之间有规则， 市场把它的供应方能做的东西转发出去。 再给市场接一个供应方， 每个基地都能向它下单， 不用自己再加规则。

**需要**： 带合成CPU、合成终端、存储和电源的基地网络； 带存储的市场网络； 一个供应方网络， 这里是锯木厂， 它的<ItemLink id="ae2:pattern_provider" />和<ItemLink id="ae2:molecular_assembler" />把原木做成木板； 它们共同接触的一个<ItemLink id="ae2federation:switch" />。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/market_hub.snbt" />
  <BoxAnnotation color="#915dcd" min="4 0 0" max="7 2 1">
    基地： 合成终端、合成CPU、存储， 以及给三个网络供电的能源元件
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="2 2 0" max="5 3 1">
    市场： 只有存储， 没有CPU也没有样板
  </BoxAnnotation>
  <BoxAnnotation color="#5ccd78" min="0 0 0" max="3 2 1">
    锯木厂： 样板供应器， 旁边是分子装配室
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0 0" max="4 1 1">
    一个交换机： 每个面加入它接触的网络
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

联邦界面里会显示这三个网络和它们之间的规则：

<FederationTopology>
  <Network key="district" label="基地" color="#915dcd" column="0" row="0" details="合成CPU、终端|能源元件" />
  <Network key="market" label="市场" color="#5CA7CD" column="1" row="0" details="驱动器" />
  <Network key="sawmill" label="锯木厂" color="#5ccd78" column="2" row="0" details="样板供应器|分子装配室" />
  <Rule user="district" source="market" capability="crafting" />
  <Rule user="district" source="market" capability="storage" />
  <Rule user="market" source="sawmill" capability="crafting" state="reexport" />
  <Rule user="market" source="sawmill" capability="storage" />
  <Energy first="district" second="market" />
  <Energy first="market" second="sawmill" />
</FederationTopology>

## 搭建

1. **把三个网络接到同一个交换机的各个面上**， 如场景所示。
2. **打开“基地使用市场的”合成规则**。 存储规则会随之打开。
3. **打开“市场使用锯木厂的”合成规则**， 再往前切一次， 切到开启并转发。 市场从此把锯木厂的配方转发给每个使用市场合成的网络。
4. **打开基地和市场之间、 市场和锯木厂之间的ME能量**。 能量沿着这条链汇到一起， 三个网络都靠基地的能源元件运行。
5. **在基地下单木板**。 锯木厂的配方列在基地的可合成物品里。

## 任务怎样运行

基地的合成CPU执行任务。 它把原木直接发给锯木厂的样板供应器， 木板也直接回到它这里。 市场不参与： 它不需要CPU， 也没有东西经过它。 规则只决定谁能用到谁。

## 试一试

把“市场使用锯木厂的”合成规则切回开启。 木板从基地的可合成物品里消失， 而市场自己仍然可以下单。 再切到转发， 它们就回来了。
