---
navigation:
  parent: index.md
  title: 跨域联动
  icon: ae2federation:bridge
  position: 25
---

# 跨域联动

**目标**： 使用一个和你之间没有规则的网络。 网络A用一个桥接器连到B， B再用另一个桥接器连到C。 每个桥接器自成一个联邦域， 所以A和C从不相遇， 哪个界面里都没有“A使用C的”规则。 B的规则开启转发后， A照样能经由B使用C的存储和合成。

规则属于一对网络， 而不属于某个联邦域（参见[联邦的工作方式](mechanics.md)）： “B使用C的”只在B和C相遇的联邦域里设置一次， A能用到C， 是因为B把它的访问转发了出去。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="assets/across_domains.snbt" />
  <BoxAnnotation color="#915dcd" min="6 0 0" max="8 1 1">
    网络A： 一个终端， 以及给三个网络供电的能源元件
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="5 0.25 0.25" max="5.375 0.75 0.75">
    A和B之间的桥接器： 由这两个网络组成的联邦域
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="3.375 0 0" max="5 2 1">
    网络B： 自己的一个驱动器， 也是转发C的访问的网络
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0.25 0.25" max="3.375 0.75 0.75">
    B和C之间的桥接器： 第二个联邦域， 由B和C组成
  </BoxAnnotation>
  <BoxAnnotation color="#5ccd78" min="0 0 0" max="3 2 1">
    网络C： 装满物品的驱动器， 靠A的电运行
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

在A–B桥接器的联邦界面里显示相连的域时：

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

## 搭建

1. **用两个桥接器连接网络**， 如场景所示： 一个在A和B之间， 一个在B和C之间。
2. **打开ME能量**： 在A–B桥接器的界面里打开A和B之间的， 在B–C桥接器的界面里打开B和C之间的。
3. **右键B–C桥接器**， 打开“B使用C的”存储规则， 再往前切一次， 切到开启并转发。
4. **右键A–B桥接器**， 打开“A使用B的”存储规则。 开启就够了； 只有还要让另一个网络经由A用到B和C时， 这条规则才需要转发。

## 网络A看到什么

A的终端把C的物品和A、B自己的物品列在一起， 不标出它们在哪里。 A可以把它们取出， 也能像存入自己的存储一样存入C的驱动器。

* **没有东西经过B**。 物品直接在C的驱动器和A之间移动， 但B必须保持加载， 两个桥接器也都要在原处， 它的规则才起作用。
* **由“B使用C的”上的转发决定**。 B自己无论如何都能看到C的物品； 只有使用B的网络受它影响。
* **更长的链**也一样， 只要沿途每个网络都把访问转发给下一个。 绕回起点的链不会让一个网络把自己的物品看到两份。

## 合成也一样

如果“B使用C的”合成规则是开启并转发， “A使用B的”合成规则也已打开， A的终端就会列出C的<ItemLink id="ae2:pattern_provider" />里的样板。 A的合成CPU把原料直接推送给C的样板供应器， 产物回到A； B不需要CPU。 每条合成规则都会打开自己的存储规则， 但那条存储规则不必转发。 不转发时A看不到C的存储： A的CPU只能在A或B上找原料， 而副产物、被取消的任务的产物等剩余物品会留在C上， A够不到。 参见[远程合成](remote-processing.md)。

## 能量不需要转发就能连成一片

ME能量没有转发档， 也不需要。 A与B共享， B与C共享， 三者就共用一个能量池， 只靠A的能源元件运行。

## 联邦界面里

A–B桥接器的界面打开时是**当前域**： 只有A和B以及它们之间的规则。 点击另一个范围按钮， 说明文字变成**含所有相连的域（只读）**： B–C联邦域放在自己的底板上加进来， 带着网络C和“B使用C的”规则的实际状态， 用转发的颜色显示。 它在这里不能修改， 要到B–C桥接器上编辑。

## 试一试

把“B使用C的”存储规则切回开启。 C的物品从A的终端里消失， 而B仍然列着它们。 再切到转发， 它们就回来了。
