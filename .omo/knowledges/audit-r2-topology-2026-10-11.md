# Topology adversarial audit, 2026-10-11, second round

Baseline: `2fa70d74f271a69e80baff583940d4debd3a65ed`, clean isolated worktree
`audit/topology-round2-20261011`. AE2 version: 19.2.17. This record distinguishes
source analysis, explicit fault/configuration injection, and real native GameTests.

## Confirmed: coalesced callbacks must not link two P2P inputs

Native `P2PService.updateFreq` can retain two input objects with the same nonzero
frequency while its input map selects only one. Its output set never contains an
input. This can be represented with actual tunnel parts and native configuration
updates; the regression deliberately injects that frequency collision, rather
than claiming to reproduce every possible carrier merge in a client.

Federation's queued drain includes changed members as well as the selected input
and registered outputs. That is necessary to withdraw moved/disconnected members,
but the same member list was also passed as an input's candidate peers. Two
changed inputs at frequency 73 with no outputs therefore published reciprocal
links. The native output set was empty while the Federation domain joined them.
This could join otherwise separate Federation domains through input-only wiring.

`P2PCollisionGameTests.p2pCollidingInputsStaySeparate` failed before the fix with
`An input must not link directly to another input`; its earlier native-empty-output
assertion had passed. The fix computes one output-only view per group, while
still publishing all affected members. Outputs continue to use the native
selected input, and reciprocal link requirements prevent a nonselected input
from joining the selected input/output domain. The test also adds a real output
using the memory card, then changes which input the native registry selects.
No storage, identity, GUI, save format, timeout, or native P2P registry changes.

The added output filter is one linear pass per affected group. The existing
`members` diagnostic measures group size, not every Java iteration; it does not
count this filter or each peer predicate. No claim of a new performance speedup
is made for this correctness patch.

## Independent passes and counterevidence

- **Old frequency / unloaded input:** AE2 19.2.17 `removeNode` and `updateFreq`
  notify old outputs using the underlying output collection even when
  `getOutputs` subsequently returns empty because the input is absent. Those
  changed outputs remain eligible for the queued old-group publication. A moved
  tunnel is excluded from its previous group, and a removed tunnel has no
  `nodeId`/front to republish. Power loss still has a registered input and output
  collection. Do not confuse an empty post-removal enumeration with no callbacks.
- **Cross-dimension topology:** domain node IDs contain dimension, position, and
  part. P2P peer evidence uses the other tunnel's complete node ID. Server-level
  registries combine dimensions intentionally; level close removes only that
  dimension. Router face resolution checks `isLoaded` before native neighbor
  lookup. The patch adds no world lookup and no chunk loading.
- **Cache invalidation / identity:** claim add/remove/release advance
  `IdentityEpoch`, including conflicts introduced on another Grid. Cached
  settlements and `ObservedGrids.confirmed` therefore invalidate on cross-Grid
  copied-lineage and split changes, not only a local node revision. Empty Grid
  cleanup follows node-removal epoch changes. No TTL is involved.
- **Fresh vs persisted lineage:** only nodes minted without saved lineage get
  same-tick adoption permission. Copied NBT and persisted lineage do not acquire
  that permission from joining a live Grid or entering initialization scope.
  A fresh-only Grid defers settlement and requests binding refresh after the
  tick boundary. A fresh Grid can legally adopt durable A, lose A, and adopt B
  in the same tick; preserving that rule is necessary.
- **Same-Grid A-to-B return router:** that legal transition exposed a separate
  stale captured `NetworkId` in `syncRouters`, because IGrid object identity need
  not change. Construction using two fresh native managed nodes plus durable
  seed A/B was sent to the returns worker, who owns the reproduction and fix.
  This is not grounds to forbid same-tick adoption or transfer A's policy to B.
- **Switch/Router lifecycle:** a retained `RouterFacePort` has no blanket alive
  guard around all resolving methods. Destroying its boundary node alone while
  leaving the Switch block alive, then sending neighbor updates, invalidates its
  capability cache. That was an invalid partial-owner fixture in the previous
  audit, not evidence of a normal whole-owner production unload failure. Native
  block owners must be removed before manually destroying retained face nodes;
  native CPU blocks must execute cluster teardown. No real late callback after
  whole-owner destruction was established in this pass, so no speculative
  lifecycle patch is included.

## Verification receipts

Raw evidence is kept locally under `.omo/evidence/audit-r2-topology-2026-10-11/`.
The regression uses actual AE2 parts, channel service, memory card configuration,
and Federation domain registry. It is a GameTest/testmod receipt, not a production
client screenshot or a save/restart proof.

```sh
python3 tools/dev_gametests.py p2pcollidinginputsstayseparate --ci --no-isolate
./gradlew --dependency-verification=strict check
```

The first command failed before the patch as described above. Strict check passed
after the production patch, including the shared JAR content check.

The final native batch passed **15/15 in 29 seconds** in one server, with the
new collision test first and subsequent tests reusing the level:

```sh
python3 tools/dev_gametests.py p2pcollidinginputsstayseparate p2punpairedstayseparate p2pfrequencyandinputchanges p2prefreshindependentgroups p2prefreshscale31 p2plinkfollowspowerandreload p2pchannelshortage p2pacrossdimensionssharesnetworks p2pacrossdimensionschunkunload identitysametickfragments identityinitializationorder identitystablequerycost identitycopiednode identitylegacyprovisional identitysingleestablishedmerge --ci --no-isolate
```

Raw log: `topology-green.log`; red raw log: `p2p-collision-red.log`.
Diagnostic receipts from this run (not a controlled before/after benchmark):

| Change | Callbacks | Queued tasks | Tasks run | Groups | Group members | Publications | Links | Task time |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Two independent groups, stable republish | 6 | 1 | 1 | 2 | 6 | 6 | 8 | 359,474 ns |
| 31 outputs, pair | 1,117 | 1 | 1 | 2 | 32 | 32 | 62 | 1,667 us |
| 31 outputs, power off | 96 | 1 | 1 | 1 | 32 | 32 | 0 | 1,502 us |
| 31 outputs, power on | 96 | 2 | 2 | 2 | 64 | 64 | 62 | 1,643 us |

Pairing includes the old zero-frequency group, explaining two builds with only
32 total members. Power restoration triggers two native callback bursts; it is
not asserted to require a single task over all ticks. No Late/Ultra factory
performance conclusion follows from this 32-tunnel workload. No production
client test or real process restart was run by this worker.
