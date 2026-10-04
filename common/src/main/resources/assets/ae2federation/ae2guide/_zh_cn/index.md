---
navigation:
  title: AE2 联邦
  icon: ae2federation:router
  position: 900
---

# AE2 联邦（AE2 Federation）

<Row>
  <ItemImage id="ae2federation:router" scale="3" />
  <ItemImage id="ae2federation:bridge" scale="3" />
  <ItemImage id="ae2federation:pattern_provider" scale="3" />
  <ItemImage id="ae2federation:processing_endpoint" scale="3" />
</Row>

AE2联邦把彼此独立的ME网络连接起来， 让它们共享存储、自动合成、加工机器和ME能量； 每个网络仍保留自己的频道、控制器和合成CPU。 在你打开之前什么都不会共享， 而且每项权限只在一个方向上生效。

* [入门](getting-started.md)： 合成零件， 连接你的头两个网络。
* [联邦的工作方式](mechanics.md)： 联邦域、规则、共享能量和各项限制。
* [远程合成](remote-processing.md)： 向另一个网络的样板供应器下单， 或把处理样板发给它的机器。
* [使用示例](examples/index.md)： 共享仓库、装配工坊和外包机器的完整搭建。
* [排错](troubleshooting.md)： 联邦界面里各条提示的含义。

## 物品与方块

* <ItemLink id="ae2federation:federation_logic_processor" />： 所有联邦设备都要用到的材料。
* <ItemLink id="ae2federation:bridge" />： 直接连接两个相邻的网络。
* <ItemLink id="ae2federation:router" />： 把最多六个网络接入一个联邦域。
* <ItemLink id="ae2federation:cable" />： 远距离连接路由器、联邦样板供应器和处理端点。
* <ItemLink id="ae2federation:pattern_provider" />和<ItemLink id="ae2federation:processing_endpoint" />： 在属于其他网络的机器上执行处理样板。
