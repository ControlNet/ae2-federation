# feature/function-system review (2026-11-24)

Read-only review of `feature/function-system` at 4b967c95, after dev was merged in (739723b5), asked by the owner
("继续review吧"). Done by reading the code directly, without review agents. Scope (assistant choice), in order:
1. the 2026-11-15 fixes (9db7adbf), which no one had reviewed;
2. dev features since the 2026-11-01 merge that reach the branch's code without a git conflict;
3. every server-bound request the branch adds.

Nothing was changed. Two throwaway probes were run and then removed: a CPython differential test and a GameTest.

## Findings

1. **A stopped domain still shares through programs (confirmed with a throwaway GameTest).**
   - A domain over cable capacity, or one whose Beam Converters cannot pay, "stops as a whole". The guide
     (`mechanics/capacity.md`) says its networks "share nothing until the count fits again".
   - `DomainCalls` honours that only in `ae2f.online`. `grids()` takes the registry's members and never checks
     `FederationDomainRegistry.stopped`. So `ae2.move`, `ae2.import_from` / `ae2.export_to` with a network, `ae2.craft`
     on another network, and the reads (`ae2.amount`, `ae2.storage`, `ae2.cpus`) all act across the stopped domain.
     `ProgramScheduler` keeps running there too.
   - Probe: `programMovesBetweenItsNetworks` with `CableCapacityLimit.set(true, 1)` returned
     `False False True 4`. Both networks read offline, yet four iron moved.
   - My recommendation, not a decision: while the device's domain is stopped, pause its runs and conditions, as for a
     device without its installations, and log why. Calls could fail instead, but a pause matches "stops as a whole".
2. **Any player can restart the server-wide recipe sync (code traced).**
   - The Recipe sync page's "Rescan all" and per-category Rescan reach `ViewerScans.rescan` through the domain screen's
     binding (`FederationDomainPolicyMenuHolder.acceptSyncRequest`). There is no permission check and no cooldown.
   - Each new number from any player with a domain screen open starts a new round (`newRound` drops every lease), or
     rereads a whole category. The normal button is enough; no modified client is needed.
   - `recipeViewerUploads = OPERATORS` does not cover it.
   - My recommendation: the same rule as uploads (operators when the switch says so, the world's owner always), plus a
     server-wide cooldown for "all".
3. **String methods that differ from CPython (measured, low).** 957 cases from `count`, `find`, `replace`, `split`,
   `in`, `split()`, `strip()`, `upper`, `lower`, `len`, slicing.
   - `s.replace("", x)` inserts between UTF-16 units: an emoji becomes two lone surrogates. Files and packets write
     them as `?` (`String.getBytes(UTF_8)`, Netty), so nothing crashes.
   - `split()` and `strip()` use Java's whitespace (`\s+`, `String.strip`). U+00A0, U+3000 (the full-width space common
     in Chinese text), U+001C-001F and U+0085 are not whitespace there; CPython treats them as whitespace.
   - `repr` leaves non-printable characters raw. CPython escapes them, e.g. `'\xa0'`.

## Checked and fine

- `Ops.Search` (substring search: Java's `indexOf` up to 16 characters, Knuth-Morris-Pratt above) matches CPython in
  every case tried:
  - overlapping needles (`"aaaa".count("aa")` = 2);
  - empty needles (`split("")` raises `ValueError`);
  - non-BMP haystacks (`find` counts code points);
  - needles of 15, 16, 17 and 18 characters.
- Work owed (`Runner.owed`) lives with the run, so it goes when the run goes. Paying it uses the run's grant, not the
  tick's `left`; the overspend was already charged in the tick it happened. `mc.wait` counts down while a run owes.
  `WorkShare` starts the next tick at the first installation it refused, so no installation starves.
- `ProgramRun.check` cache: it belongs to one `Prepared`, which is built from the pushed snapshot (the file and the
  files it calls). A push removes it. Library edits that are not pushed do not reach runs, by design.
- `ViewerUploadRule`: the singleplayer/LAN owner always may; LAN guests follow the switch. It is a static value set at
  server-config load and reset at unload.
- Dev's 32,767-character limit on LDLib2 String bindings (9268c1ea): the branch adds only `syncAsk`, the domain ID and
  the status tag (measured at 436 KB in the worst case at ATM10 scale, 4b967c95). Program data travels in its own
  payloads, whose answers are already cut to 32,000 characters.
- Federation P2P tunnels (dev): `TunnelCalls` lists and links them through AE2's `P2PService`, as a memory card does,
  and their refresh runs on the next tick. A GameTest covers a program that overloads a domain this way.
- Quantum Cube / other dimensions: tunnel, provider and interface records carry their dimension, and
  `ComputingDevices.domainOf` uses the entry's dimension. Dev's bounded observation snapshots (914f963f) do not reach
  automation, which reads the registry directly.
- Server-bound requests:
  - interface alias: reach 4, alias validated;
  - `Open`, `Fetch`, `Upload`, `Request`: domain gate from 497d5923, 4 uploads per player, dry runs every 10 ticks.
    `push` and `delete` are library-wide and unchecked, by design;
  - viewer `Hello`, `Prints`, `Records`: lists bounded at decode, answered only against leases;
  - ECO - N alias: `canPlayerInteract`, numbered once.

## Not covered

- The editor and graph client code, and the checker beyond its run limits (also left out on 2026-11-15).
- The ECO - N package beyond the 21.2.15 screen change (screen tests added in 4b967c95).
- Dev-only code: PatternProjection, Lanes, statistics. That belongs to a dev review.

## Fixes (owner, 2026-11-24: "1和3吧")

Finding 2 (rescan permission) was not chosen and stays open.

1. **Stopped domains pause their programs.**
   - The owner was offered "pause" or "calls fail"; they answered "1和3" without choosing, so the assistant's
     recommendation, pause, was applied.
   - `ProgramScheduler.tick` checks `stopReason(domain)`, once per domain per tick, before the device budget. While the
     domain is over capacity or unpowered, the device's installations neither tick nor start, and events are not read,
     since reading them reaches across the domain.
   - Each installation logs "paused: the domain stopped: a cable is over its capacity" (or "...: its Beam Converters
     cannot pay its use") once, then "resumed".
   - GameTest `aStoppedDomainPausesItsPrograms` (manifest `programs.domain-stopped`) fails before the fix.
   - Guide: `automation/compute.md`, section "When the domain stops" (EN and ZH).
   - UI scenario `ui.automation-domain-stopped`; before/after image
     `build/domain-stopped-check/domain-stopped-before-after.png`.
2. **CPython strings.** In `Ops`:
   - `isSpace` holds `str.isspace`'s exact set, measured on CPython 3.12.2. It is used by `split()`, `strip()`, `int()`
     and `float()`.
   - `replace("", x)` works by code points.
   - `stringRepr` escapes what `str.isprintable` rejects (`\xhh`, `\uhhhh`, `\Uhhhhhhhh`), using Java's
     `Character.getType` (Unicode 15 in both).
   - Seven new `EvaluatorTest.cpython` cases; six failed before the fix (`float` already passed, as Java's `strip`
     takes U+2003 and U+3000).
   - UI scenario `ui.automation-strings`; before/after image `build/strings-check/strings-before-after.png`.
   - The language still reads no `\x`/`\u` escapes in literals, so a repr with them cannot be pasted back. That was
     already true of control characters.
