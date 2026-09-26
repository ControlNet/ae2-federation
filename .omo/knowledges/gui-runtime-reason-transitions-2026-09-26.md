# Runtime reason coverage and real crafting transition

Previous goal turn was progress: typed historical backend reasons, real missing-provider client evidence and the
energy source-removal/replacement regression test. This turn expands existing reconciliation observations and proves
that a backend condition change replaces the previous UI reason.

## Changes

- `BindingDiagnostic.inactiveReason` maps the actual PolicyActivationState returned by existing policy activation.
  It rejects ACTIVE rather than manufacturing an inactive reason. The service calls activation once, as before.
- Crafting/energy failed activation now records the actual inactive classification. Empty domain references record
  a separate reason. Energy additionally distinguishes a missing directional interface on the consuming network.
- All 11 diagnostic reason enum values have both English and Chinese translation keys (source/locale comparison).
- Existing policy/topology revision filtering and historical "Last backend check" semantics are preserved.
  These remain observations, not current operation authorization or a new readiness decision.

## Actual-world transition

After the existing missing-provider check, `ui.graph-controls` places a real AE2 Pattern Provider at the endpoint's
`east(4)` position, seeds the already-confirmed native network identity, joins its node to the actual target grid,
and waits for active/booted status. The existing production crafting backend registry then observes a provider but
no CPU. The UI must show "No native crafting CPU" and no longer contain the missing-provider explanation.
No fabricated provider, CPU status, or GUI row is used. The fixture stores its owned position for teardown.

Evidence: `.omo/evidence/gui-runtime-reason-transitions/attempt-20260926T134927789Z`.
BUILD SUCCESSFUL; 6/6 scenarios, 1224 steps, 113/113 checks; 248 unit tests, zero failures/errors/skips.
Visually inspected `ldlib2/screenshots/ui.graph-controls/247_ui-policy-runtime-cpu-missing.png`; reason text is readable.

```sh
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict \
  -PevidenceDir=.omo/evidence/gui-runtime-reason-transitions \
  --dependency-verification=strict --no-configuration-cache --console=plain
```

Expected: BUILD SUCCESSFUL, all six scenarios/checks pass, reason changes after the real native provider joins.
Keep sources unchanged while this wrapper runs.

## Remaining evidence limits

Missing native provider and missing CPU now have actual-client evidence, including transition/replacement. Energy
source/cycle/identity and new inactive/domain-reference/consumer-interface branches have implemented projection and
locale coverage but no dedicated client screenshots yet. Existing unit tests cover revision mismatch rejection;
actual policy/topology invalidation still needs targeted integration evidence. Device ambiguous/unconfirmed-domain
fixtures and final scope audit remain outstanding; the active goal is not declared complete.
