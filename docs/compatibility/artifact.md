# Production artifact boundary (Task 39)

The only declared target is Minecraft 1.21.1 / NeoForge 21.1.250 with Java 21, Gradle 9.2.1, ModDevGradle 2.0.146, AE2 19.2.17 and LDLib2 2.2.34. The machine-readable `artifact.json` and the archive's exact singleton NeoForge metadata describe this one combination, not an open `1.21.x` or loader range. `gradle/verification-metadata.xml` pins resolved dependency checksums. Optional Task 36 addons are test-only configurations and are not included in this artifact. GTCEu remains startup-blocked on the selected dedicated-server tuple; the license-blocked Provider extension was not used.

## Hooks and licensing

| Boundary | Pinned owner | Review surface |
|---|---|---|
| Grid identity and native node data | AE2 19.2.17, source commit `79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a` | `GridServices`, `Grid.saveNodeData`, `GridNode` and `IGridNodeListener`; `docs/architecture/native-integration-gates.md` |
| Processing Lane and target | AE2 19.2.17 at the same commit | `PatternProviderLogic.patterns`, `patternInputs`, native ticker/provider services, `PatternProviderTargetCache.find`; production `ae2federation.mixins.json` |
| Native Storage and Crafting | AE2 19.2.17 at the same commit | `IStorageProvider.mountInventories`, `StorageService`, `ICraftingService`, `MultiCraftingTracker`; qualified gates and `docs/architecture/native-crafting-contract.md` |
| GUI integration | LDLib2 2.2.34 binary | Pinned runtime coordinate and checksum; no matching public 2.2.34 source tag identified in `dependencies.md` |

The mod's `LICENSE` is AGPL-3.0-only and is included byte-for-byte as `META-INF/LICENSE` in both binary and sources archives. AE2 and LDLib2 are linked dependencies, not vendored source. AE2's POM lists LGPLv3, MIT and CC BY-NC-SA 3.0 components; LDLib2 upstream lists LGPL-3.0. The binary/source archives must exclude the dev testmod, scenarios, debug/instrumentation controls and optional addon classes. `verifySharedJarContent` remains the existing semantic archive gate; `tools/artifact_verify.py` separately checks pinned metadata, license hash, required resources, contamination and actual ZIP integrity. The test suite creates mutated copies of the **real built archive** solely as negative test inputs, not distributable fixtures.

## Reproduction and limits

```bash
./gradlew :neoforge-1.21.1:check :neoforge-1.21.1:build --dependency-verification=strict --no-configuration-cache
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tests -p test_artifact_verify.py -v
PYTHONDONTWRITEBYTECODE=1 python3 tools/artifact_verify.py inspect
PYTHONDONTWRITEBYTECODE=1 python3 tools/artifact_verify.py clean-build --report .omo/evidence/task-39-independent/clean-build.json
# Only after the user has reviewed/accepted Minecraft's EULA and set APPROVED_EULA_FILE to their own eula.txt:
./gradlew :neoforge-1.21.1:federationVerify -Pcases=artifact.clean-build,artifact.same-jar-client-server,artifact.license-manifest,artifact.reject-wrong-tuple,artifact.no-extra-target -PfederationApprovedEulaFile="$APPROVED_EULA_FILE" -PevidenceDir=.omo/evidence/task-39 --dependency-verification=strict --no-configuration-cache
```

The clean-build command snapshots current tracked and untracked regular source outside `.omo`, rejects a changing source during its run, overlays those exact bytes onto a detached task-owned Git worktree, uses a fresh isolated Gradle home, and compares both resulting JAR SHA-256 values with the just-built local reference. It reports source revision/digest, dependency-verification hash, complete archive entry lists and artifact hashes; the disposable checkout/cache are removed after success or failure. Run it only when concurrent source writes have settled and keep its report/log for review. An isolated cache may need network access to download all locked dependencies.

The artifact run creates isolated server and client installations with unchanged copies of the **same** product JAR in each `mods` directory. Both ModDevGradle launches have `loadedMods=[]` so the local source-set mod is not registered. At mod construction, NeoForge's `ModList` reports the loaded mod file; the process computes its SHA-256, records its canonical path, and the harness compares both observations with the built archive. The client uses Minecraft Quick Play to join a local offline test server; both sides must log the named player's join. The production proof listener is opt-in via `ae2federation.artifactProof` on these isolated runs. Runtime worlds are task-owned, deleted after capture, and the two installed JAR copies are retained with the attempt alongside logs. The schema-v3 persisted consumer rechecks the archive hashes, origin/path evidence, successful joins, complete negative tests and current source/dependency identity. A server readiness banner or Gradle exit alone is never acceptance.

The earlier `.omo/evidence/task-39/attempt-20260923T095329159Z/result.json` used an automatic `eula=true` write in `tools/artifact_verify.py`; it is historical forensic evidence, not accepted qualification. A later authorized five-case run at `.omo/evidence/task-39/attempt-20260923T132715867Z/result.json` recorded both nested wrapper exits as zero and passed same-attempt persisted consumption, as described in the [Task 39 notes](../../.omo/knowledges/task-39-artifact.md). This is source-bound evidence, not blanket approval for a subsequently changed JAR or rendered-art quality. The runner refuses to bind a port, create server/client runtime directories, or launch without `--approved-eula-file` naming an existing regular user-provided file containing exactly `eula=true` followed by a newline. The Gradle five-case entrypoint and direct artifact tasks require `-PfederationApprovedEulaFile`; neither CI workflow supplies one. An unrelated runtime EULA file never grants consent. Review the applicable EULA before supplying a file for any new run.

Quick CI selects each distinct `backend=gametest` ID from `tests/scenarios/manifest.json` explicitly, executes it in isolation, and requires exactly one native required-test success per selected ID. This is more expensive than the former one-case smoke, but a single default test can no longer masquerade as the required manifest suite. Restart, optional-addon, GUI, and dedicated-server scenarios are separate backends and are not claimed by that suite.
