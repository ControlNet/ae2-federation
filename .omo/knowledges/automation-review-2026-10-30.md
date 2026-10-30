# Federation Automation review (2026-10-30)

Read-only review of `feature/function-system` at 556d7a45, asked by the user after the 进程 rename. Five Codex
(`gpt-6-astra`, read-only) reviews split by area (language, runtime, outside IO, client, ECO - N); briefs and raw
reports were kept in the session scratchpad only. Every item marked **confirmed** was traced in the code by Claude;
**plausible** items are the reviewer's trace, not re-checked line by line. The user then asked to fix everything,
keeping per-run fixed bandwidth shares (item "elastic shares": guide text changed, code kept).

## Crash or hang of the server thread

- **confirmed** `Ops.repeat` (`Ops.java:357`): `[] * 10**18` skips `grow()` for an empty list, then loops `n` times
  inside one node. The step budget is only checked between nodes, so the tick never ends.
- **confirmed** A push that adds a required parameter: installations keep their old argument list, `Runner`'s
  constructor evaluates the missing parameter's `null` default (`Runner.java:108`) and the NPE passes
  `ProgramScheduler.start`'s `catch (IllegalArgumentException)` into the server tick.
- **confirmed** `ScriptParser` has no nesting limit: about 20,000 nested `-` or `(` under the 64,000-character cap
  overflow the stack. The upload is saved before `changed()` parses it, so the file stays and every index rebuild
  hits it again.
- **confirmed (code read)** `print` joins all arguments before `Ops.grow` (`Evaluator.java:224`): 2,000 references to
  one 100,000-character string allocate 200M characters.
- plausible (reviewer ran it): `str.upper` (ß doubles) and `str.split` skip the 100,000 cap; recursive records
  (`a.next = a`) overflow `repr`/`==`; a chain of records holding 100,000-character strings has no aggregate memory
  cap; `list(s)` on a long string is quadratic (`offsetByCodePoints` from 0 per item); `net.http` headers have no
  count or total size cap.

## Wrong behaviour in normal play

- **confirmed** After a server restart nothing runs: `ProgramScheduler.tickAll` only ticks an existing service and
  only GUI, command, redstone and device-removal paths call `get()`. Every/Condition/Event installations stay idle
  until someone opens the tab or runs a command. No GameTest restarts the server.
- **confirmed** Stale device records: `AutomationInterfaceBook.claim` keys by ID only, so a record whose block entity
  stored another ID stays at the same position; `ProgramScheduler.loaded` resolves a record by position and
  `instanceof ComputingHost` without comparing `host.id()`. The stale record's installations run on the live device
  and its budget is counted separately (capacity counted twice). This explains the `device-shqvr8` record seen in the
  N1 check world (2026-10-25) after a forced server kill: devices live in overworld SavedData, the ID in the chunk.
- **confirmed** A run keeps the domain it started in (`ProgramHost` captures domain and catalog); the scheduler only
  checks the device still has some domain. After a cable cut, the old run keeps acting on the domain it left.
- **confirmed** `mc.break_block` simulates each drop alone against the same free space and ignores the real inserts
  (`DomainCalls.java:373-385`): two new drop types with one free type slot lose one.
- **confirmed** A condition's purity is checked only in `setTrigger`; a later push can make it act, and
  `checkCondition` runs it with a real host.
- **confirmed** Typed strings (`Capability`, `RuleMode`, `FilterMode`, `Side`) are checked only as constants. Values
  from `fs.read`/`net`/concatenation reach `DomainCalls` defaults: an unknown capability becomes **power**
  (`DomainCalls.java:443`), an unknown mode `off`, filter `all`, side ignored. `HttpMethod` alone is checked at run
  time. The checker comment at `Checker.java:544` promises a run-time check.
- **confirmed** Elastic shares: the guide (`compute.md`) says shares change whenever installations or the device
  change; the design note and code keep each run's per-call limit from its start (`ProgramHost.limitMoves`). A loop
  started alone keeps the whole share; one started at 0 stays at 0.
- **confirmed** `ae2.craft` checks free CPUs, not pending plans: a loop can queue unbounded AE2 calculations;
  uninstalling does not cancel pending plans; plans finishing with no free CPU are dropped silently.
