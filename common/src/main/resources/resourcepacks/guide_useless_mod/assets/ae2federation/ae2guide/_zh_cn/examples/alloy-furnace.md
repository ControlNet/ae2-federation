---
navigation:
  parent: examples/index.md
  title: 隔着两个桥接器的万象合金炉
  icon: useless_mod:advanced_alloy_furnace_block
  position: 71
---

# 隔着两个桥接器的万象合金炉

**目标**： 向一个工坊里的UselessMod万象合金炉下单， 工坊离你的基地隔着两个桥接器。 工坊和基地各有一个通往贸易站的桥接器， 只有贸易站和工坊之间有规则： 贸易站以转发的方式把合金炉的配方传过来。 合金炉把合成样板放在自己的样板槽里， 并亲自完成合成， 不需要分子装配室。 这一页出现， 是因为安装了UselessMod。

**需要**：

* 工坊网络： 一台装在ME线缆上的<ItemLink id="useless_mod:advanced_alloy_furnace_block" />， 以及存放工坊自己库存的驱动器； 没有自己的电源。
* 贸易站网络： 一段ME线缆， 别的什么都不要。
* 你的基地网络： 合成CPU、合成终端、存储和电源。
* 两个<ItemLink id="ae2federation:bridge" />： 一个装在贸易站的线缆上、接触工坊的线缆， 另一个装在基地的线缆上、接触贸易站的线缆。

<GameScene zoom="3" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/alloy_furnace.snbt" />
  <BoxAnnotation color="#915dcd" min="4.375 0 0" max="7 2 1">
    你的基地： 合成终端、合成CPU、存储， 以及给三个网络供电的能源元件
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4 0.25 0.25" max="4.375 0.75 0.75">
    基地的桥接器： 装在基地的线缆上， 外侧接触贸易站的线缆
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="2.375 0 0" max="4 1 1">
    贸易站： 只有线缆， 没有自己的存储、 CPU和电源
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 0.25 0.25" max="2.375 0.75 0.75">
    贸易站通往工坊的桥接器： 装在贸易站的线缆上， 外侧接触工坊的线缆
  </BoxAnnotation>
  <BoxAnnotation color="#5ccd78" min="0 0 0" max="2 2 1">
    工坊： 装着合成样板的万象合金炉， 以及存放工坊自己库存的驱动器
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

每个桥接器和它连接的两个网络单独组成一个联邦域。 右键基地的桥接器： 界面显示本域， 也就是基地和贸易站。 在范围按钮上选“含所有相连的域（只读）”， 贸易站和工坊所在的域就会在单独的底板上加进来， 只读：

<FederationTopology>
  <Network key="base" label="基地" color="#915dcd" column="0" row="0" details="合成CPU、终端|存储、能源元件" />
  <Network key="post" label="贸易站" color="#5CA7CD" column="1" row="0" details="只有线缆" />
  <Network key="workshop" label="工坊" color="#5ccd78" column="2" row="0" details="万象合金炉|驱动器" />
  <Rule user="base" source="post" capability="crafting" />
  <Rule user="base" source="post" capability="storage" />
  <Rule user="post" source="workshop" capability="crafting" state="reexport" />
  <Rule user="post" source="workshop" capability="storage" />
  <Energy first="base" second="post" />
  <Energy first="post" second="workshop" />
  <Domain key="d1" label="本域" networks="base,post" opened="true" />
  <Domain key="d2" label="域 5E07" networks="post,workshop" />
</FederationTopology>

## 搭建

1. **把合金炉接到工坊的线缆上**， 再把合成样板放进它的样板槽。 它和其他AE2设备一样需要一个频道。
2. **把贸易站接到工坊**： 在贸易站的线缆上装一个桥接器， 让它的外侧接触工坊的线缆。 右键它， 打开“贸易站使用工坊的”合成规则， 再往前切一次， 切到开启并转发。 它的存储规则会随之打开， 停在普通的开启。 同时打开这对网络的**ME能量**。
3. **把基地接到贸易站**： 在基地的线缆上装一个桥接器， 让它的外侧接触贸易站的线缆。 右键它： 界面里只有基地和贸易站。 打开“基地使用贸易站的”合成规则（存储规则会随之打开）和ME能量。 能量沿着链汇到一起， 三个网络都靠基地的能源元件运行， 合金炉合成时不需要自己的FE。
4. **从基地下单**。 合金炉的配方列在基地的可合成物品里。 由基地的合成CPU执行任务： 它把材料直接发给合金炉， 合金炉做出的东西也直接回到它这里， 进入基地的存储。 贸易站不参与， 也不需要CPU。

这就是跨越两个联邦域的[市场枢纽](market-hub.md)。 那里一个交换机把三个网络放进同一个联邦域； 这里工坊和基地不在同一个域里， 所以没有哪个界面有它们之间的规则。 “贸易站使用工坊的”要从贸易站通往工坊的桥接器修改； 基地的界面里它是只读的。

## 留在工坊的东西

“贸易站使用工坊的”存储规则只是普通的开启， 所以工坊的驱动器只有贸易站看得到， 基地看不到。 这不影响任务： 产物会回到基地正在等待的CPU。 但落进工坊存储里的东西， 例如副产物， 或者你取消的任务的产物（参见[远程合成](../remote-processing.md)）， 基地是看不到的。 想在基地看到它们， 把“贸易站使用工坊的”存储规则也切到开启并转发。

## 试一试

把“贸易站使用工坊的”合成规则切回开启。 合金炉的配方从基地的终端里消失， 而贸易站自己仍然保留它们。 再切到转发： 配方回来了， 基地又可以下单。
