# Test Commands

For a human-operated client session on a graphical Java 21 desktop, run `./gradlew :neoforge-1.21.1:runManualClient --dependency-verification=strict --no-configuration-cache` from the repository root, then use the [manual real-client walkthrough](manual-client.md). Its world persists in ignored `neoforge-1.21.1/run-manual-client/`; no automated cleanup or F3 approval is implied. See the [acceptance matrix](../acceptance-matrix.md) for registered cases versus missing Task 37/38 and final gates.

Run all commands from the repository root. The GameTest server loads the production mod and the dev-only
`ae2federation_test` mod, enables only the `ae2federation_test` GameTest namespace, and uses a disposable flat world
under `neoforge-1.21.1/run-gametest`.

## Unit and build checks

```bash
./gradlew --no-daemon --dependency-verification=strict :neoforge-1.21.1:test
./gradlew --no-daemon --dependency-verification=strict :neoforge-1.21.1:check :neoforge-1.21.1:build
```

Expected signal: JUnit passes, the strict build exits 0, and the normal JAR contains no
`space/controlnet/ae2federation/test/` or `data/ae2federation_test/` entries.

## Native GameTest surface

```bash
./gradlew --no-daemon --dependency-verification=strict :neoforge-1.21.1:runGameTestServer
```

Expected signals include `Enabled Gametest Namespaces: [ae2federation_test]`, native test ID
`harnessnativesmoke`, `AE2F_GT_RESULT ... assertions=6 operations=2`, and `All 1 required tests passed`.
The bounded fixture places an AE2 creative energy cell and ME chest, waits for the real Grid node to boot, then
modulates a real ME storage insert and extraction.

## Evidence verification

Task 40 documentation QA selects exactly four registered IDs. Run this from the repository root against the current
documentation, not a historical BLOCKED receipt:

```bash
./gradlew :neoforge-1.21.1:federationVerify -Pcases=docs.coverage,docs.commands,docs.reject-unsupported-claims,docs.reject-stale-evidence -PevidenceDir=.omo/evidence/task-40 --dependency-verification=strict --no-configuration-cache
```

