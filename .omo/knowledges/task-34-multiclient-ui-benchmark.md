# Task 34 multi-client UI and benchmark

- The canonical UI run uses one real loopback dedicated server plus distinct ModDev runs for ClientA and ClientB. Sharing
  one client run name races ModDev-generated launch files.
- LDLib2 `Button.setOnServerClick` registers a server RPC for `mouseDown`. Deterministic test interaction should dispatch
  `UIEvents.MOUSE_DOWN` to the production element, not a synthetic `click` event.
- Multi-process UI phases must synchronize on authoritative receipts, not equal client tick counts. The final harness
  coordinates revision 1, stale-revision observation, Fabric invalidation, and refreshed-menu completion while all policy
  mutations still traverse the production menu RPC.
- Close the production menu before clearing the client screen. Dedicated-server subscription cleanup is observed only
  after the real container close path; direct screen replacement alone is insufficient.
- Reopened container IDs may be reused and screen objects may be replaced while one menu remains active. Phase ownership
  should follow explicit completion state rather than either client object identity.
- The `ui-small` benchmark is a deterministic 40-node, 24-edge, 64-pattern production snapshot/layout workload. It binds
  profile, budgets, dependency lock, versions, structural signatures, layout reuse/rebuild behavior, retained entries,
  payload size, timing, and closed-GUI cleanup into persisted evidence.
- Canonical evidence: `.omo/evidence/task-34/attempt-20260921T161813882Z/result.json` and
  `.omo/evidence/task-34-perf/attempt-20260921T162003938Z/result.json`.

## Atlas authority repair

- The LDLib2 contract follows the actual production test path: resolve `#policy_toggle`, create
  `UIEvents.MOUSE_DOWN`, and dispatch it through `UIEventDispatcher`.
- Closed-GUI progress is owned by AE2's real `IGridTickable` provider composition. Evidence captures every native delegate
  counter before both production menus close and after 20 closed-GUI ticks; the verifier recomputes the exact positive
  per-delegate delta instead of accepting a boolean or elapsed ticks.
- The bounded 40-node workload is derived from an exact production `FabricGraphProjection.snapshot` of an authentic
  two-member Fabric with a real Provider, Endpoint, physical edges, capability Claim edge, and native Pattern rows.
  Persisted evidence carries the encoded source projection; the verifier decodes its wire rows, recomputes its hash and
  exact kind/layer counts, and binds those facts to the baseline.
- A refreshed Hub editor must not accept the first non-original publication after a split. Wait for the old Hub's
  subscriptions to close, reconstruct the Hub through its normal block lifecycle, and require one current non-original
  registry reference whose membership set is exactly the two pre-split confirmed network identities.
- LDLib2 `TextElement.getText()` aggregates nested rows. The production `members_value` therefore contains the `2 members`
  summary plus two distinct member-ID lines; screenshot synchronization validates all three lines while persisted summary
  evidence records the first line.
- Final canonical evidence: `.omo/evidence/task-34/attempt-20260921T214717547Z/result.json` and
  `.omo/evidence/task-34-perf/attempt-20260921T214535537Z/result.json`.
- Screenshot capture clears transient client toasts, tutorial prompts, and chat HUD, then waits for three consecutive
  `ScreenEvent.Render.Post` frames where the synchronized stale class and localized bound label agree. Capturing after the
  actual render avoids stale framebuffer content without mutating or post-processing the production UI.
