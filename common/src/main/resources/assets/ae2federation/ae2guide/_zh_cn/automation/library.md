---
navigation:
  parent: automation/index.md
  title: 函数库
  icon: ae2federation:automation_interface
  position: 30
---

# 函数库

程序通过三个命名空间接触游戏， 通过`fs`读写文件， 通过`net`访问网络。 程序标签页里的**函数库**面板列出同样的函数， 每个都附有一段简短示例。 标为**操作**的函数每次调用花一个游戏刻。 **长操作**以那一刻的操作开始， 之后运行等它完成； 等待期间不占用操作。 你自己写的函数调用了长操作时， 在补全和图里也标为长操作。

`network`参数可以省略： 省略时， 调用作用于联邦域里的每个网络， 各自从自己的存储读写， 与规则共享了什么无关。

## ae2： 存储与合成

| 函数 | 结果 |
| --- | --- |
| `ae2.amount(resource, network)` | 存了多少 |
| `ae2.storage(network)` | 存储的全部内容， 以`Stack`列出 |
| `ae2.craftable(resource, network)` | 是否有样板能合成它 |
| `ae2.crafting(resource, network)` | 正在运行和已请求的任务还会送入存储多少 |
| `ae2.craftables(network)` | 所有能合成的东西 |
| `ae2.cpus(network)` | 合成CPU列表， 以`Cpu`列出， 带上各自正在运行的任务 |
| `ae2.free_cpus(network)` | 空闲且接受自动化请求的合成CPU数 |
| `ae2.energy(network)` | 储存的AE |
| `ae2.channels(network)` | 每个网络的频道情况， 以`Channels`列出 |
| `ae2.craft(resource, amount, network, cpu=None)` | **操作**： 请求合成任务， 给了`cpu`就用这个名字的CPU； 没有样板能合成或没有可用的空闲CPU时为`False` |
| `ae2.cancel(resource, network)` | **操作**： 取消这个安装在该网络上发起的任务 |
| `ae2.import_from(block, resource, amount, network)` | **操作**： 从方块搬进存储 |
| `ae2.export_to(block, resource, amount, network)` | **操作**： 从存储搬进方块 |
| `ae2.move(source, target, resource, amount)` | **操作**： 在联邦域的两个网络之间搬运 |

AE2会在接下来几刻里规划任务， 规划好后像对待其他自动化请求一样挑选空闲的CPU： 只接受玩家请求的CPU和容量不够的CPU都不会用。 规划时缺材料， 或者到那时没有这样的CPU， 任务就不会开始， `ae2.crafting`也随之回落。 任务的最终产物从请求起计入`ae2.crafting`， 直到送进存储。

给了`cpu`时， 任务在这个名字的空闲CPU上运行， 选放得下任务的最小那个， 只接受玩家请求的CPU不会用。 在铁砧里改过名的合成单元， 名字就是它所在CPU的名字。 没有CPU叫这个名字时， 运行以`ValueError`停下。

`Channels`给出网络`network`、 频道模式`mode`（`"default"`、`"x2"`、`"x3"`、`"x4"`或`"infinite"`）、 控制器状态`controller`（`"none"`、`"online"`或`"conflict"`）、 已用频道数`used`、 需要频道却没有分到的设备数`missing`， 以及是否正在启动`booting`。 没有ME控制器的网络最多只给出八个频道， 需要频道的设备一旦更多就一个也不给， 这时`missing`会算上所有这样的设备。

## ae2： 设备设置

这里的`block`是接口前方的设备： 在线缆上时， 是所选那一面的部件（默认是朝向接口的那个）； 否则就是方块本身， 例如ME接口或ME样板供应器。 附属模组的设备也一样。

| 函数 | 结果 |
| --- | --- |
| `ae2.settings(block)` | 设备的各项设置， 以`Setting`列出， 带`name`、 当前值`value`和可选值`values`； 没有设置的方块给出空列表 |
| `ae2.setting(block, name)` | 某项设置的值， 设备没有这项设置时为`None` |
| `ae2.set_setting(block, name, value)` | **操作**： 修改一项设置， 效果和设备界面里的按钮相同 |
| `ae2.priority(block)` | 设备的优先级， 没有优先级时为`None` |
| `ae2.set_priority(block, priority)` | **操作**： 设置优先级 |
| `ae2.config(block)` | 按顺序列出配置或过滤槽位， 以`Stack`列出； 空槽位为`None` |
| `ae2.set_config(block, slot, resource=None, amount=1)` | **操作**： 把资源放进第`slot`个槽位（从0数起）； 不给资源时清空该槽位 |

