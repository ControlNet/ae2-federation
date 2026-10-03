---
navigation:
  parent: index.md
  title: 远程加工
  icon: ae2federation:processing_endpoint
  position: 30
---

# 远程加工

一个网络上的<ItemLink id="ae2federation:pattern_provider" />可以通过放在机器旁的<ItemLink id="ae2federation:processing_endpoint" />， 把处理样板发给属于另一个网络的机器。 产物会回到供应器所在的网络。 加工不需要在联邦界面里设置规则。

<Row>
  <RecipeFor id="ae2federation:pattern_provider" />
  <RecipeFor id="ae2federation:processing_endpoint" />
</Row>

## 搭建

1. **机器所在的网络**。 按AE2的常规做法搭一个小ME网络（加工子网络）， 用它的存储给机器供料， 例如让存储总线或接口对着机器。 放置端点， 让它的一个ME面接入这个子网络。 子网络不能是供应器自己的网络。
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
  <BoxAnnotation color="#5CA7CD" min="1 1 0" max="3 3 1">
    加工子网络， 有自己的电源
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="0.125 2 0.125" max="0.875 2.3 0.875">
    存储总线： 子网络的存储， 原料直接进入熔炉
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="0 0 0" max="1 1 1">
    漏斗： 把产物推入端点的侧面， 而不是前面
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

供应器所在的网络发起合成时， 原料会进入子网络的ME存储， 再从那里送到机器。 机器（或管道）必须把产物推入端点前面以外的某个面。 产物先进入供应器中对应这个端点的回流缓冲， 再进入供应器所在的网络。 留在子网络存储里的产物不会返回。

## 一个端点只属于一个供应器

端点同一时间只属于一个供应器。 映射样板时会占用端点； 其他供应器会把它显示为虚线且只读， 往它上面拖样板会被拒绝， 并显示它的所有者。 要把端点交给别的供应器， 先在拥有它的供应器里删除映射并释放它。 释放要等到没有正在发送的内容、并且回流缓冲已清空。

同一个供应器映射的两个端点必须位于不同的子网络。

## 本地模式

端点也可以为另一个网络上的普通AE2 <ItemLink id="ae2:pattern_provider" />方块服务： 把那个样板供应器贴在端点的前面即可。 此时端点工作在本地模式， 在移走这个原版供应器之前不能接收联邦样板。
