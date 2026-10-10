---
navigation:
  parent: examples/index.md
  title: 靠供应器FE运行的粉碎机
  icon: appflux:induction_card
  position: 46
---

# 靠供应器FE运行的粉碎机

**目标**： 在主网络上请求沙砾， 由供应器的端点上的一台Mekanism粉碎机粉碎圆石， 它用的是主网络存储里的FE。 粉碎机没有发电机， 也没有能量立方， 它的子网络也没有能源元件。 这一页出现， 是因为安装了Applied Flux和Mekanism。

**需要**： 带合成CPU、合成终端、存有圆石， 并且ME驱动器里装着<ItemLink id="appflux:fe_1k_cell" />的主网络； 一个装有<ItemLink id="appflux:induction_card" />的<ItemLink id="ae2federation:pattern_provider" />； 一个<ItemLink id="ae2federation:processing_endpoint" />； <ItemLink id="ae2federation:cable" />； 一台<ItemLink id="mekanism:crusher" />； 一个<ItemLink id="ae2:storage_bus" />。 FE怎样存进元件见[Applied Flux的指南](appflux:appflux/flux_cells.md)。

<GameScene zoom="5" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/induction_crusher.snbt" />
  <BoxAnnotation color="#915dcd" min="5 0 0" max="7 2 1">
    主网络： 能源元件、合成终端、合成CPU， 以及装着物品元件和ME能源存储元件的ME驱动器
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4 0 0" max="5 1 1">
    升级一栏里装着感应卡的联邦样板供应器： 把你网络的FE继续送到它端点上的机器
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 0 0" max="3 1 1">
    处理端点： 前面接联邦线缆， 顶上是粉碎机， 侧面接粉碎机的子网络
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 1 0" max="3 2 1">
    粉碎机： 从下方的端点取FE， 并把沙砾向下弹出到端点里
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="1.7 1.125 0.125" max="2 1.875 0.875">
    粉碎机侧面的存储总线： 粉碎机的这一面设为输入物品
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

联邦界面里会显示你的主网络连着它的供应器映射的端点。 输入沿着连线送出， 结果送回来， 子网络靠你网络的能量运行：

<FederationTopology>
  <Network key="main" label="主网络" color="#915dcd" column="0" row="0" details="ME能源存储元件|供应器里的感应卡" />
  <Endpoint key="crusher" label="端点 · 粉碎机" owner="main" energy="true" details="粉碎机， 没有自己的电源" />
</FederationTopology>

## 搭建

1. **放置供应器**， 前面接联邦线缆， 另一个面接你的网络。
2. **放置端点**， 前面接同一条线缆， 再把粉碎机放在端点顶上。
3. **在端点的另一个面上搭粉碎机的子网络**： 一段ME线缆， 粉碎机侧面装一个存储总线。 供应器使用这个子网络时， 端点用你网络的能量给它供电。 它不能接到你的主网络。
4. **设置粉碎机的各面**： 在它的侧面配置里， 物品方面把朝向存储总线的一面设为输入， 底面设为输出， 并打开自动弹出； 能量方面把底面设为输入。
5. **把感应卡放进供应器**， 放在它界面里**升级**一栏的槽中。 供应器随后把你网络存储里的FE送进贴着它此时能发送样板的端点的机器， 这里就是粉碎机。
6. **放入样板**： 编码一个从一个圆石到一个沙砾的处理样板， 放进供应器， 再在供应器的连线图里把它拖到端点上。
7. **在主网络上请求沙砾**。 圆石进入子网络的存储， 也就是粉碎机； 它用你网络的FE粉碎圆石， 把沙砾弹出到端点里， 端点再把沙砾送回你的网络。

FE只送到供应器此时能发送样板的端点： 一旦到端点的联邦连接断开， 粉碎机就不再得到FE。

## 试一试

搭建时跳过第5步， 然后请求沙砾。 圆石到了粉碎机里， 但它没有电， 所以没有沙砾回来， 任务一直等待。 把感应卡放进供应器的升级槽， 任务就会完成。
