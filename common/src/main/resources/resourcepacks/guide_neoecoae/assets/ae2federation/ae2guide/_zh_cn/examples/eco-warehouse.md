---
navigation:
  parent: examples/index.md
  title: 区域ECO仓库
  icon: neoecoae:storage_system_l4
  position: 80
---

# 区域ECO仓库

**目标**： 把整个区域的库存放进一个Neo ECO存储系统， 让各个城区通过一个枢纽使用它。 每个城区有一个通往枢纽的桥接器， 只和枢纽之间有规则。 城区离仓库隔着两个桥接器， 处在另一个联邦域里， 由枢纽以转发的方式把仓库传过来。 这一页出现， 是因为安装了Neo ECO AE Extension。

**需要**：

* 仓库网络： 一个已成形的ECO存储系统， 包括主机（<ItemLink id="neoecoae:storage_system_l4" />）、 装着ECO存储矩阵（例如<ItemLink id="neoecoae:eco_item_storage_cell_16m" />）的驱动器（<ItemLink id="neoecoae:eco_drive" />）， 以及接在仓库线缆上的<ItemLink id="neoecoae:storage_interface" />； 电源。
* 枢纽网络： 一段ME线缆， 别的什么都不要。
* 每个城区网络： 线缆上一个终端， 没有自己的存储和电源。
* 枢纽和仓库之间一个<ItemLink id="ae2federation:bridge" />， 每个城区和枢纽之间各一个。

<GameScene zoom="3" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/eco_warehouse.snbt" />
  <BoxAnnotation color="#5ccd78" min="0 0 0" max="6 3 3">
    仓库： 最小的ECO存储系统， 背面的通讯接口接在仓库的线缆上， 还有给三个网络供电的能源元件
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="6 1.25 2.25" max="6.375 1.75 2.75">
    枢纽通往仓库的桥接器： 装在枢纽的线缆上， 外侧接触仓库的线缆
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="6.375 1 2" max="8 2 3">
    枢纽： 只有线缆， 没有自己的存储、 CPU和电源
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="8 1.25 2.25" max="8.375 1.75 2.75">
    城区的桥接器： 装在城区的线缆上， 外侧接触枢纽的线缆
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="8.375 1 2" max="10 2 3">
    城区： 一个终端， 别的什么都没有
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

每个桥接器和它连接的两个网络单独组成一个联邦域。 右键城区的桥接器： 界面显示本域， 也就是城区和枢纽。 在范围按钮上选“含所有相连的域（只读）”， 枢纽和仓库所在的域就会在单独的底板上加进来， 只读：

<FederationTopology>
  <Network key="district" label="城区" color="#915dcd" column="0" row="0" details="终端" />
  <Network key="hub" label="枢纽" color="#5CA7CD" column="1" row="0" details="只有线缆" />
  <Network key="warehouse" label="仓库" color="#5ccd78" column="2" row="0" details="ECO存储系统|能源元件" />
  <Rule user="hub" source="warehouse" capability="storage" state="reexport" />
  <Rule user="district" source="hub" capability="storage" />
  <Energy first="warehouse" second="hub" />
  <Energy first="hub" second="district" />
  <Domain key="d1" label="本域" networks="district,hub" opened="true" />
  <Domain key="d2" label="域 3C91" networks="hub,warehouse" />
</FederationTopology>

## 搭建

1. **按Neo ECO指南搭建[存储系统](neoecoae:neoecoae_intro/storage_system.md)**， 最小的长5格、 高3格、 深2格。 往它的驱动器里放ECO存储矩阵， 再把它的通讯接口和一个能源元件接到仓库的线缆上。
2. **把枢纽接到仓库**： 在枢纽的线缆上装一个桥接器， 让它的外侧接触仓库的线缆。
3. **右键这个桥接器**。 打开“枢纽使用仓库的”存储规则， 再往前切一次， 切到开启并转发。 同时打开这对网络的**ME能量**。
4. **把城区接到枢纽**： 在城区的线缆上装一个桥接器， 让它的外侧接触枢纽的线缆。 右键它： 界面里只有城区和枢纽。 打开“城区使用枢纽的”存储规则和ME能量。
5. **打开城区的终端**。 仓库里的物品都列在这里。 取出一些， 再放进去一些： 城区没有自己的存储， 放进去的东西存进仓库。

## 隔着两个桥接器

* 城区和仓库不在同一个联邦域里， 所以没有哪个界面有“城区使用仓库的”开关， 也不需要它。 城区能用到什么， 由两条规则决定： 城区自己的域里的“城区使用枢纽的”， 以及枢纽和仓库的域里设为转发的“枢纽使用仓库的”。 后者要从枢纽通往仓库的桥接器修改； 城区的界面里它是只读的。
* 更多城区也这样接入， 每个城区有自己通往枢纽的桥接器和自己的“使用枢纽的”存储规则。 不管有多少城区， 仓库都只有一个桥接器和一条规则。
* 能量沿着链汇到一起： 两对网络都打开ME能量后， 三个网络都靠仓库的能源元件运行。 ME能量没有转发档， 能量池只是连成一片。

## 试一试

把“枢纽使用仓库的”存储规则切回开启。 仓库的物品从城区的终端里消失， 而枢纽凭自己的规则仍然能看到它们。 再切到转发， 它们就回来了。
