# Task 4 Network Identity

- Accepted evidence is `.omo/evidence/task-04/attempt-20260913T184118156Z/result.json`, schema version 3, status
  `complete`, with all four exact case IDs passed and both `restart-prepare` and `restart-verify` child exits equal to 0.
- Restart process one used Grid/service/node identities `f3bc091` / `6d842f1d` / `2c67e745`; process two restored native
  node metadata into distinct identities `5b80304c` / `56648935` / `392a1329`. This demonstrates reconstruction rather
  than reuse of live Java handles.
- Persist identity through native AE2 Grid service node data: schema, `NetworkId`, persistent node UUID, and revision.
  Persist only sparse settlement status in Minecraft `SavedData`; live claim maps are transient.
- `GridNode.callListener(IGridNodeListener::onSaveChanges)` is the pinned AE2 internal compatibility boundary needed to
  schedule host persistence when a service assigns fresh node metadata.
- A native copied-node collision does not notify the original Grid. Any Policy access decision must reconcile against
  current registry claims instead of trusting a settlement cached at the last local node event.
- Never put delayed assertions inside an already-successful `succeedWhen`. Keep the complete state machine inside the
  bounded assertion so missing evidence and late failures cannot be reported as success.
- Repaired canonical evidence is `.omo/evidence/task-04/attempt-20260913T194020162Z/result.json`. Persisted consumption
  must parse exact native case semantics and restart logs; fully rebound copied-node settlement forgery is rejected.
- Paired continuity values require parse-before-compare. Native object facts use canonical unsigned decimal syntax
  `0|[1-9][0-9]*`; missing and empty before/after pairs are rejected even after full evidence rebinding.