设置名沿用AE2自己的名字， 值写成小写。 补全会列出设置名， 每个都附有设备界面里对应按钮的标题， 列表下方写着哪些设备有这项设置； 填值时只列出前面那个参数所指设置的可选值。 设备有哪些设置由设备自己决定， 所以附属模组的设置也能用， `ae2.settings`也会列出来。 设备没有的设置或值、 没有优先级或配置槽位的设备、 超出最后一个的槽位， 都会让运行以`ValueError`停下， 并说明设备有什么。 现在够不到的方块给出空列表或`None`， 对它的修改给出`False`； 槽位不接受的资源也给出`False`， 例如物品过滤槽里的流体。 终端的显示设置（例如排序）不在其中。

`ae2.config`列出设备的全部槽位， 包括界面上要装容量卡才显示的那些； 设备暂时不用的槽位也会保留放进去的东西。 过滤槽位不记数量； ME接口的槽位记的是要备货的数量。

| 设置 | AE2按钮 | 值 | 设备 |
| --- | --- | --- | --- |
| `access` | 输入/输出模式 | `read`、`write`、`read_write` | ME存储总线 |
| `auto_export` | 自动输出 | `yes`、`no` | 压印器 |
| `blocking_mode` | 阻挡模式 | `yes`、`no` | ME样板供应器 |
| `condenser_output` | 物质聚合器 - 输出 | `trash`、`matter_balls`、`singularity` | 物质聚合器 |
| `copy_mode` | 复制模式 | `clear_on_remove`、`keep_on_remove` | 元件工作台 |
| `craft_only` | 合成行为 | `yes`、`no` | ME输出总线 |
| `craft_via_redstone` | 合成发信模式 | `yes`、`no` | ME标准发信器 |
| `crafting_scheduling_mode` | CPU自动分配模式 | `any`、`player_only`、`machine_only` | 合成单元 |
| `filter_on_extract` | 操作过滤 | `yes`、`no` | ME存储总线 |
| `fullness_mode` | 操作模式 | `empty`、`half`、`full` | ME IO端口 |
| `fuzzy_mode` | 模糊匹配 | `ignore_all`、`percent_99`、`percent_75`、`percent_50`、`percent_25` | ME输入总线、ME输出总线、ME存储总线、ME成型面板、ME标准发信器、ME接口 |
| `inscriber_input_capacity` | 输入堆叠上限 | `one`、`four`、`sixty_four` | 压印器 |
| `inscriber_separate_sides` | 自动化访问模式 | `yes`、`no` | 压印器 |
| `lock_crafting_mode` | 锁定合成 | `none`、`lock_until_pulse`、`lock_while_high`、`lock_while_low`、`lock_until_result` | ME样板供应器 |
| `operation_mode` | 传输方向 | `fill`、`empty` | ME IO端口 |
| `overlay_mode` | 区块加载显示模式 | `yes`、`no` | 空间锚 |
| `pattern_access_terminal` | 样板管理终端 | `yes`、`no` | ME样板供应器 |
| `place_block` | 方块成型 | `yes`、`no` | ME成型面板 |
| `redstone_controlled` | 红石模式 | `ignore`、`low_signal`、`high_signal`、`signal_pulse` | ME输入总线、ME输出总线、ME IO端口 |
| `redstone_emitter` | 红石模式 | `low_signal`、`high_signal` | ME标准发信器、ME能源发信器 |
| `scheduling_mode` | 调度模式 | `default`、`roundrobin`、`random` | ME输出总线 |
| `storage_filter` | 报告不可交互的物品 | `none`、`extractable_only` | ME存储总线 |

## ae2： 样板

这里的样板供应器是联邦域里某个网络上的样板供应器， 和样板管理终端列出的一样， 附属模组的样板供应器也算。

