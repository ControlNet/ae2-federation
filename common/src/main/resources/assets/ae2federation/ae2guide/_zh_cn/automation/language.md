---
navigation:
  parent: automation/index.md
  title: 语言
  icon: ae2federation:automation_interface
  position: 20
---

# 语言

程序用朴素的过程式Python编写。 能用的部分和Python 3的行为一致； 不能用的部分会在所在行报告“不支持”。

## 可以使用

* `def`， 每个参数都要写类型； 默认值、文档字符串和注释。 返回值的类型可以不写， 由检查器根据`return`推断。
* `if`、`elif`、`else`、`while`、`for x in xs`、`break`、`continue`、`return`、`pass`。
* 赋值， 包括`+=`等算术赋值。
* `int`、`float`、`str`、`bool`、`None`、列表、`range`、`stack.amount`这样的字段、下标和切片， 以及列表和字符串的方法。
* 关键字参数， 例如`ae2f.select(port, side="down")`。
* 类只含带类型的字段、不含方法， 当作记录使用： `Order("minecraft:iron_ingot", 64)`。

## 不能使用

导入、异常（`try`、`raise`）、带方法的类、lambda、推导式、生成器、字典、集合、元组、嵌套函数和`global`。 函数只能定义和调用， 不能作为值存储或传递。

## 类型

每个值都有静态类型， 在程序运行前检查。 除了Python自己的类型， 还有：

| 类型 | 含义 |
| --- | --- |
| `Item`、`Fluid`、`Resource` | 一个ID， 例如`"minecraft:iron_ingot"`； `Resource`可以是任何种类， 包括附属模组的资源 |
| `Interface` | 一个自动化接口， 用ID（如`"interface-k3m9qa"`）或别名表示 |
| `Network` | 联邦域里的一个网络， 用ID（如`"network-082a5c"`）或名字表示 |
| `Block` | 用`ae2f.select`通过接口选中的方块 |
| `Stack` | 一种资源和数量， `mc.storage`列出的就是它 |
| `Side`、`Capability`、`RuleMode`、`FilterMode` | 固定的词， 例如`"down"`、`"storage"`、`"on"`、`"allow"` |

数量用各资源自己的单位： 物品按个， 流体按mB。

## 多个函数和文件

一个文件里可以有任意多个函数。 同一文件里的函数直接用名字调用， 其他文件的函数写成`文件名.函数名(...)`， 不需要导入。 以`_`开头的名字是文件私有的， 不能安装。

<FederationProgram src="../assets/programs/iron_tools.ae2f" function="refill_iron" />

## 程序怎样运行

* 读取（例如`ae2.amount`）不花时间。 **操作**（例如`ae2.craft`或`mc.set_redstone`）花一个游戏刻， 所以一次运行每刻最多做一个操作。 大量搬运或合成应在一次调用里完成。
* `mc.wait(ticks)`让运行暂停`ticks`刻； `mc.wait(0)`不花时间。
* 一次运行每刻最多执行10,000步， 剩下的下一刻继续， 所以`while True:`不会卡住服务器。
* 整数是64位的， 一个列表或字符串最多100,000项， 调用最多嵌套1,000层。 一个程序文件最多64,000个字符。
