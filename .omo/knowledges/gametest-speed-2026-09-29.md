# GameTest run speed

## Why one GameTest took about 100 s

A single run went through these stages:
- Gradle configuration: about 10 s.
- Server start: about 11 s.
- The test itself: 1–3 s.
- The save at shutdown: 25–78 s.

The shutdown save was slow for this reason:
- `GameTestServer` inherits `MinecraftServer.forceSynchronousWrites()`, which returns true.
- Every region write therefore uses `DSYNC`. On this VM's virtio disk each write takes about 5 ms, and a save writes thousands of chunks.
- `GameTestServer` never reads `server.properties`, so setting `sync-chunk-writes=false` there has no effect.

## Fix 1: turn off synchronous writes (applies to every GameTest server, CI included)

`test/mixin/GameTestServerChunkWritesMixin` (TEST-ONLY) makes `forceSynchronousWrites()` return false when the server is a `GameTestServer`. The world is thrown away after the run, so durability doesn't matter. A single test now takes about 27–32 s in total, of which the save is about 4 s.

## Fix 2: development batch mode, many tests in one server

Commands:
- `python3 tools/dev_gametests.py <id...>`
- `python3 tools/dev_gametests.py --manifest [--match text] [--no-isolate]`

Measured: the full required manifest (152 tests) takes about 2.5 min, including solo reruns. One server per test took about 4.4 h before Fix 1.

The tool runs `runGameTestServer -PfederationGameTestSelection=batch -PfederationGameTestId=a,b,...`. `FederationTestMod` registers exactly those ids and throws if an id is unknown.

The following hooks act only under the batch selection and are all TEST-ONLY.

`test/mixin/GameTestSequentialBatchMixin` gives each test its own batch, in the requested order. By default the vanilla server runs every test of one batch name at the same time, and these tests share level-wide registries. Around each batch it does three things:
- Before the batch it removes every player from the player list. Mock players from `makeMockServerPlayerInLevel` have embedded connections that negotiated no mod payloads. AE2 packets sent to them in a later test crash the server (`Payload ae2:assembler_animation may not be sent to the client!`).
- Before the batch it sets `ae2federation.testId` to that one test. Evidence writers and authority receipts compare against this property ("Authority receipt test ID does not match the selected child").
- After the batch it runs `StructureUtils.clearSpaceForStructure` on the test's area. Otherwise the finished test's AE2 grids and Federation blocks keep ticking and count in later tests' level-wide observations (for example Crafting backend discoveries).

`test/mixin/GameTestSequenceFailureMixin` handles exceptions from a test's sequence. Vanilla sequences catch only `GameTestAssertException`, so any other exception crashes the server. In batch mode it fails only the test that threw.

## How the tool reads results

- The GameTest server logs only failures, as `]: <name> failed at <pos>! <reason>`.
- The tool counts a test as PASS when its batch `'<batch>/<name>:0'` started, no failure line names it, and either a later batch started or the run printed `GAME TESTS COMPLETE`.
- A crash (for example a block entity tick exception) marks the running test as CRASH, with the innermost `Caused by`. The tool then starts a new server with the tests that come after it.
- Every FAIL and CRASH from the batch then runs again alone (`positive` selection, the way CI runs it):
  - **SHARED** means the test passes alone, so only another test's leftover level state failed it.
  - "(also fails alone)" means a real failure.
- A known SHARED cause is level SavedData that outlives each test: rule stores and network identities. `policysparsescale` and `policydeletereconnect` used to count leftover rules and tombstones; since 2026-10-01 they assert only what they add over the count they start with, so they pass in a batch too. (`energydisconnectnosource` no longer exists; energy bindings were removed.)
- Make a level-wide count in a test relative to the count it starts with, so it neither depends on nor breaks a shared level.

Batch mode is for fast local iteration only:
- It doesn't run the Gradle evidence verifiers (`federation-qa.gradle`).
- Tests share one level.

The required gate remains `python3 tools/required_gametests.py`: one server per test, and a test passes only on exactly one "All 1 required tests passed". That gate also doesn't run the evidence verifiers.