| 函数 | 结果 |
| --- | --- |
| `mc.recipes_for(item)` | 能做出该物品的配方， 以`Recipe`列出： 合成、 切石、 锻造和处理配方 |
| `ae2.providers(network)` | 样板供应器， 以`Provider`列出 |
| `ae2.provider_at(block)` | 选中方块处的样板供应器； 不是本联邦域的样板供应器时为`None` |
| `ae2.provider_patterns(provider)` | 样板供应器里的样板， 每个都以它的`Recipe`列出 |
| `ae2.write_pattern(provider, recipe, items={}, substitutes=False)` | **操作**： 用存储中的一张空白样板写下配方， 放进样板供应器的空槽位 |

合成、 切石和锻造样板都在样板供应器旁边的分子装配室里完成， 分子装配室需要你自己在那里搭好。 处理样板把输入推进样板供应器面对的机器， 机器的产物怎样回到网络由你自己搭建， 和你亲手编码的样板一样。 样板供应器的`targets`列出它面对的机器， 配方的`machines`列出能做这个配方的机器， 两边都有的机器就能找出可以用这个配方的样板供应器。

`Recipe`给出`id`、 种类`kind`（`"crafting"`、`"stonecutting"`、`"smithing"`或`"processing"`）、 配方类型`type`、 产物`outputs`， 以及按槽位排列的`inputs`， 每个槽位列出它接受的物品堆： 合成网格的九个槽位按行排列， 切石机一个槽位， 锻造台依次是模板、 基础物品和附加物品， 处理配方则是它的各项输入。 有序配方放在网格左上角， `width`和`height`给出它的大小； 其他配方这两项为0。 `machines`列出能做这个配方的机器， 合成类配方是`ae2:molecular_assembler`。 `byproducts`列出不一定每次都产出的产物， 样板从不把它们算作产物。 AE2写不成样板的配方不会列出， 例如地图复制和盔甲纹饰； 产物带额外数据的配方也不列出， 因为程序无法只用ID指明它。

处理配方包括熔炉（`minecraft:smelting`）、 高炉（`minecraft:blasting`）和烟熏炉（`minecraft:smoking`）的配方。 它们的样板只放要烧的物品， 熔炉的燃料需要你自己供应。 营火配方不列出， 因为样板供应器没法把物品放到营火上。

`ae2.write_pattern`从联邦域的存储里取一张空白样板， 写成配方对应种类的样板。 `items`按槽位编号为接受多种物品的槽位选定物品， 例如`{0: "minecraft:birch_planks"}`； 其余槽位用存储里存得最多的那种， 都没有时用它接受的第一种。 `substitutes=True`就是AE2的“允许替代”。 存储里没有空白样板、 样板供应器没有空槽位， 或者现在够不到样板供应器时， 调用给出`False`， 也不会用掉空白样板。 槽位不接受的物品会让运行以`ValueError`停下。 从样板读出的处理配方没有`id`， 照它读出来的样子写： `inputs`和`outputs`原样不变， 每个槽位按上面的规则选定放入的物品堆。 处理样板有1到81项输入、 1到27项产物， 超出这个范围会让运行以`ValueError`停下。

`ae2.provider_patterns`把由配方写成的样板读作那个配方， 与`mc.recipes_for`给出的相等； 处理样板读出来没有`id`和`type`， 输入按样板供应器推出的顺序逐项列出， 所以照样再写一次会得到同一个样板。 含有带额外数据的物品（例如水瓶）的处理样板不会列出， 因为它的记录会指向另一种物品。 `Provider`有`id`（之后的调用用它指明这个样板供应器）、 铁砧改的名字`name`（没有时为`None`）、 `targets`、 在样板管理终端里的分组`group`、 自身的物品`block`、 `network`、 `x`、`y`、`z`和`dimension`、 样板槽位数`slots`和其中空着的`free`、 它的样板能做出的`makes`， 以及是否显示在终端里的`in_terminal`。

## ae2： P2P通道

这里的P2P通道是联邦域里某个网络上的P2P通道： AE2自己的、 附属模组的， 或者承载网络属于这个联邦域的联邦P2P通道。

| 函数 | 结果 |
| --- | --- |
| `ae2.tunnels(network)` | P2P通道， 以`Tunnel`列出 |
| `ae2.tunnel_at(block)` | 选中方块那一面上的P2P通道； 不是本联邦域的P2P通道时为`None` |
| `ae2.link_tunnel(input, output)` | **操作**： 像内存卡一样把输出端连到输入端 |
| `ae2.unlink_tunnel(tunnel)` | **操作**： 解除通道的连接 |

