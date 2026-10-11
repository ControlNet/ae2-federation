# Canonical P2P input publication audit

Baseline production source: `3968e286`. The previous collision fix prevented input-to-input domain links, but each
changed input still scanned every output and published unilateral candidate links. With N colliding inputs and M
outputs this produced N*M input-side entries, although native AE2 selects exactly one input for the frequency.
Reciprocal domain validation rejected the extra links only after their construction and publication.

A new required GameTest uses 32 real active native tunnels, sixteen inputs and sixteen outputs. Frequency collisions
are explicitly injected with the native P2P API. Before repair it publishes 272 directed entries instead of 32:
16*16 input-side candidates plus sixteen output-side links. The test fails on this exact quantity.

The publisher now reads AE2's canonical `getInput()` for every tunnel. A noncanonical input publishes its ordinary
node evidence without scanning outputs or publishing tunnel links. Selected input and outputs preserve their full
reciprocal relations. Native `getInput()` returns null for frequency zero or a missing compatible input; existing
active checks still suppress links when power or channels are lost. No GUI information for a real connection is
removed, and no save format changes.

Independent review verified native AE2 19.2.17's method, old-input invalidation and the ordering after front
revalidation. After repair, the new quantity test, selected-input replacement test, 31-output scale test and
independent-frequency test pass together: 4/4 in 25 seconds. The new test retains all 32 node publications, builds one
32-member group with one queued task, checks every unselected input stays isolated and all sixteen outputs remain
in the selected input's domain. This is a counted pathological collision case, not a Late/Ultra factory benchmark.

```sh
python3 tools/dev_gametests.py p2pcollidinginputslinearpublication --ci --no-isolate
python3 tools/dev_gametests.py p2pcollidinginputslinearpublication p2pcollidinginputsstayseparate p2prefreshscale31 p2prefreshindependentgroups --ci --no-isolate
```

Red and green logs are retained in `.omo/evidence/audit-round3-2026-10-11/` in the main workspace.
