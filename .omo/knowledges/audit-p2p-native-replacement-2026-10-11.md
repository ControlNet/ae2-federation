# Native P2P replacement callback invalidation

Final read-only review found that AE2 19.2.17 `P2PService.updateFreq` replaces the canonical input map entry and
notifies the new input and outputs, but not the displaced input. The previous regression manually notified every
part after replacement, masking residual one-way evidence. Removing that synthetic callback and checking published
links reproduced expected two, actual three. Reciprocal domain checks still passed: this was stale evidence and
accumulating publication cost, not evidence of unauthorized network connectivity.

Each output now retains its last published input only as an invalidation hint. `scheduleRefresh` snapshots that input
into the deduplicated pending group before removal or another group publication can clear/change the hint. The drain
still checks current Grid/frequency and native `getInput/getOutputs`; there is no second authoritative topology,
full-Grid scan, TTL or chunk loading. Removal clears the retained reference. Capturing only during drain would miss
same-burst output removal and cross-group publication ordering; that rejected draft was never delivered.

The strengthened required regression checks native-only replacement and replacement followed immediately by actual
output removal in the same burst. No assertion was weakened and no timeout changed. Fixed-source four-case batch
passed 4/4 in 27 seconds (collision, 32-node publication, scale31, independent groups). Final complete required batch
passed 224/224 in 53 seconds; strict check/build/archive passed, including 547 JUnit tests with no failures/errors/skips.
Independent reviewer accepted the queue-time snapshot's removal, frequency/Grid movement, power and ordering guards.

```sh
python3 tools/dev_gametests.py p2pcollidinginputsstayseparate p2pcollidinginputslinearpublication p2prefreshscale31 p2prefreshindependentgroups --ci --no-isolate
./gradlew --dependency-verification=strict check build
python3 tools/dev_gametests.py --manifest --ci --no-isolate
```

Final production JAR SHA-256: `215f627a98adc99bba978600062920c7c4eb4cb7ce9b78e71c7ae70f7f4c2d6e`.
No save-format changes. Raw red/green/full receipts are preserved in the main workspace's ignored round-three evidence.
The user requested stopping after this batch's final verification and delivery; no further audit round is planned.
