# Historical crafting/energy backend reasons

Previous goal turn was verified progress (localized graph observations and coherent control chrome).
This iteration adds specific historical backend reasons to the policy editor without invoking capability resolution
from the UI.

## Implementation

`BindingDiagnostic` records a typed reason plus policy and global topology revisions. Existing crafting/energy
reconciliation records real discovery failures. Crafting discovery distinguishes unconfirmed identity, missing active
native provider, and missing CPU; the existing dependency cycle guard also records a reason. Energy discovery has a
typed IllegalStateException subclass distinguishing unconfirmed identity and absence of a local public extractable
source. The prior catch for other IllegalStateExceptions remains in place, preserving failure behavior.

Each service replaces diagnostics on reconciliation and clears them on close. Static read-only accessors look up
existing services and return a record only when policy/topology revisions still match. They do not discover backends,
reconcile, call capability() or binding.isCurrent(), create bindings, or authorize work. Network changes with the same
revision can still make a historical backend observation old: the UI explicitly labels it "Last backend check" and
never promotes it to live readiness. No diagnostic is fabricated for an unobserved service.

The selected policy detail appends the localized reason when no published binding exists. Configured disabled and
missing required operation messages retain precedence. Both English and Chinese strings were added.

## Evidence

- `.omo/evidence/gui-backend-reasons/attempt-20260926T134245772Z`: BUILD SUCCESSFUL; 6/6 actual-client scenarios,
  1219 steps, 112 checks, zero failed checks. 248 unit tests pass, zero failures/errors/skips.
- New unit tests reject observations after policy revision changes or topology changes.
- `ui.graph-controls` enables the real crafting rule and calls production observation over the fixture's real grids.
  Its provider-side network contains no active native crafting provider. The test confirms that exact recorded reason,
  performs 100 read-only diagnostic queries retaining the same observation, and waits for the displayed explanation.
- Visually inspected `ldlib2/screenshots/ui.graph-controls/242_ui-policy-runtime-backend-missing.png`.
- Targeted existing `energydisconnectnosource` GameTest passes all 1 required tests (9 assertions). Initial extraction
  200, stale binding extraction 0 after source removal, replacement extraction 100; old binding is no longer current,
  replacement provider generation advances 1 to 6. Its log and properties are archived alongside the UI evidence.
  This is regression evidence for backend behavior, not a dedicated assertion of the energy diagnostic's UI wording.

```sh
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict \
  -PevidenceDir=.omo/evidence/gui-backend-reasons \
  --dependency-verification=strict --no-configuration-cache --console=plain

./gradlew :neoforge-1.21.1:runGameTestServer \
  -PfederationGameTestSelection=positive \
  -PfederationGameTestId=energydisconnectnosource \
  -PfederationNativeEvidenceFile=/tmp/gui-energy-backend-regression.properties \
  --dependency-verification=strict --no-configuration-cache --console=plain
```

Expected: BUILD SUCCESSFUL in both; six UI scenarios pass and GameTest logs "All 1 required tests passed".
Keep sources unchanged while the UI wrapper runs.

## Remaining audit gaps

Dedicated client visual coverage for missing CPU/energy source/cycle and actual revision invalidation is still absent.
Runtime endpoint readiness, absent domain references and absent consumer energy-source branches still fall back to
unobserved publication rather than a specific reason. Broader no-domain/ambiguous states and final visual audit remain
open as listed in the completion audit; this iteration does not claim the whole goal complete.
