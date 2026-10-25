---
navigation:
  parent: items/index.md
  title: 联邦计算系统
  icon: ae2federation:eco_computation_system_l4
  position: 470
categories:
- devices
item_ids:
- ae2federation:eco_computation_system_l4
- ae2federation:eco_computation_system_l6
- ae2federation:eco_computation_system_l9
- ae2federation:eco_computation_interface
---

# 联邦计算系统

<Row>
  <ItemImage id="ae2federation:eco_computation_system_l4" scale="3" />
  <ItemImage id="ae2federation:eco_computation_system_l6" scale="3" />
  <ItemImage id="ae2federation:eco_computation_system_l9" scale="3" />
  <ItemImage id="ae2federation:eco_computation_interface" scale="3" />
</Row>

生存模式的计算设备。 它就是Neo ECO的[计算子系统](neoecoae:neoecoae_intro/computation_system.md)， 只是把Neo ECO的主机换成ECO - N4、N6或N9联邦计算子系统主机； 它运行联邦程序， 而不是合成任务。 需要Neo ECO 21.2.1或更高版本。

* **搭建**： 和Neo ECO的结构相同， 只有两处不同： 主机换成我们的， 以及Neo ECO放ME接口的位置改为计算子系统结构外壳。 不能使用网络交换模块。 主机的等级不能低于核心的等级。 主机的结构搭建器会搭出带一个通讯接口的结构。
* **连接**： 主机本身不接线缆。 侧面（Neo ECO放ME接口的那个2x3端面）的任意位置要放1到4个<ItemLink id="ae2federation:eco_computation_interface" />。 结构成型后， 每个接口的外侧接一根<ItemLink id="ae2federation:cable" />， 由此加入联邦域。 只有四个接口拼成2x2时， 才能接2x2粗线缆。 没有接口、放了第五个接口或接口放在其他面上时， 结构都无法成型。 ME网络接不进这个结构， 它也不是合成CPU。
* **能量和容量**： 不消耗FE（[能量](../mechanics/power.md)）。 整个系统在线缆的[容量](../mechanics/capacity.md)里计1； 通讯接口像线缆一样把联邦域传过去， 自身不计数。
* **算力**： 并行核心的加速器数， 4、6、9级每个核心分别为64、192、576 CU。
* **带宽**： 驱动器里闪存晶阵的字节数， 每个CE4、CE6、CE9分别为67,108,864、268,435,456、1,073,741,824 B/s。 手持闪存晶阵右键主机， 可以填满空的驱动器。
* **进程**： 每个安装是一个进程， 进程数等于线程核心的线程数。
* **ID和别名**： 空手右键主机， 可以查看、复制它的ID（例如`device-k3m9qa`）， 或者给它起别名。

安装的开销怎么计算， 见[算力与带宽](../automation/compute.md)。

每台主机在Neo ECO的集成工作站里制作， 配方与Neo ECO的主机相同， 只是用<ItemLink id="ae2federation:nexus_processor" />代替超导处理器。 通讯接口的配方与Neo ECO的计算子系统通讯接口相同， 只是用<ItemLink id="ae2federation:router" />代替ME接口， 用Nexus处理器代替运算处理器。