## Real defects batch mode found

`CableFacePort.tick()` queried its `BlockCapabilityCache` after `destroy()`: a neighbour change marked the destroyed port dirty again. NeoForge then throws "Do not call getCapability on an invalid cache", which crashes the server. A destroyed port now skips resolution until the cable initializes again.

## Intermittent CI fixture timeouts (seen 2026-09-28 to 2026-09-30)

- On CI, `tools/required_gametests.py` sometimes fails a required GameTest with its first readiness message, e.g.
  `automationrejectduplicatedemand ... Waiting for duplicate-demand topology`, `observenativeflowonce ... Waiting for
  native directional energy fixture`, `subscriptionrejectstalegeneration ... Waiting for stale-generation relationship`.
- It looks instant ("1 GAME TESTS COMPLETE IN ~0.7 s") but is a real timeout: the idle GameTest server ticks unthrottled,
  and the CI log shows about 200 progress lines (one per 20 ticks) = the 4000-tick `timeoutTicks`. Locally the same test
  passes after about 6 lines (~120 ticks).
- Not random: it is decided by the test origin, which vanilla `GameTestServer.startTests` picks at random. Replaying the
  CI origin fails every time: `-PfederationGameTestOrigin=9734410,2895050` (TEST-ONLY property, read by
  `CompatibilityGameTestPlacementMixin`). CI origins that reproduced: automationrejectduplicatedemand 9734410,2895050 and
  608922,-10346086; observenativeflowonce -12807526,2017388; subscriptionrejectstalegeneration 9457322,-8590470.
- Root cause (confirmed 2026-09-30 with per-node identity logging): the fixture's network straddles chunk borders. AE2's
  `TickHandler.readyBlockEntities` readies new block entities chunk by chunk, in `Long2ObjectOpenHashMap` key order, so
  the order depends on the coordinates. A node readied before its neighbours exist forms its own Grid, and
  `NetworkIdentityGridService.addNode` mints it a durable lineage from that Grid's fresh id. Only nodes inside one
  `NativeIdentityInitialization` scope (one cable bus) are provisional. When the pieces connect, the Grid holds two
  network ids, `IdentityReconciler` returns `AMBIGUOUS_MERGE` ("Merge pending"), and `networksSettled()` never holds.
- Beyond tests, the same thing can happen whenever nodes without saved lineage are readied in separate batches but belong
  to one network. That follows from the code; no in-game test has shown it yet. Examples: structure or schematic
  placement across chunk borders, and first load of an existing AE2 world after installing the mod.
- Fix (user chose "provisional within one tick"): `NetworkIdentityGridService` records the server tick in which each
  node without saved lineage was minted (weak `MINTED_TICK`, kept when AE2 moves the node between Grids during a merge).
  Until that tick ends, such nodes can adopt a single NetworkId: the one held by an established node, else a
  boundary's hint, else one they already carry. Settlement reports are unchanged: a new network is `SETTLED` at once,
  and a merge of two established networks is still `AMBIGUOUS_MERGE`. Not covered: fragments readied in different
  ticks, such as an old world loading chunk by chunk.
- Do not make "only new nodes this tick" report `PARTIAL_LOAD` until the tick ends. Tried and reverted: consumers such
  as `MultipartBridgePart` refresh only on node events (`onSaveChanges`, `onStateChanged`, `onGridChanged`), and a tick
  passing fires none. T33 `ui.graph-controls` then timed out on "the related domains settle": both networks were
  SETTLED, but the Bridge never republished domain membership. A merge inside the tick already fires `onGridChanged`,
  so consumers republish with the unified id.
- Regression test: `identitysametickfragments` places chest, chest, then the middle cell in one tick inside one chunk,
  so AE2 readies the ends first at every origin. It was red before the fix (AMBIGUOUS_MERGE). `identityinitializationorder`
  now seeds its `stable` node with saved lineage: a node minted in the test's tick no longer counts as established.
- Read failures from the `required-gametest-log` artifact: `gh run download <id> -n required-gametest-log -D <dir>`, then
  `grep "failed at" <dir>/gametest.log`.