`ae2.link_tunnel`和内存卡的做法相同： 先让输入端成为输入端， 它没有频率或原本是输出端时给它一个新频率， 再让输出端使用这个频率。 种类不同的输出端会变成输入端那一种通道， 和对它使用内存卡时一样。 和AE2里一样， 通道只能在同一个网络上连接： 两个通道在不同网络上时， 运行会以`ValueError`停下。 `ae2.unlink_tunnel`让通道回到刚放下时的样子， 即没有频率的输入端， 所以输入端解除后， 它的输出端就没有输入端了。 现在够不到通道时， 两者都给出`False`。

`Tunnel`有`id`（它所在的方块和那一面， 之后的调用用它指明这个通道）、 种类`type`（通道的物品）、 铁砧改的名字`name`（没有时为`None`）、 频率`frequency`（没有连接时为0）、 是否为输出端`output`、 连接到的通道的id `linked`（输入端的各个输出端， 或输出端的输入端）、 是否在工作`active`（有能量也有频道）， 以及`network`、 `x`、`y`、`z`、 `dimension`和`side`。

重新连接联邦P2P通道会改变它承载的联邦线缆， 和用内存卡时一样： 它可能把两个联邦域连成一个、 把一个分开， 或者让一段线缆超过[容量](../mechanics/capacity.md)， 使那个联邦域停止。

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
| `mc.break_block(block)` | **操作**： 破坏方块； 掉落物进入联邦域的存储， 放不下的掉在原地 |
| `mc.place_block(block, item)` | **操作**： 用联邦域存储里的物品放置方块 |
| `mc.move_fe(source, target, amount)` | **操作**： 在两个方块之间搬运FE |
| `mc.recipes_for(item)` | 能做出该物品、 且AE2能写成样板的配方； 见“ae2： 样板” |
| `mc.wait(ticks)` | **长操作**： 暂停运行`ticks`刻 |

## fs： 文件

文件放在世界的工作区文件夹`<world>/ae2federation/workspace`里， 这个世界的所有程序共用它。 路径是相对路径， 名字之间用`/`分隔， 例如`"logs/iron.txt"`； 指向文件夹以外的路径会让运行停止。 一个文件最多存100,000个字符的文本， 整个文件夹最多16 MB、 10,000个文件和文件夹。 只读写普通文件； 路径上是别的东西（例如管道）时， 运行以`OSError`停止。

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

运行会等待回答， 等待不占用操作， 最多10秒。 任何状态码（包括404）都算回答； 没有回答、超时或回答的文本或头部超过100,000个字符时， 运行停止。 一个请求最多发送100个头部， 总共最多100,000个字符。 回答里的头部名字是小写的。 试运行不发送请求， 给出`Response(0, "", {})`。

websocket的URL以`ws://`或`wss://`开头。 连接属于安装： 两次运行之间保持打开， 安装被移除时关闭。 一个安装最多同时打开4个连接。 `net.connect`和`net.send`像`net.http`一样等待； 消息在后台到达， `net.receive`按顺序读取， 没有消息时为`None`。 没读的消息只保留最新的256条， 总共最多1,000,000个字符， 超过100,000个字符的消息会关闭连接。 关闭前到达的消息仍然可以读， 重新连接后也可以； 一个安装最多保留4个已关闭连接的未读消息， 关闭最早的先被丢弃。 等待结束或运行停止时还没发完的消息会关闭连接。 试运行不打开连接： `net.receive`给出`None`。 想在消息到达时启动运行， 把它安装在`ws_message`事件上。

在同一个`[automation]`部分， `allowHttp = false`关闭`net.http`， `allowWebsockets = false`关闭websocket函数并断开已打开的连接； 关闭后这些调用会以`PermissionError`停止运行。 `networkTimeoutSeconds`设置请求、连接和发送最多等待多久。

Python内置的`int`、`float`、`str`、`bool`、`list`、`dict`、`set`、`range`、`len`、`abs`、`min`、`max`、`sum`、`round`、`any`和`all`， 以及列表、字符串、字典和集合的方法， 都和Python中一样。 `print(...)`往安装的日志里写一行。