- plausible: `ae2.cancel` matches a job by (CPU, output) and can cancel a player's job that reused the CPU before
  pruning; `ae2.move` rollback ignores what the source took back (power-limited loss); program-caused events are lost
  for installations iterated earlier in the same tick; push re-prices reservations while old runs keep running.

## Client data loss

- **confirmed** One `ScriptEditor` serves every file and LDLib2 `TextArea.setValue` keeps `historyStack` (private,
  no clear API): undo after switching files puts the other file's text in and autosaves it under the new name.
- **confirmed** Upload acknowledgement writes the uploaded text to the local copy (`copies.sent`) without re-reading:
  an outside-editor save inside that window is overwritten.
- **confirmed** Delete clears `dirty` before the server answers; a refused delete (installed program) lets the next
  index refresh overwrite the unsent edits.
- **confirmed** `LocalCopies.key` maps every non-ASCII character to `_`: Chinese world names of the same length
  (`新的世界`, `测试世界`) share one local folder.
- plausible: local copy and bases written in place (not temp + move); `ClientPrograms` keeps the previous server's
  index until the new one arrives; a text over 64,000 characters leaves `sending = true`; `dryWaiting` stays true if
  the tab is left during a dry run; alias acknowledgement reopens a closed alias screen; a graph field blurred after a
  remote refresh commits against the new text by a reused node ID.

## Server boundary

- plausible: program requests (`install`, `run`, `trigger`, `stock`, `dry`) take device/domain IDs without checking
  the player has that domain's screen open; unfinished uploads, the library and empty workspace files have no
  aggregate quota; `dry` requests are not throttled and recompile the whole library each time; a dry-run error text
  can exceed the packet string limit; a reloaded file over 64,000 characters cannot be fetched by the editor.

## Python divergences

- **confirmed** float `//` uses `Math.floor(x / y)`: `1.0 // 0.1` gives 10.0, CPython 9.0.
- **confirmed** `str.join` rejects dict and set though the checker accepts them.
- plausible: `range` membership and slicing overflow `long` silently.

## Fixes (2026-10-30, feature/function-system)

- **b7c265cf** language and runtime limits: `Meter` (a step compares or writes out at most 5,000,000 values; a run
  holds at most 4,000,000 items, walked every 2,000,000 new ones), parser nesting 200 / blocks 100, `repeat`/`print`/
  `upper`/`split` caps, cyclic `repr` and `==`, one-pass `list(str)`, CPython float `//`, BigInteger `range`, `join`
  of dict/set, typed strings checked at run time.
- **670beb05** runtime: scheduler starts on the first server tick (restart runs), double-buffered events, runs stop on
  domain change or push, kept arguments and conditions re-checked per start, any start fault logged instead of
  reaching the tick, stale device/interface records released and their installations moved, merged networks' short
  IDs resolve, craft plans counted against free CPUs and dropped on uninstall, cancel by job link, move/break_block
  never lose items.
- **497d5923** server boundary: device/installation/domain requests need that domain's open Federation screen
  (`FederationDomainPolicyMenu.openDomain`; library-wide requests unchecked, commands and `handle` unchecked); 4
  uploads per player, cleared on logout; dry runs every 10 ticks per player and the compiled library cached by texts;
  library caps 256 programs / 4,000,000 characters, files over 64,000 skipped on load; answers cut to 32,000
  characters; `net.http` at most 100 headers and 100,000 header characters either way; workspace at most 10,000 files
  and folders.
- Not reproduced: a websocket handshake ending after its wait. The JDK's `connectTimeout` already ends it (test
  `aHandshakeThatEndsAfterItsWaitLeavesNothingOpen` passes without a fix). Only a narrow race remained (our
  `orTimeout` firing just before the JDK's); `onOpen` now aborts a socket that arrives after the connection closed.
- **4ebc52ac** client: `ScriptEditor` overrides `pushHistory`/`onExecuteCommand` with its own `HistoryStack`, cleared on
  file switch (LDLib2's is private and never cleared); ui.automation checks it, failing without the fix (before/after
  image `build/undo-check/undo-before-after.png` in the worktree). Upload answers and server texts never overwrite an
  outside save; refused uploads/deletes keep `dirty`; >64k refused locally as an answer; `dryWaiting` reset on leave;
  atomic local writes; folder keys hashed when characters were replaced; `ClientPrograms.reset()` on logout; alias
  answers (`View.answer`) never open a screen or replace newer typing; graph fields commit only against the text they
  were drawn from.