Only after this actual attempt succeeds, consume its freshly written `result.json` using the real attempt directory
printed by the command (replace `<run-id>` with that attempt's run ID):

```bash
./gradlew :neoforge-1.21.1:federationVerifyEvidence -PresultFile=.omo/evidence/task-40/attempt-<run-id>/result.json -PevidenceDir=.omo/evidence/task-40 --dependency-verification=strict --no-configuration-cache
```

The earlier [Task 40 QA receipt](../../.omo/evidence/task-40-qa/verification.md) is historical and BLOCKED, not a
passing docs result or F1-F4 approval. F3's five `final.*` IDs have a BLOCKED backend; `federationUiTest` rejects them
before client launch. Registration supplies no final-client screenshots or benchmark spot-check. Task 37 still has
direct/subnet small 3/3 each and Federation 0/3, Task 38's soak is absent, and F1-F4 remain unapproved.

```bash
./gradlew --no-daemon --dependency-verification=strict :neoforge-1.21.1:federationVerify \
  -Pcases=harness.native-smoke,harness.reject-stale,harness.reject-empty,harness.detect-child-crash,harness.benchmark-reject-unknown,harness.benchmark-reject-empty \
  -PevidenceDir=.omo/evidence/task-02

./gradlew --no-daemon --dependency-verification=strict :neoforge-1.21.1:federationVerify \
  -Pcases=harness.gametest-registered,harness.gametest-required-failure,harness.gametest-timeout,harness.gametest-namespace-mismatch,harness.gametest-zero-selection \
  -PevidenceDir=.omo/evidence/task-02-gametest
```

Every invocation creates a fresh `attempt-<run-id>` directory. Native GameTests write a structured properties artifact
to a parent-selected path. The runner compares its test ID, structure, assertion count, operation count, and balanced
insert/extract accounting with the selected manifest entry; console markers are diagnostic only.

The schema-v3 `result.json` records relative artifact paths with SHA-256 hashes, child exits, source/worktree identity,
dependency metadata identity, product JAR identity, timestamps, and exact requested/executed/assertion accounting.
The producer validates the report before returning. A persisted report can be consumed again with:

```bash
./gradlew --no-daemon --dependency-verification=strict :neoforge-1.21.1:federationVerifyEvidence \
  -PresultFile=.omo/evidence/task-02/fail-closed-fix/attempt-<run-id>/result.json
```

Reports are machine-local execution evidence, not portable replay certificates. `evidenceRoot` and `attemptPath` record
the producer's canonical absolute paths, hashed together with `runId` in `pathIdentitySha256`. Relocated or renamed
attempts, symlink aliases, traversal paths, and edited path metadata are rejected. The hash is an integrity check,
not a digital signature against an attacker who rewrites the entire report. To explicitly require the original root,
also pass `-PevidenceDir=<producer-root>` when consuming evidence. Portable reproducibility requires a fresh run at its
new root; matching logical assertions and artifact identities do not make an old relocated attempt current.

The consumer rejects malformed or incomplete reports, stale source identity, zero assertions, nonzero/missing required
child exits, manifest/native mismatches, missing artifacts, external artifact paths, and artifact hash mismatches.

Task 5 uses the exact five-case native attachment set:

```bash
./gradlew --no-daemon --dependency-verification=strict :neoforge-1.21.1:federationVerify \
  -Pcases=ports.bridge-cable-device,ports.router-six-grids,ports.router-repeated-grid,ports.reject-floating-node,ports.reject-cross-grid-join \
  -PevidenceDir=.omo/evidence/task-05
```

The `ports.reject-floating-node` case includes a valid-then-replaced native neighbor and records
`replacedAccepted=false`. Task 5 persisted consumption parses each isolated `positive-<testId>.log` and correlates every
Grid identity in the native properties against deterministic `AE2F_PORT_TRACE` facts. Run the fully rebound semantic
self-test against the fresh result:

```bash
./gradlew --no-daemon --dependency-verification=strict \
  :neoforge-1.21.1:federationTaskFiveEvidenceSelfTest \
  -PresultFile=.omo/evidence/task-05/attempt-<run-id>/result.json
```

## Benchmark smoke

```bash
./gradlew --no-daemon --dependency-verification=strict :neoforge-1.21.1:federationBenchmark \
  -Pprofile=harness-native-smoke \
  -PevidenceDir=.omo/evidence/task-02-benchmark
```

Expected signal: the registered benchmark GameTest performs 1,000 modulated insert/extract cycles against live AE2
network storage, reconciles exactly 1,000 inserted and 1,000 extracted items, reports nonzero elapsed nanoseconds, and
writes current-run evidence. An unknown profile or the deterministic empty-work fault seam must fail.

## Cleanup

`runGameTestServer` is finalized by `federationCleanupGameTest`, including native failure and timeout exits. The finalizer
copies available logs/crash reports into `.omo/evidence/task-02/direct-cleanup/` before removing only `run-gametest`.
Wrappers select diagnostics directories within their immutable attempts and also run parent-side cleanup in `finally`.
Native properties and redirected child output already reside in the attempt, outside the runtime tree. An explicit
cleanup invocation is available for interrupted direct runs:

```bash
./gradlew --no-daemon :neoforge-1.21.1:federationCleanupGameTest
./gradlew --stop
```

Confirm no server process or listener remains:

```bash
pgrep -af 'net.neoforged.devlaunch.Main|GameTestServer'
ss -ltnp
```

The expected process search is empty; no task-owned listener is present. Do not delete user worlds or accept a EULA.

## Task 3 actual-client UI qualification

Run the exact LDLib2 scenario set under the task-owned Xvfb display:

```bash
./gradlew --no-daemon --dependency-verification=strict :neoforge-1.21.1:federationUiTest \
  -Pcases=ui-harness.shared-resource,ui-harness.server-ack,ui-harness.reject-stale \
  -PevidenceDir=.omo/evidence/task-03
```

Expected signal: both actual-client LDLib2 scenarios pass with synthetic input, the shared XML resource is reloaded and
renders changed text, the stable `#ack_control` click receives a matching server acknowledgment, two nonblank screenshots
are captured, and the wrapper's stale/missing/mismatched evidence probes are rejected. The schema-v3 result records the
resolved LDLib2 JAR hash, product JAR hash, source identity, GUI scale, window/framebuffer dimensions, language, and GL
renderer metadata.

Consume the latest immutable attempt independently:

```bash
./gradlew --no-daemon --dependency-verification=strict :neoforge-1.21.1:federationUiVerifyEvidence \
  -PresultFile=.omo/evidence/task-03/attempt-<run-id>/result.json
```

The consumer rejects stale source or dependency identity, relocated attempts, malformed accounting, missing artifacts,
and screenshot hash mismatches. Dynamic `.omo` orchestration/session state is excluded from source identity; product and
build inputs remain bound through Git status/file hashes, dependency metadata, and product/LDLib2 artifact hashes.

The UI wrapper always removes `neoforge-1.21.1/run-uitest` after preserving available logs. Confirm cleanup with:

```bash
test ! -e neoforge-1.21.1/run-uitest
pgrep -af 'runUiTestClient|run-uitest|ae2federation.ui.runId|Xvfb.*1280x720x24'
```

Expected result: the directory check exits 0 and the process search prints nothing.

## Task 33 scoped Federation Domain workspace

Run all six production LDLib2 scenarios in one actual client:

```bash
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict \
  -PevidenceDir=.omo/evidence/task-33 \
  --dependency-verification=strict --no-configuration-cache --warning-mode=fail
```

Expected signal: the graph pan/zoom and layer controls remain interactive; a real mapped Provider accepts slot 0 to Lane
0 with a server acknowledgment; a claimed production Endpoint reports Federation mode and Claim epoch; the real
multipart Bridge entrance exposes the same Federation Domain; Simplified Chinese renders at GUI scale 4 with the large quantity;
and a competing Claim is rejected as `OWNER_CONFLICT`. The persisted verifier also rejects rebound forged mapping and
Claim-success reports.

## Task 11 direct multipart Bridge

Run the exact five-case Bridge set serially through the schema-v3 evidence harness:

```bash
./gradlew --no-daemon --dependency-verification=strict federationVerify \
  -Pcases=bridge.valid,bridge.invalid,bridge.same-grid,bridge.reject-federation-cable,bridge.reload-replace \
  -PevidenceDir=.omo/evidence/task-11
```

Consume the resulting report and run the fully rebound semantic probes:

```bash
./gradlew --no-daemon --dependency-verification=strict \
  :neoforge-1.21.1:federationVerifyEvidence \
  :neoforge-1.21.1:federationTaskElevenEvidenceSelfTest \
  -PresultFile=.omo/evidence/task-11/attempt-<run-id>/result.json \
  -PevidenceDir=.omo/evidence/task-11
```

Expected signals: all five required GameTests pass with zero child exits; the valid case records two distinct native
Grids and no native cross-domain connection; invalid, same-Grid, Federation Cable, and replacement/removal cases fail
closed with their typed reasons; persisted consumption accepts only the current schema-v3 report; and the three Task 11
identity, rebound-case, and incomplete-trace probes are rejected.
