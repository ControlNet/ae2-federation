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
