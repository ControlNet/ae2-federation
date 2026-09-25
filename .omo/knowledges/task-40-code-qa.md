# Task 40 code-side QA boundary

- `federationVerify` selects manifest cases by exact ID, runs the matching backend, and the canonical schema-v3 consumer requires `status=complete`, `parentExit=0`, matching source/dependency/JAR identity, and hash-bound artifacts. An ID in the manifest alone does not execute a validator.
- Documentation and final audits must not emit a passing report while Task 37/38 evidence and final client review are absent. The provisional acceptance matrix explicitly records failed Federation small warmup and absent soak; the suite result remains `BLOCKED`, including when its independent negative probes reject.
- F3 is a distinct client workflow, not an alias to Task 33's LDLib2 scenarios. The current `federationUiTest` only recognizes exact Task 3/15/33/34 sets. Register F3 only with a real end-to-end client scenario and current benchmark spot-check.
- The exact commands, exits and receipts for the interim QA dispatch are in `.omo/evidence/task-40-qa/verification.md`.

## Revised gate

- Docs qualification is independent of Tasks 37/38 being complete. Parse the expanded matrix's exact `T-*` ranges and manifest IDs; require references to exist and truthful 3/3 direct/subnet, 27/256 failed Federation warmup, 0/3 Federation windows, NON_COMPARABLE resources, absent soak, provisional art/compatibility, and unapproved final wave. Run the same validator on the bound copy in persisted consumption and compare it to current docs bytes. Full F1-F4 approvals remain separate and blocked.
- Exact F3 IDs are now registered with explicit blocked backend semantics. The real `federationUiTest` entrypoint rejects those IDs before client launch; registering them is not actual-client proof.
