---
navigation:
  parent: mechanics/index.md
  title: 规则
  icon: ae2federation:switch
  position: 10
---

# 规则

规则让一个网络使用另一个网络的某样东西。 规则属于一对网络和一个方向： “A使用B的存储”和“B使用A的存储”是两条规则。 在一个联邦域里设置的规则， 在这两个网络相遇的其他地方也同样生效。 所有规则一开始都是禁用的。

| 规则 | 允许什么 | 状态 |
|---|---|---|
| 存储 | 查看、存入和取出对方网络的物品、流体和其他资源 | 禁用、启用、启用（可转出） |
| 合成 | 从自己的终端和自动化向对方网络的样板供应器下单 | 禁用、启用、启用（可转出） |
| ME能量 | 把两个网络的AE能量合成一个能量池 | 禁用、启用 |

在联邦界面里点击两个网络之间的连线， 就会列出它们的规则。 左键规则向前切换， 右键向后切换。

* **存储和合成互相独立**。 只开合成时， 任务由你自己的合成CPU执行， 从你的网络能看到的材料里取原料， 产物也进入你的网络； 对方网络不需要CPU。 想让CPU也使用对方网络的材料， 或者拿到留在那里的剩余物品， 再打开同方向的存储。 参见[跨网络合成](../crafting/index.md)。
* **ME能量**每对网络只有一个开关， 能量双向流动。 参见[能量](power.md)。
* 联邦样板供应器和处理端点之间的**加工**不需要规则： 任何供应器都可以使用其联邦域内的任何端点。

## 可转出： 把访问传下去

第三档**启用（可转出）**让一个网络把自己的访问转给别人。 如果A使用B的存储， 而B使用C的存储并设为可转出， A就能经由B用到C的存储。 合成也一样， 两个网络还可以互相使用对方的样板供应器。 这条链可以从一个联邦域跨到下一个。

下面的场景就是这种情况。 网络A用一个联邦桥连到B， B再用另一个联邦桥连到C。 每个联邦桥自成一个联邦域， 所以A和C从不相遇， 哪个界面里都没有“A使用C的”规则。 B把C的存储转出后， A照样能用到它。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/across_domains.snbt" />
  <BoxAnnotation color="#915dcd" min="5.375 0 0" max="8 1 1">
    网络A： 一个终端， 以及给三个网络供电的能源元件
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="5 0.25 0.25" max="5.375 0.75 0.75">
    A和B之间的联邦桥： 由这两个网络组成的联邦域
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="3.375 0 0" max="5 2 1">
    网络B： 自己的一个驱动器， 也是转出C的访问的网络
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0.25 0.25" max="3.375 0.75 0.75">
    B和C之间的联邦桥： 第二个联邦域， 由B和C组成
  </BoxAnnotation>
  <BoxAnnotation color="#5ccd78" min="0 0 0" max="3 2 1">
    网络C： 装满物品的驱动器， 靠A的电运行
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

在A–B联邦桥的联邦界面里显示相连的域时：

<FederationTopology>
  <Network key="a" label="网络A" color="#915dcd" column="0" row="0" details="终端|能源元件" />
  <Network key="b" label="网络B" color="#5CA7CD" column="1" row="0" details="驱动器" />
  <Network key="c" label="网络C" color="#5ccd78" column="2" row="0" details="驱动器" />
  <Domain key="ab" label="本域" networks="a,b" opened="true" />
  <Domain key="bc" label="域 7E2A" networks="b,c" />
  <Rule user="a" source="b" capability="storage" />
  <Rule user="b" source="c" capability="storage" state="reexport" />
  <Energy first="a" second="b" />
  <Energy first="b" second="c" />
</FederationTopology>

### 搭建

1. **用两个联邦桥连接网络**， 如场景所示： 一个在A和B之间， 一个在B和C之间。
2. **打开ME能量**： 在A–B联邦桥的界面里打开A和B之间的， 在B–C联邦桥的界面里打开B和C之间的。
3. **右键B–C联邦桥**， 打开“B使用C的”存储规则， 再往前切一次， 切到“启用（可转出）”。
4. **右键A–B联邦桥**， 打开“A使用B的”存储规则。 这里启用就够了； 只有还要让另一个网络经由A用到B和C时， 这条规则才需要可转出。

### 网络A看到什么

A的终端把C的物品和A、B自己的物品列在一起。 A可以从C的驱动器里取出物品， 也能像存入自己的存储一样存入它们。

* **物品直接移动**， 在C的驱动器和A之间往来。 B必须保持加载， 两个联邦桥也都要在原处， 它的规则才起作用。
* **由“B使用C的”上的可转出决定**。 B在两种状态下都能看到C的物品； 可转出决定使用B的网络是否也能看到。
* **更长的链**也一样， 只要沿途每个网络都把访问转出给下一个。 在绕回起点的链里， 每个网络看到的自己的物品仍然只有一份。

### 沿链合成

“B使用C的”合成规则设为“启用（可转出）”， “A使用B的”合成规则也打开后， A的终端就会列出C的<ItemLink id="ae2:pattern_provider" />里的样板。 A的合成CPU把原料直接推送给C的样板供应器， 产物回到A； B不需要CPU。 A的CPU从A取原料， “A使用B的”存储规则打开时也从B取。 副产物等剩余物品留在C上， A只有通过设为可转出的“B使用C的”存储规则才能拿到。

### 沿链共享ME能量

ME能量没有可转出档。 A与B共享， B与C共享， 三个能量池就连成一个， 三个网络都靠A的能源元件运行。

### 在联邦界面里

A–B联邦桥的界面打开时是**当前域**： A、B以及它们之间的规则。 另一个范围按钮切换到**含所有相连的域（只读）**， 把B–C联邦域放在自己的底板上加进来， 带着网络C和“B使用C的”规则的实际状态， 用可转出的颜色显示。 这条规则要到B–C联邦桥上编辑。

### 试一试

把“B使用C的”存储规则切回“启用”。 C的物品从A的终端里消失， 但仍留在B的终端里。 再切到可转出， 它们就回来了。
