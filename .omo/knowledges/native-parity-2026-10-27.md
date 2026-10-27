# Native parity: every crafting scene through Federation and on one joined Grid (2026-10-27)

Each `AddonCraftingScene` test runs twice: once through Federation, and once in a joined run where the networks are
joined into one AE2 Grid before the order
(`--jvm-property ae2federation.compat.joinBeforeOrder=true`). A joined test that fails, while its Federation run
passes, marks a native difference. A Federation test that fails, while its joined run passes, marks our bug.

## How to run

```sh
./gradlew --dependency-verification=strict :neoforge-1.21.1:compatTestJar
python3 tools/compat_run.py --accept-eula --no-build --group addons -j 8 -p 4 --run-suffix fed
python3 tools/compat_run.py --accept-eula --no-build --group addons -j 8 -p 4 --run-suffix joined \
    --jvm-property ae2federation.compat.joinBeforeOrder=true
python3 tools/compat_run.py parity fed joined > build/parity-table.md
```

- `jobTicks` in `compat-report.json` counts game ticks from submitting the first order until the consumer's CPU is
  idle. For scenes that order twice it covers the first order only.
- `@FederationOnly("why")` marks tests that check only Federation, such as the ledger after a cancel, a removed Bridge,
  a merge or a late byproduct, retired-CPU watches, and an accelerator moved to the provider network.
  - A joined run skips them and reports them as `skipped`. They are not failures.
  - A scene with a cancel, disconnect, merge or late-byproduct option but no annotation fails in a joined run with
    "mark the test @FederationOnly".
- Classes in the table:
  - Federation fails: our bug.
  - joined fails: a native difference, which needs a look.
  - both fail: an unsupported pairing.
  - slower: Federation takes more than the joined ticks plus max(10, half of them). This threshold is my choice and
    open to the owner.
  - parity.
  - Federation-only.
  - no control: tests without a crafting scene.

## Result (dev 0f6cff24 + @FederationOnly), group addons, 32 profiles

Federation: every profile passed. The joined run's counts:

| Federation fails | joined fails | both fail | slower | parity | Federation-only (skipped) | no control |
| --- | --- | --- | --- | --- | --- | --- |
| 0 | 1 | 0 | 3 | 258 | 179 | 676 |

- parity: 223 of 258 have identical ticks, and one Federation run is 1 tick faster.
  - Neo ECO CPU jobs are a constant 2-5 ticks slower through Federation. Examples: computation system ordering 17 vs
    12, crafting-system throughput 97 vs 92, and the Large Integrated Working Station 109 vs 107.
  - This is fixed latency, not per-craft cost.
- slower: `trinitycpusixteenremoteruns` in data-energistics, addons-all and useless-mod-addons takes 18-19 ticks vs
  4. The Trinity CPU sends a projection one craft per push, because Data Energistics batches only to providers it
  recognises as counted. This is the target of the DE counted-batching work.
- joined fails: `smartdoublingturnedonlater` in extendedae-plus-aeallpattern only.
  - On one Grid with AE All Pattern loaded, the consumer never sees the provider's re-decoded pattern objects after
    Smart Doubling is switched on. AE All Pattern replaces AE2's pattern refresh.
  - Federation's projection compares pattern objects by identity and re-reads them, so it passes.
  - The difference is native behaviour of AE All Pattern, not ours. The same test passes joined without AE All
    Pattern (extendedae-plus).

## Pitfalls found on the way

- After a join, the networks merge and Federation's network ids go away. `PolicyBridgeFixtures.mainNetwork()` then
  throws `No value present`, so ledger checks cannot run joined.
- In a joined run, `assertReturnsSettled` checks only that the consumer's CPUs are idle and empty.
- Checks that read the projection or the remote plan must read `scene.joined()`, not a method parameter. This applies
  to Neo ECO `batches`, `smartDoubling` and DE `trinityPlanning`/`trinityCpuSixteenRemoteRuns`.
