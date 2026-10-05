---
navigation:
  parent: examples/index.md
  title: 送回化学品
  icon: mekanism:chemical_oxidizer
  position: 50
---

# 送回化学品

**目标**： 在主网络上请求碳， 它是Mekanism的一种化学品。 待在自己小网络里的化学氧化机把木炭变成碳， 再直接推回端点。 这一页出现， 是因为安装了Mekanism和Applied Mekanistics。

**需要**： 带合成CPU、合成终端、存有木炭， 并在驱动器里装有<ItemLink id="appmek:chemical_storage_cell_1k" />的主网络； 一个<ItemLink id="ae2federation:pattern_provider" />； 一个<ItemLink id="ae2federation:processing_endpoint" />； <ItemLink id="ae2federation:cable" />； 一台<ItemLink id="mekanism:chemical_oxidizer" />； 一个充好电的<ItemLink id="mekanism:basic_energy_cube" />； 一个<ItemLink id="ae2:storage_bus" />。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/mekanism_oxidizer.snbt" />
  <BoxAnnotation color="#915dcd" min="5 0 0" max="7 2 1">
    主网络： 合成终端、合成CPU， 以及装着物品元件和化学品元件的驱动器
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4 0 0" max="5 1 1">
    联邦样板供应器： 前面接联邦线缆， 背面接你的网络
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 1 0" max="3 2 1">
    处理端点： 前面朝下接线缆； 氧化机立在它上面， 子网络的线缆接在它侧面
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="1.7 2.125 0.125" max="2 2.875 0.875">
    氧化机侧面的存储总线： 氧化机的这一面设为输入物品
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 2 0" max="3 3 1">
    化学氧化机： 底面输出化学品并打开自动弹出， 碳直接进入端点
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 3 0" max="3 4 1">
    基础能量立方： 底面向氧化机的顶面输出能量
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

联邦界面里会显示主网络， 以及连到它上面、 由它的供应器映射的端点。 输入沿着连线送出， 结果沿着它回来， 子网络用的是你网络的电：

<FederationTopology>
  <Network key="main" label="主网络" color="#915dcd" column="0" row="0" details="合成CPU、终端|联邦样板供应器" />
  <Endpoint key="oxidizer" label="端点 · 氧化机" owner="main" energy="true" details="化学氧化机的子网络" />
</FederationTopology>

## 搭建

1. **放置供应器**， 前面接联邦线缆， 另一个面接你的网络。
2. **放置端点**， 前面接同一条线缆， 再把化学氧化机立在端点顶上。
3. **在端点的另一个面上搭氧化机的子网络**： 一段ME线缆， 在氧化机侧面装一个存储总线。 供应器使用端点期间， 端点会用你网络的电给这个子网络供电。 子网络不能连到你的主网络。
4. **设置氧化机的各个面**。 在它的侧面配置里， 物品一项把朝向存储总线的一面设为输入； 化学品一项把底面设为输出并打开自动弹出； 能量一项把顶面设为输入。
5. **给氧化机供电**。 在它顶上放一个充好电的基础能量立方， 把立方的底面设为输出。 用任何产FE的机器给立方充电即可。
6. **存放碳**。 Applied Mekanistics让AE2能存储化学品： 在主网络的驱动器里放一个化学品存储元件。 Mekanism的化学品不是流体， 流体元件装不下它们。
7. **加入样板**。 氧化机用一个木炭做出20 mB碳。 编码一个从一个木炭到20 mB碳的处理样板， 放进供应器， 然后在供应器的接线图里把它拖到端点上。
8. **在主网络上请求碳**。 木炭进入子网络的存储， 也就是氧化机； 碳向下流进端点， 回到你的网络。

端点除前面以外的任意一面都能收回物品、流体以及AE2附属模组添加的其他资源， 比如Applied Mekanistics的化学品。 自己会推出产物的机器不需要漏斗。

## 试一试

关掉氧化机化学品的自动弹出， 然后请求碳。 碳积在氧化机里， 什么都不会回来， 任务一直等待。 重新打开自动弹出， 任务就会完成。
