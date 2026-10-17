---
navigation:
  parent: index.md
  title: ME联邦光束转换器
  icon: ae2federation:beam_converter
  position: 125
categories:
- network infrastructure
item_ids:
- ae2federation:beam_converter
- ae2federation:creative_beam_converter
---

# ME联邦光束转换器

<Row>
  <BlockImage id="ae2federation:beam_converter" scale="6" />
  <BlockImage id="ae2federation:creative_beam_converter" scale="6" />
</Row>

[联邦域](../mechanics.md)靠FE运行。 它的交换机、路由器、线缆、样板供应器和处理端点每tick都消耗一点能量， 联邦域从它的光束转换器里取用。 转换器付不起时， 联邦域**停止**， 和超出[容量](../network-capacity.md)时一样： 各个网络照常独立工作， 但在重新获得FE之前， 不共享存储、合成、处理和能量。

<GameScene zoom="5" interactive={true} background="transparent">
  <ImportStructure src="../assets/beam_converter.snbt" />
  <BoxAnnotation color="#dddddd" min="3 1 0" max="4 3 1">
    一台1×1×2的光束转换器： 正面朝下对着线缆。 FE从其他任意面输入
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="5 0 0" max="8 2 1">
    网络A
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="3 1 1">
    网络B
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 联邦域的消耗

| 方块 | 每个方块每tick的FE |
|---|---|
| <ItemLink id="ae2federation:cable" /> | 0.5 |
| <ItemLink id="ae2federation:switch" />、<ItemLink id="ae2federation:router" /> | 2 |
| <ItemLink id="ae2federation:pattern_provider" />、<ItemLink id="ae2federation:processing_endpoint" /> | 2 |
| <ItemLink id="ae2federation:federation_p2p_tunnel" />、光束转换器 | 0 |

更大的交换机或路由器按每个方块2计算， 粗线缆里的每根线缆按0.5计算。 服务器可以按比例调整所有消耗， 也可以关闭这项要求； 关闭后联邦域不需要转换器。

## 尺寸

转换器是由转换器方块组成的实心长方体， 每个方块朝哪边放都可以。 它的正面截面必须和**联邦域里最大的线缆**相同， 长方体朝正面方向的长度是正面长边的两倍：

| 最大的线缆 | 转换器 |
|---|---|
| 单根线缆 | 1×1， 长2 |
| 1×2粗线缆 | 1×2， 长4 |
| 1×3粗线缆 | 1×3， 长6 |
| 2×2粗线缆 | 2×2， 长4 |
| 2×3粗线缆 | 2×3， 长6 |
| 3×3粗线缆 | 3×3， 长6 |

正面像交换机的面一样接入联邦域； 把转换器贴着线缆或任意联邦方块建造。 尺寸不对的转换器也会成形并接入联邦域， 但不供能。 加入更大的[粗线缆](../bundled-cables.md)后， 要建一台与之匹配的转换器。 空手右键没有成形的方块， 动作栏会说明原因； 手持方块右键则照常放置。

成形后， 转换器的正面是长度方向的一端： 多数方块朝向的那一端， 否则是贴着联邦线缆的那一端。 用扳手右键已成形的转换器， 可以把它前后调转。

## 能量

一台转换器是一个整体缓存， 每个方块提供16,000 FE。 用任意模组的线缆或发电机从正面以外的面输入FE， 接一根线缆就能充满整台转换器。 联邦域把所有匹配转换器的缓存加在一起， 从中平均取用。

缓存耗尽时联邦域停止， 并继续取用输入的能量。 连续超过30 tick能量充足后， 它重新运行， 就像AE2网络断电后重新启动一样。 新的联邦域只要转换器付得起， 就立即运行。

联邦域停止时， 它的线缆发出细细的红光， 拓扑界面变成暗红色。 拓扑界面的角落显示转换器的充满程度： 运行时为绿色， 消耗多于输入时为黄色， 停止后为红色。 用Jade查看转换器， 可以看到它的缓存和输入的FE； 与联邦域匹配时为绿色， 不匹配时为红色。

**创造ME联邦光束转换器**遵循相同的尺寸规则， 而且永远不会耗尽。

<RecipeFor id="ae2federation:beam_converter" />
