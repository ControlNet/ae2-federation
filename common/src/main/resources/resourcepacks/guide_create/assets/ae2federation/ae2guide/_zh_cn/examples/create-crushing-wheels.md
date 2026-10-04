---
navigation:
  parent: examples/index.md
  title: Create粉碎轮
  icon: create:crushing_wheel
  position: 60
---

# Create粉碎轮

**目标**： 在主网络上请求沙砾， 而粉碎圆石的一对Create粉碎轮待在它们自己的小网络里。 这一页出现， 是因为安装了Create。

**需要**： 带合成CPU、合成终端并存有圆石的主网络； 一个<ItemLink id="ae2federation:pattern_provider" />； 一个<ItemLink id="ae2federation:processing_endpoint" />； <ItemLink id="ae2federation:cable" />； 两个<ItemLink id="create:crushing_wheel" />； 一个<ItemLink id="create:chute" />； 一个<ItemLink id="ae2:storage_bus" />； 驱动粉碎轮的旋转动力。

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/create_crushing_wheels.snbt" />
  <BoxAnnotation color="#915dcd" min="5 0 0" max="7 2 1">
    主网络： 合成终端、合成CPU和存储
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4 0 0" max="5 1 1">
    联邦样板供应器： 前面接联邦线缆， 背面接你的网络
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 1 0" max="3 2 1">
    处理端点： 前面朝下接线缆， 顶上是溜槽， 背面接子网络的线缆
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 2 0" max="3 3 1">
    缝隙下方的溜槽： 接住粉碎轮的沙砾， 向下推进端点
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2.125 4 0.125" max="2.875 4.3 0.875">
    两个粉碎轮缝隙上方的存储总线， 朝下
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="1 3 1" max="2 4 2">
    粉碎轮后面的传动杆， 接到你的旋转动力
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 搭建

1. **放置供应器**， 前面接联邦线缆， 另一个面接你的网络。
2. **放置端点**， 前面接同一条线缆， 再在端点顶上放一个溜槽。
3. **放置粉碎轮**： 两个粉碎轮并排放在溜槽上方， 中间隔一格空位， 空位正对溜槽。
4. **让粉碎轮转起来**： 用旋转动力驱动它们向内转、彼此相对， 和Create的思索场景演示的一样。 端点不会给Create机器供能。
5. **在端点的另一个面上搭粉碎轮的子网络**： 一段ME线缆向上接到两个粉碎轮缝隙上方的存储总线， 总线朝下。 供应器使用端点期间， 端点会用你网络的电给这个子网络供电。 子网络不能连到你的主网络。
6. **把产物送回来**。 粉碎轮只会把产物交给Create自己的溜槽和传送带， 其他情况下会把它们作为掉落物扔出来。 粉碎轮下方的溜槽把沙砾向下推进端点。
7. **加入样板**。 粉碎轮把一个圆石粉碎成一个沙砾。 编码一个从一个圆石到一个沙砾的处理样板， 放进供应器， 然后在供应器的接线图里把它拖到端点上。
8. **在主网络上请求沙砾**。 圆石进入子网络的存储， 也就是粉碎轮； 沙砾经过溜槽和端点回到你的网络。

## 试一试

让粉碎轮停下， 然后请求沙砾。 什么都不会被粉碎， 没有沙砾回来， 任务一直等待。 让粉碎轮重新转起来， 任务就会完成。
