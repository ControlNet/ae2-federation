# Task 42 review verification

## Scope and source identity

The patch is based on `2a3f40db8cdf910568cc4ae658dac8554be1b755`. At verification time, local `dev` and fetched `origin/dev` were at that
commit and the patch was uncommitted. The user subsequently authorized committing and pushing this repair patch;
concurrent visual work is excluded. No Issue or PR was created.
Production behavior is described in [identity lifecycle](../architecture/network-identity.md),
[retained Endpoint cleanup](../architecture/retained-endpoint-cleanup.md), and the
[actual production client run](production-jar.md#task-42-terminal-request-real-furnace-unload-and-restart-2026-09-26).

All validation worktrees use the same production source/JAR, SHA-256
`f4b5b7d88faf96b7bf99f2918f039880b60cfb482ba6440c14e397e533d2b562`.
Additional tests were added while the full suite ran. The latest versions of the changed/new tests were rerun separately
in fixed worktrees; receipts identify each exact source diff. This is not a claim that every invocation used an identical
test source snapshot. Runtime implementation and dependency versions are identical across the green runs.

## Results

| Verification | Result |
|---|---|
| Current manifest ordinary GameTest | 153 entries / 152 distinct testIds, all covered and passed |
| Restart backend | 2 entries / 2 distinct testIds, passed in actual separate JVMs |
| Applied Flux backend | 1 entry / 1 distinct testId, passed with pinned dependencies |
| Combined three GameTest backends | 156 entries / 155 distinct testIds |
| Strict `check` / `build`, warnings fatal | Passed; 236 JUnit tests, 0 failures/errors/skips |
| Python archive evidence tests | 15 passed |
| Production archive | 784 entries; production initialization mixin present; no testmod or CAS fault probe/mixin |
| Identity stable reads | 2,000 cached reads; 0 added settlement computations; 0 added lineage lookups |
| Lane maintenance | Existing own-Lane loop / 20 ticks unchanged; native ticker-count test passed (1 physical ticker, 3 delegates); no new global scan |
| Real production client / furnace | Passed terminal order, in-flight unmap, independent remote chunk unload/reload, native CPU completion, exact count, release and restart |

The ordinary collection is 148 distinct tests from the full sweep plus the final CAS, buffer, initialization-order and
active-Policy additions; overlapping revised tests were also rerun. The harness aliases `harness.native-smoke` and
`harness.gametest-registered` intentionally share one testId. Machine-readable per-entry coverage is
`.omo/evidence/task-42-verification/coverage.json`. Full-sweep logs are `build/ae2f-work/logs/task42-full-*.log`.
Verified receipts are in `.omo/evidence/task-42-final-targeted/attempt-20260926T070012212Z`,
`task-42-final-identity/attempt-20260926T070457110Z`, `task-42-order/attempt-20260926T070737541Z`,
`task-42-policy/attempt-20260926T065149377Z` and `task-42-resources/attempt-20260926T065650482Z` under `.omo/evidence/`.
The logs and receipts are ignored artifacts retained in this workspace.

## Red/green and test interpretation

- Baseline `identitysingleestablishedmerge`: expected AMBIGUOUS_MERGE, actual SETTLED. Fixed test also verifies saved
  lineage and configured Policy survive reload; separate canonical `identity.restart` now uses a truly standalone node.
- Baseline `identitylegacyprovisional`: joining a larger established Grid regenerated both copied NetworkId and nodeId.
  The strengthened fixture forces that native transfer direction. An equal-size fixture could move the other Grid and
  incorrectly miss this defect. Both copied-node rejection and old-lineage preservation assertions remain.
- Baseline loaded-absent Endpoint: explicit release returned `rejected-endpoint-unavailable`. The precise red run throws
  immediately with that result; the earlier retrying GUI fixture obscured the first failure behind a missing-BE timeout.
  Green tests exercise a fresh management session, select the missing retained identity and perform two-step cleanup.
- Green replacement/changed-Claim tests leave the replacement/new owner's Claim unchanged. A changed remote state
  invalidates the old confirmation. A test-only one-shot false CAS preserves Claim and local binding; real CAS then
  succeeds; Lane reuse rejects the old authorization/return association.
- Buffer tests deliberately seed native send/return buffers and verify refusal plus native dismantling drops. This is
  synthetic test data, not a claimed real partial furnace insertion. The production furnace sequence is separate.
- Retention tests use the existing test-only machine. Their management tests run the server-side menu session with a
  mock player; actual client UI/network evidence comes only from the production run.

No existing critical assertion was removed and no timeout was increased. The standalone restart fixture removes a
creative energy cell to satisfy its one-node premise. New cases were registered in the evidence switch; identity-only
initialization evidence correctly records no resource movement. Original canonical task groups remain exact.
Intermediate runs rejected stale source identity when tests changed during execution; these failures were retained and
rerun from fixed worktrees. An initial new-case accounting registration error was corrected without relaxing validators.

## Reproduction commands

Run from the repository root. Expect BUILD SUCCESSFUL, no failed required tests and complete evidence receipts.
Canonical identity and Policy groups must be run separately because their validators require exact task case sets.

```bash
./gradlew --no-daemon --dependency-verification=strict :neoforge-1.21.1:check :neoforge-1.21.1:build --warning-mode=fail --no-configuration-cache
python3 -m unittest discover -s tests -p test_artifact_verify.py -v
./gradlew --no-daemon --dependency-verification=strict federationVerify -Pcases=provider.production-release-cas-failure,provider.production-cleanup-buffers,provider.production-stale-cleanup,provider.production-replaced-cleanup,provider.production-claim-changed-cleanup,identity.single-established-merge,identity.legacy-provisional -PevidenceDir=.omo/evidence/task-42-recheck-targeted --no-configuration-cache
./gradlew --no-daemon --dependency-verification=strict federationVerify -Pcases=identity.initialization-order,identity.part-on-settled-cable,identity.part-policy-activation -PevidenceDir=.omo/evidence/task-42-recheck-order --no-configuration-cache
./gradlew --no-daemon --dependency-verification=strict federationVerify -Pcases=identity.replace-all-access,identity.restart,identity.ambiguous-split,identity.copied-node -PevidenceDir=.omo/evidence/task-42-recheck-identity --no-configuration-cache
./gradlew --no-daemon --dependency-verification=strict federationVerify -Pcases=policy.lifecycle-matrix,policy.new-bridge-restore,policy.reject-stale-edit,policy.delete-reconnect,policy.sparse-scale -PevidenceDir=.omo/evidence/task-42-recheck-policy --no-configuration-cache
./gradlew --no-daemon --dependency-verification=strict federationVerify -Pcases=resources.item-fluid-components,resources.stored-fe,resources.optional-absent,resources.reject-overflow,resources.reject-fe-power-coupling -PevidenceDir=.omo/evidence/task-42-recheck-resources --no-configuration-cache
```

Full ordinary manifest sweep, one native server per distinct testId:

```bash
python3 - <<'PY' > /tmp/ae2f-task42-testids.txt
import json
cases = json.load(open('tests/scenarios/manifest.json'))['cases']
print('\n'.join(dict.fromkeys(c['testId'] for c in cases if c['backend'] == 'gametest')))
PY
while IFS= read -r test_id; do
  ./gradlew --no-daemon --dependency-verification=strict :neoforge-1.21.1:runGameTestServer -PfederationGameTestSelection=positive -PfederationGameTestId="$test_id" --no-configuration-cache || break
done < /tmp/ae2f-task42-testids.txt
```

A passing native log contains `All 1 required tests passed :)`; check all 152 IDs, not only the final process exit.
The full run used independent fixed worktrees for three concurrent shell workers, without concurrent writes to a world.

## Limits retained

Late/Ultra, a two-hour soak and the full three-layout performance comparison were not rerun and are not marked passed.
This run establishes bounded maintenance structure and existing ticker semantics, not a new large-Lane latency benchmark.
Third-party compatibility is not requalified: the documented GTCEu dedicated-server client-class-loading block and
Recursive AE2 Pattern Provider license block remain. See `docs/compatibility/matrix.md` for the pinned historical matrix.
No crafting scheduler, authoritative inventory, processing queue, task ledger, or multiplayer authorization system was added.
