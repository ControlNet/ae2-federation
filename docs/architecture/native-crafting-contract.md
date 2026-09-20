# Native Crafting Binding Gate

## Status

Task 9 is complete on the locked Minecraft 1.21.1, NeoForge 21.1.250, AE2 19.2.17, and Java 21 runtime.
Five serialized GameTests prove terminal-equivalent submission, automated stocking, missing-material rejection,
no-CPU rejection, and requester disconnect/persist/reload/cancel behavior through AE2's native crafting service.

The accepted public lifecycle is:

- `ICraftingService.beginCraftingCalculation(...)`
- `ICraftingService.submitJob(...)`
- `ICraftingSubmitResult.link()` for requester-owned jobs
- `ICraftingLink.writeToNBT(...)`
- `StorageHelper.loadCraftingLink(...)`

No compatibility shim, replacement planner, replacement CPU, or alternate crafting ledger is used.

Task 26 binds that native lifecycle across an authorized Federation relationship. A directional
`PolicyKey(consumerNetworkId, providerNetworkId, CRAFTING)` selects one revision-bound capability regardless of how many
physical Bridge routes connect the networks. The capability exposes the provider Grid's exact native crafting service,
providers, provider nodes, and CPUs; Federation does not copy patterns or own planning, execution, task, or result state.

The binding fails closed when Policy is disabled or deleted, a held revision becomes stale, Fabric connectivity is lost,
the provider generation changes, source identity is no longer settled, or native provider/CPU readiness disappears.
Reconciliation withdraws the old binding before a subsequent capability access can use it.

## Pinned Sources

All source links refer to AE2 commit `79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a`.

- [Public service](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a/src/main/java/appeng/api/networking/crafting/ICraftingService.java)
- [Public link](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a/src/main/java/appeng/api/networking/crafting/ICraftingLink.java)
- [Public reload helper](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a/src/main/java/appeng/api/storage/StorageHelper.java)
- [Terminal confirmation](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a/src/main/java/appeng/menu/me/crafting/CraftConfirmMenu.java)
- [Automation tracker](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a/src/main/java/appeng/helpers/MultiCraftingTracker.java)
- [Native service](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a/src/main/java/appeng/me/service/CraftingService.java)
- [Native CPU execution](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a/src/main/java/appeng/crafting/execution/CraftingCpuLogic.java)

## Verified Binding

Terminal-equivalent requests use a mock-player `PlayerSource`, calculate with
`CalculationStrategy.REPORT_MISSING_ITEMS`, and submit with a null requester. The successful submission is standalone,
extracts two planks, dispatches one native pattern through the Pattern Provider and Molecular Assembler, and returns four
sticks to Grid storage.

Automated stocking uses AE2's `MultiCraftingTracker` with a managed node registered as `ICraftingRequester`. Its
calculation and submission produce a native requester link. Final output is accepted through
`ICraftingRequester.insertCraftedItems`, completion invokes `jobStateChange` once, and the tracker discards the dead link.
After the tracker installs that link, the GameTest invokes the same logical slot a second time. AE2 rejects the duplicate
invocation, the observed requester-link UUID set remains equal to the original link ID with cardinality one, only two
planks are extracted, and exactly four sticks are accepted and stored.
The requester writes into the physical ME chest inventory, not the complete Grid aggregate: the aggregate contains
AE2's highest-priority `CraftingServiceStorage` interception mount and is not a valid requester destination.

The simulation requester must return a native Grid node from `ICraftingSimulationRequester.getGridNode()`. AE2 skips
provider-pattern exploration when that node is absent. The node is captured on the server thread before calculation;
GameTest helper access from the calculator thread is invalid.

Requester persistence writes the native link to NBT and reconstructs it with `StorageHelper.loadCraftingLink`. The
restored link is exposed from `getRequestedJobs()` before the replacement requester joins the Grid, allowing
`CraftingService.addNode()` to rebuild the CPU/requester nexus. Before suspension, the cancellation proof observes Grid
material fall from 64 to zero and all 64 planks in `CraftingCpuLogic` inventory. It then suspends the submitted CPU before
disconnect so no pattern input is dispatched during reconstruction; native cancellation remains active, stops the CPU,
returns the observed CPU inventory to exactly 64 Grid planks, accepts no final output, and preserves the crafting UUID
through a second reload.

## Failure Cases

- Missing two planks produces a simulation plan and native `INCOMPLETE_PLAN`; no task is submitted.
- Available materials and pattern with no crafting CPU produce an executable plan and native `NO_CPU_FOUND`; materials
  remain untouched.
- Native completion is not treated as proof of delivery. Stocking separately asserts the amount accepted by the requester.
- An authorized source with patterns but no native CPU advertises no Federation Crafting capability and has no fallback.
- Replacing provider A with a separately constructed Pattern Provider B on the same settled source `NetworkId` advances the
  provider generation. B has a different registration node UUID/node object, provider object, pattern object, and binding;
  every accessor on retained binding A remains empty after B becomes current.
- Removing the final real Bridge Fabric from a live binding advances topology, increments the withdrawal counter, empties
  every retained accessor, and publishes no replacement capability. This is separate from the no-CPU rejection proof.
- Two physical Bridge routes for the same directional relationship retain one provider, pattern set, and CPU capacity.

## Evidence And Verification

