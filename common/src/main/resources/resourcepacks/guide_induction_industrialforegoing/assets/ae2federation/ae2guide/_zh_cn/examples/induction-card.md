---
navigation:
  parent: examples/index.md
  title: 靠供应器FE运行的高级熔炉
  icon: appflux:induction_card
  position: 46
---

# 靠供应器FE运行的高级熔炉

**目标**： 在主网络上请求石头， 由供应器的端点上的一台Industrial Foregoing高级熔炉烧炼圆石， 它用的是主网络存储里的FE。 熔炉没有自己的电源， 它的子网络也没有能源元件。 这一页出现， 是因为安装了Applied Flux和Industrial Foregoing。

**需要**： 带合成CPU、合成终端、存有圆石， 并且ME驱动器里装着<ItemLink id="appflux:fe_1k_cell" />的主网络； 一个装有<ItemLink id="appflux:induction_card" />的<ItemLink id="ae2federation:pattern_provider" />； 一个<ItemLink id="ae2federation:processing_endpoint" />； <ItemLink id="ae2federation:cable" />； 一台<ItemLink id="industrialforegoing:resourceful_furnace" />； 一个<ItemLink id="ae2:storage_bus" />。 FE怎样存进元件见[Applied Flux的指南](appflux:appflux/flux_cells.md)。

<GameScene zoom="5" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/induction_furnace.snbt" />
  <BoxAnnotation color="#915dcd" min="5 0 0" max="7 2 1">
    主网络： 能源元件、合成终端、合成CPU， 以及装着物品元件和ME能源存储元件的ME驱动器
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4 0 0" max="5 1 1">
    升级一栏里装着感应卡的联邦样板供应器： 把你网络的FE继续送到它端点上的机器
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 0 0" max="3 1 1">
    处理端点： 前面接联邦线缆， 顶上是熔炉， 侧面接熔炉的子网络
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 1 0" max="3 2 1">
    高级熔炉： 从下方的端点取FE， 并把石头向下推进端点
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="1.7 1.125 0.125" max="2 1.875 0.875">
    熔炉侧面的存储总线
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

联邦界面里会显示你的主网络连着它的供应器映射的端点。 输入沿着连线送出， 结果送回来， 子网络靠你网络的能量运行：

<FederationTopology>
  <Network key="main" label="主网络" color="#915dcd" column="0" row="0" details="ME能源存储元件|供应器里的感应卡" />
  <Endpoint key="furnace" label="端点 · 熔炉" owner="main" energy="true" details="高级熔炉， 没有自己的电源" />
</FederationTopology>

## 搭建

1. **放置供应器**， 前面接联邦线缆， 另一个面接你的网络。
2. **放置端点**， 前面接同一条线缆， 再把高级熔炉放在端点顶上。
3. **在端点的另一个面上搭熔炉的子网络**： 一段ME线缆， 熔炉侧面装一个存储总线。 供应器使用这个子网络时， 端点用你网络的能量给它供电。 它不能接到你的主网络。
4. **让熔炉的输出从底面推送**： 在它的面配置里选中输出物品栏， 把底面设为推送（Push）。 输入物品栏保持不变。 底面随后把石头推进端点； 熔炉的每一面都接收FE。
5. **把感应卡放进供应器**， 放在它界面里**升级**一栏的槽中。 供应器随后把你网络存储里的FE送进贴着它此时能发送样板的端点的机器， 这里就是熔炉。
6. **放入样板**： 编码一个从一个圆石到一个石头的处理样板， 放进供应器， 再在供应器的连线图里把它拖到端点上。
7. **在主网络上请求石头**。 圆石进入子网络的存储， 也就是熔炉； 它用你网络的FE烧炼圆石， 把石头推进端点， 端点再把石头送回你的网络。

FE只送到供应器此时能发送样板的端点： 一旦到端点的联邦连接断开， 熔炉就不再得到FE。

## 试一试

搭建时跳过第5步， 然后请求石头。 圆石到了熔炉里， 但它没有电， 所以没有石头回来， 任务一直等待。 把感应卡放进供应器的升级槽， 任务就会完成。
