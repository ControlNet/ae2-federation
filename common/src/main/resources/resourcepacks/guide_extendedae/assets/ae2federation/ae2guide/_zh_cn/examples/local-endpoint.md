---
navigation:
  parent: examples/index.md
  title: 本地模式的端点
  icon: extendedae:ex_pattern_provider
  position: 70
---

# 本地模式的端点

**目标**： 不用联邦样板供应器、不用联邦线缆、也不用规则， 直接用ExtendedAE的扩展样板供应器驱动一座待在自己小网络里的熔炉。 样板供应器贴在<ItemLink id="ae2federation:processing_endpoint" />的前面放置时， 端点就进入**本地模式**。 这一页出现， 是因为安装了ExtendedAE。

**需要**： 带合成CPU、合成终端、存储、电源并存有圆石的主网络； 一个<ItemLink id="extendedae:ex_pattern_provider" />； 一个处理端点； 一座熔炉、一个漏斗和一个<ItemLink id="ae2:storage_bus" />； 一个<ItemLink id="ae2:quartz_fiber" />。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/local_endpoint.snbt" />
  <BoxAnnotation color="#915dcd" min="6 0 0" max="8 2 1">
    主网络： 合成终端、合成CPU、驱动器和能源元件
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="5 0 0" max="6 1 1">
    主网络上的扩展样板供应器： 向每一面推送， 端点的前面也在其中
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4 0 0" max="5 1 1">
    处理端点： 前面朝向供应器， 其他面接熔炉的子网络
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="5 1.375 0.375" max="5.375 1.625 0.625">
    石英纤维： 把你网络的电分给子网络， 但不把两个网络连成一个
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3.125 2 0.125" max="3.875 2.3 0.875">
    熔炉顶上的存储总线： 原料直接进入熔炉
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0 0" max="4 1 1">
    熔炉下方的漏斗： 把石头推进端点的侧面
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="3 0 0" max="5 3 1">
    熔炉的子网络
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

本地模式的端点不属于任何联邦域， 所以右键它只会单独、只读地打开它的面板， 模式显示为“本地”。 它为你的网络做的事仍然是这样： 输入送到它那里， 结果送回来， 子网络不会通过它共享你网络的电：

<FederationTopology>
  <Network key="main" label="主网络" color="#915dcd" column="0" row="0" details="合成CPU、终端|扩展样板供应器" />
  <Endpoint key="furnace" label="端点 · 熔炉" owner="main" energy="false" details="本地模式|熔炉的子网络" />
</FederationTopology>

## 搭建

1. **把扩展样板供应器放在主网络上**。
2. **贴着它放置端点**， 让端点的前面接在供应器上： 放置时点击哪个方块， 前面就朝向哪个方块。 新放的供应器向每一面推送， 所以不用任何设置就会推进端点的前面， 端点随即切换到本地模式。
3. **在端点的其他面上搭熔炉的子网络**： 一段ME线缆从端点顶面通到熔炉顶上的存储总线， 熔炉下方放一个漏斗， 推进端点的侧面。 子网络不能连到你的主网络。
4. **给子网络供电**。 本地模式的端点不共享电力。 在主网络的一段线缆和子网络的一段线缆之间放一个石英纤维： 它分享电力， 又让两个网络保持分开。
5. **给熔炉加燃料**。 存储总线只会填顶部的槽位。
6. **加入样板**。 编码一个从一个圆石到一个石头的处理样板， 和你的其他烧炼样板一起放进扩展样板供应器： 它能放的样板比AE2的供应器多得多（参见[ExtendedAE的指南](extendedae:epp_intro/extended_pattern_provider.md)）。 它推送的每个样板都经过端点进入同一座熔炉。
7. **在主网络上请求石头**。 圆石进入子网络的存储， 也就是熔炉； 石头经过漏斗和端点回到供应器， 再从那里进入你的网络。

## 本地模式如何工作

* 端点前面接着什么， 决定了它的模式。 另一个网络的样板供应器向前面推送时， 无论是方块还是线缆上的部件， 无论来自AE2还是附属模组， 都会选中本地模式。 联邦线缆、交换机、路由器、联邦样板供应器的前面， 或者什么都不接， 都会选中联邦模式。
* 推进前面的输入进入子网络的存储。 机器把结果推进端点的其他任意一面， 就会送回那个供应器。
* 端点只有一个前面， 所以只服务一个供应器。 处于本地模式时， 任何联邦样板供应器都不能映射它； 把供应器换成联邦线缆， 它就又能被映射了。

## 试一试

用扳手点击扩展样板供应器的顶面， 然后请求石头。 供应器现在只向下推送， 背离端点： 端点退出本地模式， 圆石到不了熔炉， 任务一直等待。 再点击顶面两次： 供应器重新向每一面推送， 端点回到本地模式， 任务就会完成。
