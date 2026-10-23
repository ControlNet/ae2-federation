---
navigation:
  parent: automation/index.md
  title: 函数库
  icon: ae2federation:automation_interface
  position: 30
---

# 函数库

程序通过三个命名空间接触游戏， 通过`fs`读写文件， 通过`net`访问网络。 程序标签页里的**函数库**面板列出同样的函数， 每个都附有一段简短示例。 标为**操作**的函数每次调用花一个游戏刻。 **长操作**以那一刻的操作开始， 之后运行等它完成； 等待期间不占用操作。

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
| `mc.wait(ticks)` | **长操作**： 暂停运行`ticks`刻 |

## fs： 文件

文件放在世界的工作区文件夹`<world>/ae2federation/workspace`里， 这个世界的所有程序共用它。 路径是相对路径， 名字之间用`/`分隔， 例如`"logs/iron.txt"`； 指向文件夹以外的路径会让运行停止。 一个文件最多存100,000个字符的文本， 整个文件夹最多16 MB。

服务器可以关闭文件功能： 在`config/ae2federation-server.toml`的`[automation]`部分设置`allowFileAccess = false`； 同一部分的`workspaceSizeMb`设置文件夹的大小。 关闭后， `fs`调用会以`PermissionError`停止运行。

| 函数 | 结果 |
| --- | --- |
| `fs.read(path)` | 文件的文本； 没有这个文件时为`None` |
| `fs.exists(path)` | 文件或文件夹是否存在 |
| `fs.list(path="")` | 文件夹里的名字， 按顺序排列； 文件夹以`/`结尾 |
| `fs.write(path, text)` | **操作**： 写入文件， 需要时创建所在的文件夹 |
| `fs.append(path, text)` | **操作**： 加到文件末尾， 文件不存在时先创建 |
| `fs.delete(path)` | **操作**： 删除文件或空文件夹； 本来就没有时为`False` |

## net： 网络

| 函数 | 结果 |
| --- | --- |
| `net.http(method, url, body="", headers={})` | **长操作**： 发送一个HTTP请求， 给出它的`Response` |
| `net.connect(url)` | **长操作**： 打开一个websocket连接； 已经打开时保持原连接 |
| `net.send(url, text)` | **长操作**： 在连接上发送一条文本消息 |
| `net.receive(url)` | 最早一条还没读的消息； 没有时为`None` |
| `net.connected(url)` | 连接是否打开 |
| `net.disconnect(url)` | **操作**： 关闭连接； 本来就没有时为`False` |

运行会等待回答， 等待不占用操作， 最多10秒。 任何状态码（包括404）都算回答； 没有回答、超时或回答超过100,000个字符时， 运行停止。 回答里的头部名字是小写的。 试运行不发送请求， 给出`Response(0, "", {})`。

websocket的URL以`ws://`或`wss://`开头。 连接属于安装： 两次运行之间保持打开， 安装被移除时关闭。 一个安装最多同时打开4个连接。 `net.connect`和`net.send`像`net.http`一样等待； 消息在后台到达， `net.receive`按顺序读取， 没有消息时为`None`。 没读的消息只保留最新的256条， 超过100,000个字符的消息会关闭连接。 关闭前到达的消息仍然可以读。 试运行不打开连接： `net.receive`给出`None`。 想在消息到达时启动运行， 把它安装在`ws_message`事件上。

在同一个`[automation]`部分， `allowHttp = false`关闭`net.http`， `allowWebsockets = false`关闭websocket函数并断开已打开的连接； 关闭后这些调用会以`PermissionError`停止运行。 `networkTimeoutSeconds`设置请求、连接和发送最多等待多久。

Python内置的`int`、`float`、`str`、`bool`、`list`、`dict`、`set`、`range`、`len`、`abs`、`min`、`max`、`sum`、`round`、`any`和`all`， 以及列表、字符串、字典和集合的方法， 都和Python中一样。 `print(...)`往安装的日志里写一行。
