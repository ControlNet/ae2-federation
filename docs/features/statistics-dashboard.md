# Statistics dashboard

Status: implemented on `dev` (2026-10-14). The server keeps the history (`4f2c0db5`); the domain screen's statistics
page shows it. The former idea is preserved as an [archived discussion](../archive/ideas/statistics-dashboard.md).
The design is option B ("metric list, large chart and a box below it") on the GUI design canvas. Version 2 gives each
metric its own lower box.

## Behavior

- **Where.** The third tab on the domain screen's left rail. It shows the domain's member networks only.
- **Ranges.** 2 min (1 s points), 10 min (5 s), 1 h (1 min) and 24 h (10 min), picked at the top right. History
  uses wall-clock time and is saved with the world, so a restart keeps the last 24 hours.
- **Metrics.** Six cards on the left. Each has a name, the newest figure and a sparkline. Clicking a card shows that
  metric in the large chart, and the box below it shows what that metric has besides:

  | Metric | Large chart | Box below |
  | --- | --- | --- |
  | **ME power** | Power use (AE/t), stacked by network | Stored power: each network's %, plus the shared pool |
  | **Stored** | Each network's own storage, by item or by network | The items the chart draws: search, tick, pin |
  | **Moved** | What moved between networks, by network or by item | The keys that moved most, with their routes |
  | **Crafting** | Jobs the networks' CPUs started per minute | Each network's jobs, and the CPUs crafting now |
  | **Busy CPUs** | Busy CPUs per network | Every CPU: what it crafts, progress and time, or idle |
  | **Channels** | Channels per network | Each network's channels now, peak and lowest |

- **Stored by item.** The chart stacks the picked items, the most held lowest. "Everything else" is one grey band on
  top when it is picked. The box below lists the domain's 16 most-held items of the shown kind, plus the pinned ones.
  - **Pick.** Top 6 (the default, with everything else), Pinned, or None. Ticking an item makes the pick your own.
  - **Search.** Searches every item the networks hold, by name (and by pinyin with JustEnoughCharacters, see
    [compatibility](../compatibility/matrix.md#client-mods)). An item without history says "not recorded yet" and
    can be pinned.
  - **Pin.** A pinned item's stock is kept on every member network, whether or not it is among the most held; its
    history starts when it is pinned. Up to 16 per domain. Pinning needs the right to edit the domain, and the last
    edit wins.
  - **By network** stacks each network's stock of the picked items (all of it while everything else is picked).
- **Kept by item.** Each network's stock is read every 10 s from its own storage (Federation mounts excluded). The
  server keeps, per network and resource type, the 16 items it holds most of, the pinned ones and, up to 48, items it
  tracked before and still holds, so an item slipping out of the top keeps its line.
- **Moved.** What moved from one network to another: Storage rules, crafting inputs pushed to another network's
  provider, and crafting results coming back. With one network chosen, inflow is drawn above the axis and outflow
  below. By item, the chart stacks the 6 items that moved most, with the rest in grey.
- **Power use.** AE2's average drain of each network. In a shared energy pool AE2 counts a drain on the network whose
  cells gave the power, so the page splits the pool's drain by each network's idle draw: an estimate.
- **Jobs started.** Counted from each member CPU, looked at every tick. A job that starts and ends within one tick is
  missed, and so is one in a network's first second (members are looked up once a second). When a local job ends is
  not observable without a mixin; jobs that used another network's patterns are counted as done or cancelled exactly,
  and marked along the top of the chart.
- **Networks.** The chip row filters to one network or shows all of them. Networks keep the topology's colours; items
  get colours of their own.
- **Gaps.** Time the server was not running (stopped, or a paused singleplayer game), and spans where a network was not
  loaded, are hatched. They are never drawn as zero. The tooltip says "no record" or "no data". Seconds a long tick
  ran through are observed: counters read zero there, gauges are missing.
- **Tooltip.** Hovering a chart shows a crosshair and each network's or item's figure at that point.
- **No rules.** Without a Storage or Crafting rule, the moved chart says so instead of drawing an empty graph.

## Cost

- **Server, recording:** about 33 µs per second on average (`statisticsSecond` in `perfStorageProjection`). Reading
  one network's 1000-type stock takes about 0.27 ms, once every 10 s. Watching the members' CPUs is a field read per
  busy CPU per tick.
- **Memory and saves.** A tracked item costs about 9 KB of memory and about 5 KB in the save. The worst case is 8
  networks × 2 resource types × 48 items, about 7 MB of memory and about 4 MB written per autosave. A typical domain
  tracks a small fraction of that.
- **While the page is open:**
  - The server rebuilds the page's data every second on the 2 min range, and every 5 s on the others.
  - It sends only while a player has the page open, only the chosen range, and only what the chosen metric needs.
  - Rows name networks by index, and keys go in one shared table.
  - Worst case, 8 networks with all 56 pairs moving items and fluids every second (`StatisticsPayloadTest`, after
    warm-up):

    | Metric | Size (compressed) | Build |
    | --- | --- | --- |
    | Power, CPUs, channels | 120 KB (47 KB) | 0.5 ms |
    | Stored by item (32 items on every network) | 134 KB (92 KB) | 1.0 ms |
    | Moved by item | 196 KB (71 KB) | 5.3 ms |

    A typical domain with a few active pairs costs a fraction of that. The search list (up to 1000 items) is read
    from storage and sent only while the search box has text.
- **Not measured:** ME power moved between networks has no AE2 event and would need a mixin on AE2's energy service.

## Evidence

- Unit tests: `StatisticsBookTest`, `SecondClockTest`, `TimelineTest`, `TopKeysTest`, `StatisticsPayloadTest`.
  GameTests: `StatisticsGameTests` (real traffic, jobs started, stock by item; save and load of history and pins).
- UI scenario `ui.mapping` (screenshots `ui-showcase-statistics*`, one per metric and view). It runs 75 s of scripted
  Storage-rule traffic in the eight-network test world. The routes and amounts are test-world activity, like the test
  world's names and rules.
- Notes: `.omo/knowledges/statistics-dashboard-observables-2026-10-14.md`.
