# Adding a GameTest case to the manifest (2026-10-02)

`dev_gametests.py --manifest --ci` (Quick CI) and `tools/required_gametests.py` (release gate) only need the GameTest
to pass. `federationVerify -Pcases=<id> -PevidenceDir=<dir>` is stricter, and a new row fails it unless all three
steps below are done:

1. **Row** in `tests/scenarios/manifest.json`: `id`, `testId` (the method name lowercased), `structure`, and
   `assertions`. Insert it as one text line; a JSON re-dump reformats the whole file.
2. **Evidence**: the test calls `PolicyEvidence.write(testId, assertions, facts)` with the same assertion count as
   the row. Without it, verify reports "Native evidence accounting mismatch".
3. **Accounting** in `gradle/federation-qa.gradle`:
   - A test that moves no items (policy edits, saved data) needs its id prefix in the two
     `identity.part-policy-activation ... migration.` lists near line 7315 (operations 1) and 7328 (accounting off).
     Otherwise verify expects `operations: 2` with real inserted or extracted work.
   - The id must also be a `case '<id>':` in the generic native-evidence switch near line 7585. Otherwise verify
     reports "Executed set mismatch ... executed=[]".

Avoid the `policy.` prefix for new cases: `PolicyContractTest` pins the exact `policy.*` set, and `policy.` cases get
the restart handling.

Example: `rules.crafting-needs-storage` (`RuleLinkGameTests.rulesCraftingNeedsStorage`).

Check:

```sh
./gradlew :neoforge-1.21.1:federationVerify -Pcases=rules.crafting-needs-storage -PevidenceDir=.omo/evidence/rules-verify \
  --dependency-verification=strict --no-configuration-cache --console=plain   # BUILD SUCCESSFUL
```
