---
navigation:
  parent: examples/index.md
  title: 靠供应器FE运行的半自磨机
  icon: appflux:induction_card
  position: 46
---

# 靠供应器FE运行的半自磨机

**目标**： 在主网络上请求圆石， 由供应器的端点上的一台Ender IO半自磨机研磨石头， 它用的是主网络存储里的FE。 半自磨机没有自己的电源， 它的子网络也没有能源元件。 这一页出现， 是因为安装了Applied Flux和Ender IO。

**需要**： 带合成CPU、合成终端、存有石头， 并且ME驱动器里装着<ItemLink id="appflux:fe_1k_cell" />的主网络； 一个装有<ItemLink id="appflux:induction_card" />的<ItemLink id="ae2federation:pattern_provider" />； 一个<ItemLink id="ae2federation:processing_endpoint" />； <ItemLink id="ae2federation:cable" />； 一台装有<ItemLink id="enderio:basic_capacitor" />的<ItemLink id="enderio:sag_mill" />； 一个<ItemLink id="ae2:storage_bus" />。 FE怎样存进元件见[Applied Flux的指南](appflux:appflux/flux_cells.md)。

<GameScene zoom="5" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/induction_sag_mill.snbt" />
  <BoxAnnotation color="#915dcd" min="5 0 0" max="7 2 1">
    主网络： 能源元件、合成终端、合成CPU， 以及装着物品元件和ME能源存储元件的ME驱动器
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4 0 0" max="5 1 1">
    升级一栏里装着感应卡的联邦样板供应器： 把你网络的FE继续送到它端点上的机器
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 0 0" max="3 1 1">
    处理端点： 前面接联邦线缆， 顶上是半自磨机， 侧面接半自磨机的子网络
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 1 0" max="3 2 1">
    装了基础电容的半自磨机： 从下方的端点取FE， 并把圆石向下推进端点
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="1.7 1.125 0.125" max="2 1.875 0.875">
    半自磨机侧面的存储总线
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

联邦界面里会显示你的主网络连着它的供应器映射的端点。 输入沿着连线送出， 结果送回来， 子网络靠你网络的能量运行：

<FederationTopology>
  <Network key="main" label="主网络" color="#915dcd" column="0" row="0" details="ME能源存储元件|供应器里的感应卡" />
  <Endpoint key="mill" label="端点 · 半自磨机" owner="main" energy="true" details="半自磨机， 没有自己的电源" />
</FederationTopology>

## 搭建

1. **放置供应器**， 前面接联邦线缆， 另一个面接你的网络。
2. **放置端点**， 前面接同一条线缆， 再把半自磨机放在端点顶上。
3. **在端点的另一个面上搭半自磨机的子网络**： 一段ME线缆， 半自磨机侧面装一个存储总线。 供应器使用这个子网络时， 端点用你网络的能量给它供电。 它不能接到你的主网络。
4. **给半自磨机装上基础电容**。 没有电容时它存不了能量， 什么也不做。
5. **把半自磨机的底面设为推送**： 在它的IO配置里设置， 或者用耶塔扳手。 装着存储总线的那一面保持不变。 底面随后把圆石推进端点， 同时照样从端点取FE。
6. **把感应卡放进供应器**， 放在它界面里**升级**一栏的槽中。 供应器随后把你网络存储里的FE送进贴着它此时能发送样板的端点的机器， 这里就是半自磨机。
7. **放入样板**： 编码一个从一个石头到一个圆石的处理样板， 放进供应器， 再在供应器的连线图里把它拖到端点上。
8. **在主网络上请求圆石**。 石头进入子网络的存储， 也就是半自磨机； 它用你网络的FE研磨石头， 把圆石推进端点， 端点再把圆石送回你的网络。

FE只送到供应器此时能发送样板的端点： 一旦到端点的联邦连接断开， 半自磨机就不再得到FE。

## 试一试

搭建时跳过第6步， 然后请求圆石。 石头到了半自磨机里， 但它没有电， 所以没有圆石回来， 任务一直等待。 把感应卡放进供应器的升级槽， 任务就会完成。
