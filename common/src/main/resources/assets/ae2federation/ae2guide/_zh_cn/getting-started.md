---
navigation:
  parent: index.md
  title: 入门
  icon: ae2federation:federation_logic_processor
  position: 10
---

# 入门

你需要两个各自有电的独立ME网络。 联邦不会取代AE2自己的线缆和控制器， 只是把本来就能单独工作的网络连起来。

## 1. 制作联邦逻辑处理器

每个联邦设备都需要<ItemLink id="ae2federation:federation_logic_processor" />。 在<ItemLink id="ae2:inscriber" />中用一个<ItemLink id="ae2:logic_processor" />和一份<ItemLink id="ae2:fluix_dust" />压制， 两者都会被消耗。

<RecipeFor id="ae2federation:federation_logic_processor" />

## 2. 连接两个网络

下面两种方式任选其一， 打开的都是同一个联邦界面。

### 两个网络紧挨着：桥接器

<RecipeFor id="ae2federation:bridge" />

把<ItemLink id="ae2federation:bridge" />装在第一个网络的线缆上， 让它的外侧接触第二个网络的线缆或设备。 两个网络仍然是分开的， 桥接器只是为联邦把它们连起来。

### 两个网络相距较远：路由器和联邦线缆

<Row>
  <RecipeFor id="ae2federation:cable" />
  <RecipeFor id="ae2federation:router" />
</Row>

让<ItemLink id="ae2federation:router" />的一个面接触第一个网络的ME线缆， 另一个面接触第二个网络的ME线缆。 一个路由器最多可接六个网络， 每面一个。 网络相距较远时， 给每个网络各放一个路由器， 再用<ItemLink id="ae2federation:cable" />把路由器连起来。 不要把两个路由器面对面直接贴在一起， 中间一定要有联邦线缆。

## 3. 打开共享

右键路由器或桥接器（站在八格以内）打开联邦界面。 界面把这个联邦域里的网络画成卡片。 选中一张网络卡片， 就会列出它和其他每个网络之间的规则， 每条规则有一个开关。 规则读作“网络A使用网络B的存储”： 它只在这个方向上生效， 反方向有自己的开关。

* 左键开关向前切换（禁用、启用、启用（可转出））， 右键向后切换。
* **存储**： 让一个网络查看、存入和取出另一个网络的物品和流体。
* **合成**： 让一个网络使用另一个网络的样板供应器。 开启它会同时开启同方向的存储。
* **ME能量**： 把两个网络的能量合成一个能量池； 每对网络只有一个开关。

## 4. 检查是否生效

在使用对方存储的那个网络上打开ME终端： 对方网络的物品会列在里面， 并且可以取出。 规则的状态显示在开关旁边： 生效时为绿色， 尚未生效时为黄色， 被阻止时为红色， 下面一行写着原因。 参见[排错](troubleshooting.md)。

下一步： [联邦的工作方式](mechanics.md) 和 [远程加工](remote-processing.md)。
