---
navigation:
  parent: examples/index.md
  title: 远程Mekanism粉碎机
  icon: mekanism:crusher
  position: 40
---

# 远程Mekanism粉碎机

**目标**： 在主网络上请求沙砾， 而粉碎圆石的Mekanism粉碎机待在它自己的小网络里。 这一页出现， 是因为安装了Mekanism。

**需要**： 带合成CPU、合成终端并存有圆石的主网络； 一个<ItemLink id="ae2federation:pattern_provider" />； 一个<ItemLink id="ae2federation:processing_endpoint" />； <ItemLink id="ae2federation:cable" />； 一台<ItemLink id="mekanism:crusher" />； 一个充好电的<ItemLink id="mekanism:basic_energy_cube" />； 一个漏斗和一个<ItemLink id="ae2:storage_bus" />。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/mekanism_crusher.snbt" />
  <BoxAnnotation color="#915dcd" min="5 0 0" max="7 2 1">
    主网络： 合成终端、合成CPU和存储
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4 0 0" max="5 1 1">
    联邦样板供应器： 前面接联邦线缆， 背面接你的网络
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 1 0" max="3 2 1">
    处理端点： 前面朝下接线缆， 顶面接粉碎机的子网络
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="1.125 3 0.125" max="1.875 3.3 0.875">
    粉碎机顶上的存储总线： 粉碎机的顶面设为输入物品
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="1 1 0" max="2 2 1">
    粉碎机下方的漏斗： 粉碎机的底面设为输出物品， 漏斗把沙砾推进端点的侧面
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="0 2 0" max="1 3 1">
    基础能量立方： 朝向粉碎机的一面输出能量
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

联邦界面里会显示主网络， 以及连到它上面、 由它的供应器映射的端点。 输入沿着连线送出， 结果沿着它回来， 子网络用的是你网络的电：

<FederationTopology>
  <Network key="main" label="主网络" color="#915dcd" column="0" row="0" details="合成CPU、终端|联邦样板供应器" />
  <Endpoint key="crusher" label="端点 · 粉碎机" owner="main" energy="true" details="粉碎机的子网络" />
</FederationTopology>

## 搭建

1. **放置供应器**， 前面接联邦线缆， 另一个面接你的网络。
2. **放置端点**， 前面接同一条线缆。
3. **在端点的另一个面上搭粉碎机的子网络**： 一段ME线缆， 在粉碎机顶上装一个存储总线。 供应器使用端点期间， 端点会用你网络的电给这个子网络供电。 子网络不能连到你的主网络。
4. **设置粉碎机的各个面**。 在它的侧面配置里， 物品一项把顶面设为输入、底面设为输出； 能量一项把朝向能量立方的一面设为输入。 这样存储总线会填入输入槽， 漏斗会取走输出槽里的东西。
5. **给粉碎机供电**。 在它旁边放一个充好电的基础能量立方， 把立方朝向粉碎机的一面设为输出。 Mekanism本身没有发电机； 用任何产FE的机器给立方充电即可， 比如Mekanism发电机里的发电机。
6. **把产物送回来**。 粉碎机下方的漏斗把沙砾推进端点除前面以外的任意一面。
7. **加入样板**。 编码一个从一个圆石到一个沙砾的处理样板， 放进供应器， 然后在供应器的接线图里把它拖到端点上。
8. **在主网络上请求沙砾**。 圆石进入子网络的存储， 也就是粉碎机； 沙砾经过漏斗和端点回到你的网络。

想一次粉碎更多， 就照[外包熔炉](endpoint-furnaces.md)那样多搭几个单元。

## 试一试

在粉碎机的侧面配置里关掉顶面， 然后请求沙砾。 存储总线没有地方放圆石， 所以不会有沙砾回来， 任务一直等待。 把顶面重新设为输入， 任务就会完成。
