---
navigation:
  parent: examples/index.md
  title: 矿石翻倍产线
  icon: mekanism:enrichment_chamber
  position: 42
---

# 矿石翻倍产线

**目标**： 在主网络上下单铁锭， 让两台各在一个端点后面的Mekanism机器把每块铁矿石变成两个铁锭： 富集仓把它变成两份铁粉， 电力熔炼炉再把铁粉炼成铁锭。 这一页出现， 是因为安装了Mekanism。

**需要**： 你的主网络， 带合成CPU、合成终端， 存储里有铁矿石； 一个<ItemLink id="ae2federation:pattern_provider" />； 两个<ItemLink id="ae2federation:processing_endpoint" />； <ItemLink id="ae2federation:cable" />； 一台<ItemLink id="mekanism:enrichment_chamber" />和一台<ItemLink id="mekanism:energized_smelter" />， 每台配一个充好电的<ItemLink id="mekanism:basic_energy_cube" />、一个漏斗和一个<ItemLink id="ae2:storage_bus" />。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/ore_line.snbt" />
  <BoxAnnotation color="#915dcd" min="8 0 0" max="10 2 1">
    你的主网络： 合成终端、合成CPU和存储
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="7 0 0" max="8 1 1">
    联邦样板供应器： 正面接联邦线缆， 背面接你的网络
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="3 1 0" max="6 4 1">
    第一个端点和它的子网： 富集仓把每块铁矿石变成两份铁粉
  </BoxAnnotation>
  <BoxAnnotation color="#5ccd78" min="0 1 0" max="3 4 1">
    第二个端点和它的子网： 电力熔炼炉把铁粉炼成铁锭
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

联邦界面里会显示你的主网络连着它的供应器映射的两个端点：

<FederationTopology>
  <Network key="main" label="主网络" color="#915dcd" column="0" row="0" details="合成CPU、终端|联邦样板供应器" />
  <Endpoint key="enriching" label="端点 · 富集" owner="main" energy="true" details="富集仓子网" />
  <Endpoint key="smelting" label="端点 · 熔炼" owner="main" energy="true" details="电力熔炼炉子网" />
</FederationTopology>

## 搭建

1. **放置供应器**， 正面接联邦线缆， 另一面接你的网络； 再把**两个端点**的正面都接到同一条线缆上。
2. **给每台机器搭子网**， 做法同[远程Mekanism粉碎机](mekanism-crusher.md)： 在端点上接一条ME线缆， 线缆上的存储总线对着机器顶面； 机器下面放一个漏斗， 把产物推进端点。 两个子网不能互相接触， 也不能接到你的主网络。
3. **设置每台机器的各面**： 物品方面顶面设为输入、底面设为输出； 能量方面把朝向它的能量立方的一面设为输入。 再把每个能量立方朝向机器的一面设为输出。
4. **给供应器放两个样板**： 一个处理样板从一块铁矿石到两份铁粉， 另一个从一份铁粉到一个铁锭。 在供应器的连线图里把第一个拖到富集仓的端点上， 第二个拖到电力熔炼炉的端点上。
5. **在主网络上下单铁锭**。 你的合成CPU会依次执行两步： 矿石送到富集仓， 铁粉回到你的网络， 再送到电力熔炼炉， 最后铁锭回来。

## 试一试

拿走电力熔炼炉下面的漏斗， 然后下单铁锭。 没有铁锭回来， 任务会等待。 把漏斗放回去， 任务就会完成。
