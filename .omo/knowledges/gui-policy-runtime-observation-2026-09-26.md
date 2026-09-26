# Policy runtime observation in the workspace

## Implementation and semantic boundary

The selected rule retains its configured state and revision, followed by a separate runtime explanation.
Unconfigured and disabled rules are explicit. Crafting requires REQUEST, Processing requires EXECUTE and SUPPLY,
and ME power requires SUPPLY; a missing required operation is reported before displaying binding observations.
Processing is checked per dispatch, so its text directs users to target diagnostics instead of inventing a persistent
binding. Storage source provenance failures have localized English and Chinese explanations.

StorageMountService, CraftingBindingService and EnergyBindingService now expose hasPublishedBinding as a read-only
lookup of their existing service maps. Storage additionally exposes lastDiagnosticIfPresent. These methods do not
create a service, reconcile, invoke isCurrent, or authorize operations. Published presence is explicitly described as
last reconciliation state, with authority and availability checked again on use. Absence is described as no published
binding observed; it does not guess that the network is disconnected or that a backend is ready.

This is a partial implementation of runtime explanations, not full live activation diagnostics. In particular,
missing Crafting/Energy bindings still need authoritative, specific non-publication reasons. Storage transitive
relationships can have published effective bindings, so publication must never be relabeled as direct policy ACTIVE.
The existing activation evaluator is not called with invented BackendStatus.READY.

The detail sits in its own vertical scroller, retaining the action row at narrow sizes. LDLib2 Label does not size
its text automatically when height is auto; adaptive-height: true is necessary. The initial attempt omitted that
property, causing blank details and two failed narrow-text checks. The corrected attempt below supersedes it.

The design continues the shared component/state discipline identified in NeoECO and the compact task separation
identified in OmniSequence. KilaGraph/DataEnergistics remain the graph rendering/selection references; no new graph
editor dependency or fabricated live traffic is introduced by this change.

## Verification

Final evidence: `.omo/evidence/gui-policy-runtime-observation/attempt-20260926T121516846Z`.
BUILD SUCCESSFUL; 6/6 client scenarios, 905 steps, 76/76 checks. Unit suite: 244 tests, zero failures/errors/skips.
The actual-client fixture verifies that 100 selected-rule reads do not increase storage dependency refreshes or
source validation counters. A genuine disabled Crafting rule displays disabled runtime state; enabling it without
REQUEST updates the UI to the required-operation explanation. This test edits real PolicyService state.
Existing bilingual narrow-text assertions pass. Reviewed the actual screenshots:

- `ui.chinese-scales/162_ui-chinese-narrow-policy.png`
- `ui.graph-controls/238_ui-policy-runtime-operation-denied.png`

```sh
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict \
  -PevidenceDir=.omo/evidence/gui-policy-runtime-observation \
  --dependency-verification=strict --no-configuration-cache --console=plain
```

Expected: BUILD SUCCESSFUL and all six scenarios/checks pass. Keep source unchanged during the wrapper.
The UI report records synthetic input in a real Minecraft client; this is not a manual playthrough or comprehensive
coverage of every backend failure. The original-scope audit and goal remain open.
