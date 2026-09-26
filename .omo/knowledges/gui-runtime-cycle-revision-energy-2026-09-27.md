# Cycle/energy diagnostics and current revision display

Previous turn made verified progress: missing-provider to missing-CPU transition and remaining backend reason branches.
This iteration adds real-world cycle, policy invalidation and energy-source UI evidence and fixes a display defect
found by inspecting those screenshots.

## Real transitions

- Enable an actual reverse CRAFTING/REQUEST rule over the two fixture grids: the production cycle guard reports a
  dependency cycle. The client verifies that this replaces the previous missing-CPU explanation.
- Disable the reverse rule: cycle text disappears and the actual missing-CPU observation returns.
- Disable the selected forward rule: the real policy revision advances, its old diagnostic fails revision matching,
  the service no longer returns it, and the client shows configuration-disabled without leftover backend text.
- Remove only the fixture-owned creative energy cell at endpoint.west(), guarded by an exact block type check.
  Production energy observation over the actual native grids reports ENERGY_SOURCE_MISSING. Open a fresh Router
  context, use the rule browser to select the real ME power rule, and verify the exact localized reason.
  A fresh context is deliberate: previous external policy edits leave the old menu's authorization revision stale.

## Screenshot-found bug and fix

The first six-case run passed, but its disabled-rule screenshot showed updated disabled state alongside the old
revision 3. `ruleText()` used `expectedRevision`, which belongs to submission authority, while its rule state came
from the current configured record. It now uses the configured record's revision (or current policy revision for an
unconfigured/deleted key). It does not update `expectedRevision` while rendering.

New actual-client assertion compares the displayed revision with the server's real disabled record (revision 8 in the
final screenshot). A production server-dispatch check confirms currentRequest still carries the old expected revision,
returns STALE_REVISION, and does not toggle or revise the disabled rule. This is a direct dispatch test, not a new
network transport race test.

## Verification

Final evidence: `.omo/evidence/gui-runtime-revision-display/attempt-20260926T140005082Z`.
BUILD SUCCESSFUL; 6/6 scenarios, 1269 steps, 118/118 checks; 248 unit tests pass.
Visually inspected final `259_ui-policy-runtime-revision-invalidated.png` and the initial run's cycle/energy screenshots.
Initial evidence retained at `.omo/evidence/gui-runtime-cycle-energy/attempt-20260926T135607086Z`.

```sh
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict \
  -PevidenceDir=.omo/evidence/gui-runtime-revision-display \
  --dependency-verification=strict --no-configuration-cache --console=plain
```

Expected: BUILD SUCCESSFUL, six scenarios/checks pass; revision text matches server record and outdated dispatch is
rejected without mutation. Keep sources unchanged during the run.

## Useful correction to the remaining audit

Inspected final `ui.endpoint/145_ui-endpoint-direct.png`: the existing direct Endpoint fixture already displays
"Multiple domains match. Open the intended Router or Bridge." It retains real local identity/network/ownership
information and disables domain actions. Registry source confirms Direct Bridge domains are distinct from Router
physical components; no new fabricated domain fixture is necessary. This now provides visual evidence for ambiguous
presentation, though an explicit server candidate-count/assertion should still be added.

The local selector still says "No available selection" while the actual endpoint's details are present below. Improve
that presentation using the verified local device position and an explicit read-only scope, rather than manufacturing
an editable domain choice. Unconfirmed-identity integration coverage and the final full-scope audit remain outstanding.
