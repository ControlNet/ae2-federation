# Task 19 Independent Adversarial Verification

- Baseline revision: `a895c65f134e83a02b14ab3f3da278f139169ba8`; pre-verdict dirty identity: `cd9909fb44134f98c578071b259c85103adab3b4726880fa08785e3b9c11ba86`.
- Fresh Task 19 evidence: `.omo/evidence/task-19-independent-adversarial-20260916/attempt-20260915T152245134Z/result.json`.
- A 34-case reviewer matrix copied the attempt and fully rebound artifact hashes, path/run identity, and timestamps. `federationVerifyEvidence` accepted 30 semantic forgeries and rejected only generation, disposed inversion, a fourth NBT-read receipt, and the pinned pending drop.
- Accepted categories include arbitrary or malformed ownership identities, altered NBT snapshots/lock/unlock/drain quantities, an explicit forbidden serialized Grid-reference claim, altered dismantle snapshots/targets, endpoint inversion/omission, duplicate receipts, owner swaps, and cleanup replacement/omission.
- `ProcessingOwnershipGameTests.processingDismantle` calls `addDrops` only once. The post-call receipt still shows the native send list and return inventory populated, so repeated dismantle and emptied responsibility are not proved.
- Fresh Task 17 and Task 18 producers/consumers/self-tests, `ProcessingRegressionContractTest`, strict `check build`, Java diagnostics, archive isolation, and runtime cleanup all pass. These health gates do not close the evidence-integrity blockers.
- Verdict: Task 19 remains rejected and unchecked until every acceptance-critical field is derived from uniquely cardinality-checked receipts and repeated dismantle proves no second drop or mutation.
