# Neo ECO tier codes (checked 2026-10-20, Neo ECO 21.2.1)

Read from `assets/neoecoae/lang/en_us.json` and `zh_cn.json` in the Neo ECO jar. Every ECO block or cell is named
`ECO - <subsystem letter><part letters><tier>`; the tier is 4, 6 or 9 (registry suffix `l4`, `l6`, `l9`).

| Code | What | zh_cn |
|---|---|---|
| L4/6/9 | Extensible Storage Subsystem Controller | 可拓展存储子系统主机 |
| LT4/6/9 | High Density Energy Cell | 高密度能量元件 |
| LE4/6/9 | Storage Matrix (item, fluid, FE, chemical, mana, MEGA ...) | 存储矩阵 |
| F4/6/9 | Extensible Crafting Controller | 可拓展合成子系统主机 |
| FT4/6/9 | Parallel Core (crafting) | 并行核心 |
| C4/6/9 | Extensible Computation Subsystem Controller | 可拓展计算子系统主机 |
| CT4/6/9 | Parallel Core (computation) | 并行核心 |
| CM4/6/9A | Threading Core | 线程核心 |
| CE4/6/9 | Flash Crystal Matrix (the computation cell) | 闪存晶阵 |
| "- C4" suffix | Cooling System Controller | 冷却系统控制器 - C4 |

- First letter: the subsystem (L storage, F crafting, C computation). Neo ECO does not say what the letters stand for.
- One letter alone is the subsystem's controller; T, E and M...A mark parts.
- Neo ECO is not fully consistent: Air, Experience and Soul matrices are LE1/LE2/LE3 in English but LE4/6/9 in Chinese.

## Our controllers

The Federation computation controllers were first named C4/C6/C9, the same codes as Neo ECO's own computation
controllers. The user chose N4/N6/N9 (cb5101d0): N is unused by Neo ECO and matches the Nexus parts. Registry names stay
`eco_computation_system_l4|l6|l9`.
