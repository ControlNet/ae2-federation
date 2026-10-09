# 线缆方块设计

## blockbench 工程文件

路径`tools/blockbench/projects/`

|文件名|解释|
|:---|:---|
|`cable_core`|线缆的核心体块，用于交叉位置|
|`cable_straight`|线缆直线|
|`cable_connector`|连接部位，如线缆和联邦样板供应器的连接|
|`cable_connector_straight`|连接部位，用于线缆直线和核心的连接|
|`cable_connector_small`|连接部位，用于线缆与联邦p2p的连接|
|`cable_display`|效果展示|

## 实现说明

线缆建模的核心类为`FederationCableBuilder`，`CableBakedModel`类用于烘焙模型与指定纹理，`CableShapes`用于创建模型碰撞箱。

- `cable_core`由`addDenseCore`方法实现
- `cable_connector_straight`的模型由`addDenseCableSizedCube`创建，由`addDenseConnection`实现；`cable_straight`由`addStraightDenseCableSizedCube`创建，由`addStraightDenseConnection`实现
- `cable_connector`（机器端盖）目前不再绘制：按下方建议，线缆与联邦样板供应器/联邦处理端点的连接已改为与联邦路由器相同的 dense 连接（使用`cable_connector_straight`），`addBigCoveredCableSizedCube`已移除
- `cable_connector_small`的模型由`addCoveredCableSizedCube`创建，由`addCoveredConnection`实现

为优化视觉效果，建议线缆 与联邦样板供应器/联邦处理端点的连接 和 与联邦路由器的连接 逻辑相同。（已于 2026-10-09 实现。）

