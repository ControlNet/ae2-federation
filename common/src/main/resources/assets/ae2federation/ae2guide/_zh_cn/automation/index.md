---
navigation:
  parent: index.md
  title: 联邦自动化
  icon: ae2federation:automation_interface
  position: 38
---

# 联邦自动化

<Row>
  <ItemImage id="ae2federation:automation_interface" scale="3" />
  <ItemImage id="ae2federation:creative_computing_device" scale="3" />
</Row>

联邦自动化为联邦域运行小程序。 程序读取联邦域的存储、合成、规则和周围的世界， 并据此操作： 发起合成任务、 在网络和方块之间搬运资源、 修改规则、 发出红石信号。

<FederationProgram src="../assets/programs/low_iron_alarm.ae2f" />

上面就是游戏里显示的一个程序。 **脚本**显示它的Python文本， **图**把同一个函数画成卡片和连线。 两者是同一个程序： 在任一视图里修改， 另一个视图也跟着变。

自动化由三部分组成：

* **程序**是Python文件， 在联邦界面的**程序**标签页里编写。
* 联邦域里的**计算设备**运行装在它上面的函数， 在**自动化**标签页里安装。
* <ItemLink id="ae2federation:automation_interface" />是程序接触世界的地方： 它前方的方块， 以及它发出的红石信号。

## 各页内容

* [编写程序](writing-programs.md)： 程序标签页、 脚本和图两种视图， 以及推送。
* [语言](language.md)： 程序可以使用的Python子集。
* [函数库](library.md)： `ae2`、`ae2f`和`mc`里的函数。
* [安装与触发](installing.md)： 把函数装到设备上， 以及什么会启动它。
* [算力与带宽](compute.md)： 一个安装占用生存计算设备多少资源。
* [示例程序](examples.md)： 可以直接照抄的程序。
* <ItemLink id="ae2federation:automation_interface" />和<ItemLink id="ae2federation:creative_computing_device" />。
