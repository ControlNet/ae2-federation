---
navigation:
  parent: automation/index.md
  title: 函数库
  icon: ae2federation:automation_interface
  position: 30
---

# 函数库

程序通过三个命名空间接触游戏。 程序标签页里的**函数库**面板列出同样的函数， 每个都附有一段简短示例。 标为**操作**的函数每次调用花一个游戏刻。

`network`参数可以省略： 省略时， 调用作用于联邦域里的每个网络， 各自从自己的存储读写， 与规则共享了什么无关。

## ae2： 存储与合成

| 函数 | 结果 |
| --- | --- |
| `ae2.amount(resource, network)` | 存了多少 |
| `ae2.storage(network)` | 存储的全部内容， 以`Stack`列出 |
| `ae2.craftable(resource, network)` | 是否有样板能合成它 |
| `ae2.crafting(resource, network)` | 正在运行和已请求的任务还会送入存储多少 |
| `ae2.patterns(network)` | 所有能合成的东西 |
| `ae2.free_cpus(network)` | 空闲的合成CPU数 |
| `ae2.energy(network)` | 储存的AE |
| `ae2.craft(resource, amount, network)` | **操作**： 请求合成任务； 没有样板能合成或没有空闲CPU时为`False` |
| `ae2.cancel(resource, network)` | **操作**： 取消这个安装在该网络上发起的任务 |
| `ae2.import_from(block, resource, amount, network)` | **操作**： 从方块搬进存储 |
| `ae2.export_to(block, resource, amount, network)` | **操作**： 从存储搬进方块 |
| `ae2.move(source, target, resource, amount)` | **操作**： 在联邦域的两个网络之间搬运 |

AE2会在接下来几刻里规划任务， 规划好后交给空闲的CPU。 规划时缺材料， 或者到那时CPU都忙， 任务就不会开始， `ae2.crafting`也随之回落。 任务的最终产物从请求起计入`ae2.crafting`， 直到送进存储。

## ae2f： 联邦

| 函数 | 结果 |
| --- | --- |
| `ae2f.select(interface, reach=1, side=None)` | 接口前方1到4格处的方块， 可指定从哪一面访问 |
| `ae2f.networks()` | 联邦域里的网络 |
| `ae2f.online(network)` | 网络现在是否可用 |
| `ae2f.rule(provider, consumer, capability)` | 规则的模式： `"off"`、`"on"`或`"reexport"` |
| `ae2f.set_rule(provider, consumer, capability, mode)` | **操作**： 设置规则 |
| `ae2f.set_filter(provider, consumer, capability, mode, resources)` | **操作**： 设置存储规则开启时放行哪些资源 |

## mc： 世界

| 函数 | 结果 |
| --- | --- |
| `mc.amount(block, resource)` | 方块里有多少 |
| `mc.storage(block)` | 方块里的全部内容 |
| `mc.block_id(block)` | 方块的ID， 例如`"minecraft:chest"` |
| `mc.state(block, property)` | 方块状态属性， 例如作物的`"age"`； 没有时为`None` |
| `mc.redstone(block)` | 方块收到的红石信号， 0到15 |
| `mc.fe(block)` | 方块里储存的FE |
| `mc.time()` | 游戏时间， 以刻计 |
| `mc.move(source, target, resource, amount)` | **操作**： 在两个方块之间搬运 |
| `mc.set_redstone(interface, level)` | **操作**： 从接口正面发出信号， 一直保持到再次设置 |
| `mc.break_block(block)` | **操作**： 破坏方块； 掉落物进入联邦域的存储 |
| `mc.place_block(block, item)` | **操作**： 用联邦域存储里的物品放置方块 |
| `mc.move_fe(source, target, amount)` | **操作**： 在两个方块之间搬运FE |
| `mc.wait(ticks)` | **操作**： 暂停运行`ticks`刻； 不占用本刻的那一个操作 |

Python内置的`int`、`float`、`str`、`bool`、`list`、`range`、`len`、`abs`、`min`、`max`、`sum`、`round`、`any`和`all`， 以及列表和字符串的方法， 都和Python中一样。 `print(...)`往安装的日志里写一行。
