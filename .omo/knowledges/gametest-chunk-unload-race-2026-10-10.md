# GameTests: "chunk unloaded" means its block entities are gone (2026-10-10)

The 0.0.6 release run failed one shard: `projectioncpulateafterrestart` ("The debt stays after the restart ... expected
2, but was 0"). Locally it failed about 4 runs in 5, alone or in a batch; Quick correctness had passed it by luck.

## Cause (test-only)

`OtherDimensionSite.loaded()` used `ServerLevel.hasChunk`, which only checks the chunk holder's ticket level
(`ServerChunkCache.chunkAbsent`): it turns false the moment the ticket is released. The real unload runs later,
from `ChunkMap.scheduleUnload` once the holder's save future is ready (`processUnloads` / unload queue): only then
does `ServerLevel.unload` call `LevelChunk.clearAllBlockEntities` (onChunkUnloaded, setRemoved), and AE2's nodes
leave their grids. In between, the "unloaded" CPU was still a live, busy `CraftingCPUCluster` on the consumer grid;
the returning stone went into it, the job finished and its final output went to storage, so the debt was correctly
0. Production behaved right; the test's precondition was not met. A temporary log in `CraftingReturnRouter.waiting`
(removed) showed the live cluster (`busy=true waitingFor=2 destroyed=false`).

## Fix

`releaseTickets()` records the region's block entities (`getChunkNow(...).getBlockEntities()`), and `loaded()` stays
true while any of them is not `isRemoved()`. All six tests that wait for an unload through it now wait for the real
one: `projectioncpulateafterrestart` 10/10 (was ~1/5), and `endpointacrossdimensionsunloaded`,
`projectionnetherproviderunloaded`, `p2pacrossdimensionschunkunload`, `projectioncpuoutofsight`,
`projectioncpuoutofsightotherjob` 5/5 each.

Lesson: to wait for a chunk unload in a GameTest, wait for its block entities to be removed (or the AE2 node to leave
the grid), never for `hasChunk`/`isLoaded` alone.
