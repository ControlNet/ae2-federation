---
navigation:
  parent: index.md
  title: 跨网络合成
  icon: ae2federation:processing_endpoint
  position: 30
---

# 跨网络合成

让另一个网络的机器替你完成合成任务， 有两种方式。

| | [另一个网络的样板供应器](ae2-providers.md) | [处理端点](processing-endpoints.md) |
|---|---|---|
| 样板放在 | 对方网络的AE2样板供应器里 | 你自己的<ItemLink id="ae2federation:pattern_provider" />里 |
| 需要的规则 | 合成， “你使用对方的” | 无 |
| 对方一侧 | 一个完整的ME网络， 按AE2的常规方式布置 | 机器旁的一个小子网络， 通过<ItemLink id="ae2federation:processing_endpoint" />供电 |
| 适用于 | 合成样板和处理样板 | 处理样板 |
| 合成CPU | 由你的CPU执行任务， 对方网络不需要 | 由你的CPU执行任务 |

* **使用另一个网络的样板供应器**： 适合对方网络本来就为自己合成， 而你也想向它下单的情况。 它保留自己的样板和机器， 你的终端会把它的样板和你自己的列在一起。
* **使用处理端点**： 适合机器由你供料、却位于单独网络上的情况， 例如前哨、合作伙伴的工坊， 或者一排你想分开管理的熔炉。 样板留在你的供应器里， 由你选择每个样板交给哪个端点执行。

<SubPages icons={true} />
