---
navigation:
  parent: items/index.md
  title: ECO - N1
  icon: ae2federation:eco_computation_system_l1
  position: 471
categories:
- devices
item_ids:
- ae2federation:eco_computation_system_l1
---

# ECO - N1

<Row>
  <ItemImage id="ae2federation:eco_computation_system_l1" scale="3" />
  <ItemImage id="ae2federation:eco_computation_interface" scale="3" />
</Row>

最小的联邦计算系统， 比<ItemLink id="ae2federation:eco_computation_system_l4" />低一级。 它就是Neo ECO Prototype的[L1计算子系统](neoecoprototype:neoecoprototype_intro/computation_system.md)， 只是把<ItemLink id="neoecoprototype:simplify_computation_system" />换成ECO - N1联邦计算子系统主机； 它运行联邦程序， 而不是合成任务。 装了Neo ECO Prototype 1.4.0或更高版本才会出现这一页。

* **搭建**： 和L1结构相同， 只是主机换成我们的， 侧面（L1结构放接口的那个2x3端面）的任意位置放1到4个<ItemLink id="ae2federation:eco_computation_interface" />， 其余位置放外壳。 那里不能放Neo ECO Prototype自己的接口。 主机的结构搭建器会搭出带一个通讯接口的结构。
* **连接、能量、容量和ID**： 和其他联邦计算系统主机相同。 结构成型后， 每个接口接一根<ItemLink id="ae2federation:cable" />； ME网络接不进这个结构， 它也不是合成CPU。
* **算力**： 每个<ItemLink id="neoecoprototype:simplify_computation_parallel_core" />提供24 CU； 主机正后方的充能并行核心提供1,024 CU。
* **带宽**： 驱动器里每个<ItemLink id="neoecoprototype:simplify_computation_cell_1m" />提供1,572,864 B/s， 每个充能晶阵提供5,242,880 B/s。
* **安装数**： 每个<ItemLink id="neoecoprototype:simplify_computation_threading_core" />两个； 第一个线程核心位置上的充能线程核心提供16个。

以上是Neo ECO Prototype的默认值； 它的服务端配置（`l1_computation`）改动普通L1部件的数值时， 对N1和对L1主机一样生效。

它在工作台上制作， 配方与L1主机相同， 只是用<ItemLink id="ae2federation:nexus_processor" />代替运算处理器。