The canonical schema-v3 result selects and executes exactly these five cases with assertion counts `10`, `12`, `7`, `7`,
and `14`:

```sh
./gradlew :neoforge-1.21.1:federationVerify -Pcases=craft-proof.native-terminal,craft-proof.native-stocking,craft-proof.missing-material,craft-proof.no-cpu,craft-proof.disconnect-cancel-restart -PevidenceDir=.omo/evidence/task-09 --no-configuration-cache
RESULT_FILE=$(ls -td .omo/evidence/task-09/attempt-*/result.json | sed -n '1p')
./gradlew :neoforge-1.21.1:federationVerifyEvidence -PresultFile="$RESULT_FILE" -PevidenceDir=.omo/evidence/task-09 --no-configuration-cache
./gradlew :neoforge-1.21.1:federationTaskNineEvidenceSelfTest -PresultFile="$RESULT_FILE" --no-configuration-cache
./gradlew :neoforge-1.21.1:test --tests '*NativeCraftingBindingContractTest' --no-configuration-cache
./gradlew check build --no-configuration-cache
```

Persisted consumption recomputes native job cardinality from observed UUID sets, correlates tracker and restart UUIDs with
their link IDs, validates the exact native artifact set and runtime facts, and binds source, dependency, and product
identity. The adversarial self-test fully rebinds copied evidence and rejects forged duplicate counts/UUIDs, a submitted
duplicate invocation, missing provider dispatch, unaccepted stocking output, invalid missing-material/no-CPU submissions,
forged pre-cancel Grid/CPU extraction, altered return facts, and missing artifacts.

Task 26 adds the exact four-case binding set with assertion counts `18`, `8`, `18`, and `5`:

```sh
./gradlew :neoforge-1.21.1:federationVerify -Pcases=crafting.native-binding,crafting.deduplicate-capability,crafting.native-state-owner,crafting.reject-unavailable -PevidenceDir=.omo/evidence/task-26 --no-configuration-cache
RESULT_FILE=$(ls -td .omo/evidence/task-26/attempt-*/result.json | sed -n '1p')
./gradlew :neoforge-1.21.1:federationVerifyEvidence -PresultFile="$RESULT_FILE" -PevidenceDir=.omo/evidence/task-26 --no-configuration-cache
./gradlew :neoforge-1.21.1:federationTaskTwentySixEvidenceConsumer -PresultFile="$RESULT_FILE" --no-configuration-cache
./gradlew :neoforge-1.21.1:federationTaskTwentySixEvidenceSelfTest -PresultFile="$RESULT_FILE" --no-configuration-cache
```

`AE2F_CRAFT_NATIVE_ENTRY` remains the semantic evidence channel. A separate `AE2F_CRAFT_AUTHORITY` observer scans the live
AE2 Grid and node services directly, without reading that semantic Map, and receipts the settled NetworkId, Grid/service,
registration node UUID/node object, provider object, CPU set, exact pattern object, binding/generation, Fabric references,
topology revision, common-Fabric count, withdrawal count, and access state. The consumer requires the exact phase set for
each child and cross-correlates both channels. Missing, duplicate, substituted, conflicting, or fully rebound fabricated
authority is rejected for Task 26-specific semantic reasons.

Task 27 adds the exact five-case native terminal set with assertion counts `21`, `15`, `10`, `10`, and `10`:

```sh
./gradlew :neoforge-1.21.1:federationVerify -Pcases=terminal.native-crafting,terminal.native-result,terminal.missing-material,terminal.no-cpu,terminal.reject-async-world-access -PevidenceDir=.omo/evidence/task-27 --dependency-verification=strict --warning-mode=fail --no-configuration-cache
RESULT_FILE=$(ls -td .omo/evidence/task-27/attempt-*/result.json | sed -n '1p')
./gradlew :neoforge-1.21.1:federationTaskTwentySevenEvidenceConsumer -PresultFile="$RESULT_FILE" --dependency-verification=strict --warning-mode=fail --no-configuration-cache
./gradlew :neoforge-1.21.1:federationTaskTwentySevenEvidenceSelfTest -PresultFile="$RESULT_FILE" --dependency-verification=strict --warning-mode=fail --no-configuration-cache
```

Terminal discovery captures the authorized provider service, native provider node, CPU set, action source, and filtered
craftables on the server thread. AE2's calculator receives only the immutable captured requester. Submission rechecks the
Policy/Fabric/provider authority snapshot before calling native `submitJob`; native AE2 remains responsible for executable,
missing-material, and no-CPU outcomes. Result evidence correlates the native job, CPU logic callback, provider push, and
physical cell insertion rather than accepting a public aggregate quantity increase.

The discovery fixture publishes two genuine native patterns from the same bound provider: sticks are explicitly allow-listed
and a crafting table is forbidden. An independent discovery receipt scans the source and consumer crafting services, exact
provider pattern objects, discovered key set, and configured filter. It proves that only the original allowed pattern is
visible, with no copied pattern, alternate provider, or consumer-side projection recursion.

Before submission, result authority captures the exact physical cell delegate selected as the destination. Independent
submission, callback, and insertion receipts then correlate that object with the native link UUID, selected CPU,
`CraftingCpuLogic`, callback owner, output key, requested amount, and inserted amount. Fully rebound substitutions of the
job, logic/callback, or destination across semantic/native/runtime channels are rejected because they cannot change the
pre-established authority receipts.
