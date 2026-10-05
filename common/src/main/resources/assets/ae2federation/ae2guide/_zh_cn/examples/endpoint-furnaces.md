---
navigation:
  parent: examples/index.md
  title: 外包熔炉
  icon: ae2federation:processing_endpoint
  position: 30
---

# 外包熔炉

**目标**： 在主网络上请求石头， 而负责烧制的熔炉各自待在自己的小网络里。 处理样板留在主网络上， 不需要规则， 也不共享存储。

**需要**： 带合成CPU、合成终端并存有圆石的主网络； 一个<ItemLink id="ae2federation:pattern_provider" />； 每座熔炉一个<ItemLink id="ae2federation:processing_endpoint" />、一座熔炉、一个漏斗和一个<ItemLink id="ae2:storage_bus" />； <ItemLink id="ae2federation:cable" />。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/endpoint_furnaces.snbt" />
  <BoxAnnotation color="#915dcd" min="10 0 0" max="12 2 1">
    主网络： 合成终端、合成CPU和存储
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="9 0 0" max="10 1 1">
    联邦样板供应器： 前面接联邦线缆， 背面接你的网络
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="1 0.3 0.3" max="9 0.7 0.7">
    通往两个端点的联邦线缆
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="5 1 0" max="6 2 1">
    处理端点： 前面朝下接线缆， 顶面接熔炉的子网络
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4.125 3 0.125" max="4.875 3.3 0.875">
    熔炉顶上的存储总线： 原料直接进入熔炉
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4 1 0" max="5 2 1">
    熔炉下方的漏斗： 把石头推进端点的侧面
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 1 0" max="2 4 1">
    第一座熔炉的子网络
  </BoxAnnotation>
  <BoxAnnotation color="#cdc35c" min="4 1 0" max="6 4 1">
    第二座熔炉的子网络
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

联邦界面里会显示主网络， 以及连到它上面、 由它的供应器映射的两个端点。 输入沿着连线送出， 结果沿着它回来， 两个子网络用的都是你网络的电：

<FederationTopology>
  <Network key="main" label="主网络" color="#915dcd" column="0" row="0" details="合成CPU、终端|联邦样板供应器" />
  <Endpoint key="first" label="端点 · 熔炉1" owner="main" energy="true" details="第一座熔炉的子网络" />
  <Endpoint key="second" label="端点 · 熔炉2" owner="main" energy="true" details="第二座熔炉的子网络" />
</FederationTopology>

## 先搭一座熔炉

1. **放置供应器**， 前面接联邦线缆， 另一个面接你的网络。
2. **放置端点**， 前面接同一条线缆。
3. **在端点的另一个面上搭熔炉的子网络**： 一段ME线缆， 在熔炉顶上装一个存储总线。 供应器使用端点期间， 端点会用你网络的电给这个子网络供电， 所以它不需要自己的电源。 子网络不能连到你的主网络。
4. **把产物送回来**。 熔炉下方的漏斗把石头推进端点除前面以外的任意一面。
5. **给熔炉加燃料**。 存储总线只会填顶部的槽位。 自己放入燃料， 或者从侧面用另一个漏斗供给。
6. **加入样板**。 编码一个从一个圆石到一个石头的处理样板， 放进供应器， 然后在供应器的接线图里把它拖到端点上。
7. **在主网络上请求石头**。 圆石进入子网络的存储， 也就是熔炉； 石头经过漏斗和端点回到你的网络。

## 增加熔炉

在同一条联邦线缆上再搭一个子网络， 配上它自己的端点、熔炉和漏斗， 再把同一个样板映射过去。 同一个供应器的两个端点必须在不同的子网络上。 在供应器里打开阻挡模式： 每个端点一次只接一批， 下一批会交给空闲的端点， 某座熔炉卡住也不会拖住其他熔炉。 批次怎样分配由AE2自己决定， 所以不要指望平均分配。

## 试一试

任务进行时拆掉漏斗。 石头留在熔炉里， 任务一直等待： 产物只有进入端点才算数， 留在子网络存储里的石头永远不会回来。 放回漏斗， 任务就会完成。
