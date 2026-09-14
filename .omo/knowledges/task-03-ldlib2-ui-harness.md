# Task 3 LDLib2 actual-client UI harness

- LDLib2 `2.2.34` scenarios run successfully through ModDevGradle's client run under `xvfb-run` with synthetic input.
- A fresh game directory needs `options.txt` containing `onboardAccessibility:false`; otherwise Minecraft opens the
  accessibility onboarding screen while LDLib2 waits for `TitleScreen`.
- Scenario `guiScale(3)` is applied after LDLib2 captures its top-level environment block. Attach GUI scale and
  window/framebuffer dimensions inside a rendered scenario step when the wrapper must report the tested state.
- The shared-resource case writes a task-owned XML override under `run-uitest/ldlib2`, calls
  `Minecraft.reloadResourcePacks()`, reopens the real LDLib2 UI, and checks the changed rendered label.
- The server-ack case uses stable selector `#ack_control`, LDLib2 synthetic hover/press/release, server-side fixture state,
  and the parent-selected run ID as the acknowledgment correlation ID.
- Worktree evidence identity must exclude dynamic `.omo` orchestration/session files. It still binds tracked/untracked
  product inputs by status and per-file SHA-256, plus verification metadata, product JAR, and LDLib2 JAR hashes.
- Task-owned `run-uitest` state is copied for diagnostics where relevant and removed in `finally`; Xvfb uses
  `-nolisten tcp` and exits with the child client.

Fresh accepted evidence: `.omo/evidence/task-03/attempt-20260913T151420452Z/result.json`.

## Persisted semantic validation repair

- The persisted consumer must resolve exactly one canonical `ldlib2/report.json` from the attempt's hash-bound artifact
  inventory and run the same `verifyLdlibReport` semantic verifier used by the producer.
- The outer result and upstream report are separate trust surfaces. The consumer cross-checks scenario IDs, nonzero
  checks, controls, attachments, screenshots, rendering metadata, and the parent acknowledgment correlation after
  verifying the upstream report itself.
- The required manifest cases are `ui-harness.shared-resource` and `ui-harness.server-ack` with backend `ldlib2`, plus
  wrapper self-test `ui-harness.reject-stale` with backend `self-test`.
- Rebinding all affected paths, identities, and hashes is insufficient to bypass semantic verification. Final selected
  probes reject zero upstream checks, malformed upstream JSON, and a locally forged acknowledgment with exit code 1.
- The final timing-aware visual review confirmed that LDLib2's report-level scale-2 environment is the pre-scenario
  snapshot; the rendered-step attachment is the authoritative scale-3, 1280x720 tested state.

Definitive repaired evidence: `.omo/evidence/task-03/attempt-20260913T171948925Z/result.json`.
