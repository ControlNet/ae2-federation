# Statistics dashboard

Status: implemented on `dev` (2026-10-14). The server keeps the history (`4f2c0db5`); the domain screen's statistics
page shows it. The former idea is preserved as an [archived discussion](../archive/ideas/statistics-dashboard.md).
The design is option B ("metric list, large chart and ranking") on the GUI design canvas.

## Behavior

- **Where.** The third tab on the domain screen's left rail. It shows the domain's member networks only.
- **Ranges.** 2 min (1 s points), 10 min (5 s), 1 h (1 min) and 24 h (10 min), picked at the top right. History
  uses wall-clock time and is saved with the world, so a restart keeps the last 24 hours.
- **Metrics.** Six cards on the left. Each has a name, the newest figure and a sparkline. Clicking a card shows that
  metric in the large chart:
  - **ME power:** each network's stored %, plus the shared pool when networks share one.
  - **Stored:** each network's own storage (Federation mounts excluded), stacked so the top edge is the domain total.
    It is counted every 10 s per network. Items and fluids are switched above the chart; other addon kinds get
    their own chips.
  - **Moved:** what Storage rules moved between networks per second. With one network chosen, inflow is drawn above
    the axis (coloured by source) and outflow below (coloured by destination).
  - **Crafting:** patterns pushed to another network's CPUs per minute, with finished and cancelled jobs marked.
  - **Busy CPUs** and **Channels:** sampled once a second.

  When the per-network figures do not fit on a card, it shows the lowest power % or the domain total instead.
- **Networks.** The chip row filters to one network or shows all of them. Colours follow the topology's network
  colours.
- **Gaps.** Time the server was not running, and spans where a network was not loaded, are hatched (grey, or in the
  network's colour). They are never drawn as zero. The tooltip says "no record" or "no data".
- **Tooltip.** Hovering the large chart shows a crosshair and each network's figure at that point.
- **Most moved.** The items and fluids that moved most over the range, each with its source → destination pair.
  With one network chosen, only the pairs involving it are listed.
- **No rules.** Without a Storage or Crafting rule, those charts say so instead of drawing an empty graph.

## Cost

- **Server, recording:** about 33 µs per second on average (`statisticsSecond` in `perfStorageProjection`). Reading
  one network's 1000-type stock takes about 0.27 ms, once every 10 s.
- **While the page is open:**
  - The server rebuilds the page's data every second on the 2 min range, and every 5 s on the others.
  - It sends only while a player has the page open, and only the chosen range.
  - Rows name networks by index, and ranked keys go in one shared table.
  - An extreme domain (8 networks, all 56 pairs moving items and fluids every second) sends about 140 KB per refresh,
    about 50 KB compressed. Building that takes about 6.5 ms after warm-up, 3.7 ms of it the rankings
    (`StatisticsPayloadTest`). A typical domain with a few active pairs costs a fraction of that.
- **Not measured:** ME power moved between networks has no AE2 event and would need a mixin on AE2's energy service,
  so the page shows stored power, not power flow.

## Evidence

- Unit tests: `StatisticsBookTest`, `TimelineTest`, `TopKeysTest`, `StatisticsPayloadTest`; GameTests:
  `StatisticsGameTests`.
- UI scenario `ui.mapping` (screenshots `ui-showcase-statistics`, `-hover`, `-storage`, `-network`). It runs 75 s of
  scripted Storage-rule traffic in the eight-network test world. The routes and amounts are test-world activity,
  like the test world's names and rules.
- Notes: `.omo/knowledges/statistics-dashboard-observables-2026-10-14.md`.
