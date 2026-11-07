# Statistics memory at 1000 subnets (2026-11-07)

Review finding #12 (estimate) measured with JUnit (Runtime used heap after System.gc, many copies alive; approximate).
Steady state = every point of every range filled once (444 points per ranking).

| structure | before | after |
|---|---|---|
| TopKeys per route, 1 key/point | 87 KB (175 MB at 2000 routes) | 11 KB (22 MB) |
| TopKeys per route, 5 keys/point (1000x10 bus farm) | 187 KB (374 MB) | 32 KB (65 MB) |
| TopKeys per route, 31 keys/point | 919 KB (1.8 GB) | 173 KB (346 MB) |
| TopKeys, a day of churn (5 of 200 keys/s) | 575 KB | 128 KB |
| Saved CompoundTags per ranking | 444 (888k per autosave at 2000 routes) | at most 4 (one per range) |
| Timeline, new / idle for an hour | 9.2 KB / 9.2 KB | 0.2 KB / under 4.5 KB (day range only) |

- TopKeys keeps each range's points in flat arrays (key refs + amounts, width = most keys a point holds, max 2x cap);
  `prune` releases ranges with nothing to show and narrows the width. Trim ties now keep the key added first (before:
  HashMap order); nothing else in the output changes.
- Timeline allocates a range on first write and `prune` releases it once nothing in it can be shown again.
- `StatisticsSavedData` FORMAT 4: a ranking is one tag per range with flat buckets/sizes/keys/amounts arrays.
- addAll timing (JUnit): 20 routes x 500 keys/s 8.3 -> 3.4 ms per game second; 2000 routes x 5 keys/s ~2.6-3 ms both.
- Not changed: key timelines are still kept a day after the key leaves the tracked set (evicting earlier would change
  the day range); `pollJobs` still polls every tick (STARTED counts jobs per tick; a slower poll misses short jobs).
- Pinned by `StatisticsFootprintTest` (heap bounds) and `persistence/StatisticsSavedDataTest` (tag count, round trip).
