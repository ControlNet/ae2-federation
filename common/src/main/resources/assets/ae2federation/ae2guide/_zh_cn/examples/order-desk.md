---
navigation:
  parent: examples/index.md
  title: 接单的工厂
  icon: ae2:molecular_assembler
  position: 27
---

# 接单的工厂

**目标**： 让另一位玩家的网络向你的工厂下单， 却看不到工厂的库存。 两者之间的一条规则做不到这一点： 合成需要同一方向的存储， 所以“顾客使用工厂的”合成规则总会让顾客也看到工厂的存储。 在两者之间放一个柜台网络， 顾客就经由柜台下单。

**需要**： 顾客的网络， 带合成终端、自己的合成CPU和自己的存储， 存储里放着它用来付账的原料； 一个柜台网络， 带一个放公开展示商品的<ItemLink id="ae2:drive" />； 你的工厂， 带一个<ItemLink id="ae2:pattern_provider" />， 旁边是<ItemLink id="ae2:molecular_assembler" />， 还有一个放私人库存的驱动器和一个能源元件； 两个<ItemLink id="ae2federation:bridge" />。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/order_desk.snbt" />
  <BoxAnnotation color="#915dcd" min="5.375 0 0" max="8 2 1">
    顾客： 合成终端、合成CPU， 以及放着付账原料的驱动器
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="5 0.25 0.25" max="5.375 0.75 0.75">
    顾客和柜台之间的桥接器： 由这两个网络组成的联邦域
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="3.375 0 0" max="5 2 1">
    柜台： 一个驱动器， 放店里公开展示的商品
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0.25 0.25" max="3.375 0.75 0.75">
    柜台和工厂之间的桥接器： 第二个联邦域， 由这两个网络组成
  </BoxAnnotation>
  <BoxAnnotation color="#5ccd78" min="0 0 0" max="3 2 1">
    工厂： 样板供应器， 旁边是分子装配室； 放私人库存的驱动器， 以及给三个网络供电的能源元件
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

在顾客那个桥接器的联邦界面里显示相连的域时：

<FederationTopology>
  <Network key="customer" label="顾客" color="#915dcd" column="0" row="0" details="合成CPU、终端|驱动器" />
  <Network key="counter" label="柜台" color="#5CA7CD" column="1" row="0" details="驱动器" />
  <Network key="factory" label="工厂" color="#5ccd78" column="2" row="0" details="样板供应器|私人驱动器、能源元件" />
  <Domain key="front" label="本域" networks="customer,counter" opened="true" />
  <Domain key="back" label="域 3C91" networks="counter,factory" />
  <Rule user="customer" source="counter" capability="crafting" />
  <Rule user="customer" source="counter" capability="storage" />
  <Rule user="counter" source="factory" capability="crafting" state="reexport" />
  <Rule user="counter" source="factory" capability="storage" />
  <Energy first="customer" second="counter" />
  <Energy first="counter" second="factory" />
</FederationTopology>

## 搭建

1. **用两个桥接器连接网络**， 如场景所示： 一个在顾客和柜台之间， 一个在柜台和工厂之间。
2. **打开ME能量**： 在第一个桥接器的界面里打开顾客和柜台之间的， 在第二个桥接器的界面里打开柜台和工厂之间的。 能量沿着这条链汇到一起， 三个网络都靠工厂的能源元件运行。
3. **右键柜台–工厂桥接器**， 打开“柜台使用工厂的”合成规则， 再往前切一次， 切到开启并转发。 存储规则会随之打开； 让它保持开启， 不要转发。
4. **右键顾客–柜台桥接器**， 打开“顾客使用柜台的”合成规则。 存储规则会随之打开。
5. **在顾客的终端下单**。 工厂的配方列在顾客的可合成物品里。

## 顾客看到什么

* **柜台的商品**。 顾客对柜台的合成规则会打开同一方向的存储规则， 所以柜台的驱动器向顾客敞开： 顾客的终端列出这些商品， 顾客也能把它们取出， 并像存入自己的存储一样存入这个驱动器。 那里只放你愿意交出去的东西。
* **工厂的配方**， 由“柜台使用工厂的”合成规则上的转发传过来。
* **看不到工厂的库存**。 那条规则的存储只是开启： 柜台能看到工厂的驱动器， 但不会转发出去。

## 订单怎样运行

顾客的合成CPU执行任务， 也由顾客付账。 CPU从顾客能看到的东西里取原料， 也就是顾客自己的存储和柜台的驱动器， 再把原料直接推送给工厂的样板供应器； 产物直接回到顾客这里。 柜台不参与， 也不需要CPU， 没有东西经过它。 柜台上不要放工厂的配方会用到的东西， 否则顾客可能拿店里自己的商品付账。

副产物、被取消的任务的产物等剩余物品会落在工厂里。 它们仍归你， 顾客够不到。

## 为什么要两个桥接器

每个桥接器把它连接的两个网络组成一个联邦域。 顾客的桥接器里只有顾客和柜台， 工厂也只和柜台同在一个联邦域里， 所以哪个界面里都没有“顾客使用工厂的”规则。 顾客的桥接器只在显示相连的域时才显示“柜台使用工厂的”， 而且只读， 如上图所示； 这条规则要在柜台–工厂桥接器上设置。

## 试一试

把“柜台使用工厂的”存储规则再往前切一次， 切到开启并转发。 工厂的私人库存出现在顾客的终端里。 再切回开启， 库存又消失了， 而顾客仍然可以向工厂下单。
