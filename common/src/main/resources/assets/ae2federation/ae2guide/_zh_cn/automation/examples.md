---
navigation:
  parent: automation/index.md
  title: 示例程序
  icon: ae2federation:automation_interface
  position: 60
---

# 示例程序

下面每个程序都能直接运行。 把它粘贴到一个新程序里， 推送， 再按它要求的参数安装即可。

## 保持某种物品的库存

合成补足到目标数量所缺的部分。 正在运行的任务还要做出来的量算作已有库存， 所以进行中的任务不会被重复下单。 安装时触发选**定时**100刻， 或者对这种物品选事件`stock_changed`。

<FederationProgram src="../assets/programs/keep_stocked.ae2f" />

## 给熔炉上料

一个<ItemLink id="ae2federation:automation_interface" />面朝熔炉。 程序从上方放入粗铁， 从下方取出铁锭。

<FederationProgram src="../assets/programs/smelt_iron.ae2f" />

## 收割小麦

接口正对小麦本身， 和小麦同高， 耕地在小麦下面一格。 安装时对这个接口选事件`crop_grew`。

<FederationProgram src="../assets/programs/harvest_wheat.ae2f" />

## 电量充足时共享能量

主网络储存超过100,000 AE时， 打开主网络到农场网络的能量规则， 否则关闭。

<FederationProgram src="../assets/programs/share_power.ae2f" />

## 补写缺少的样板

从存储里取一张空白样板， 写上这种物品的样板， 放进面对着能做它某个配方的机器的样板供应器： 合成配方对应分子装配室， 熔炼配方对应熔炉。 已经有样板能做的物品会被跳过， 所以每张样板只写一次。 存储里需要有空白样板。 安装时填入这种物品， 触发选**定时**200刻。

<FederationProgram src="../assets/programs/learn_pattern.ae2f" />

## 所有CPU都忙时亮灯

联邦域里没有空闲的合成CPU时， 接口点亮它前方的灯。 安装时触发选**定时**20刻。

<FederationProgram src="../assets/programs/all_cpus_busy.ae2f" />

## 销毁过剩物品

接口前方的线缆顶面装着一个ME输出总线， 朝上对着垃圾桶。 存储里这种物品多于`keep`时， 程序把它填进总线的第一个过滤槽， 否则清空这个槽。 安装时填入这种物品， 并对它选事件`stock_changed`。

<FederationProgram src="../assets/programs/trash_surplus.ae2f" />

## 自动连接新的P2P通道

面朝接口的通道是输入端。 每次运行时， 同一网络上所有同类、尚未连接的通道都会被连成它的输出端， 所以之后新放的通道不用内存卡也会接上。 其他种类的通道和已经连接的通道保持不变。 安装时触发选**定时**100刻。

<FederationProgram src="../assets/programs/link_new_tunnels.ae2f" />

## 频道不足报警

联邦域里任何地方有设备在等频道时， 点亮灯。 返回的数量会显示在日志里。 安装时触发选**定时**100刻。

<FederationProgram src="../assets/programs/channel_alarm.ae2f" />
