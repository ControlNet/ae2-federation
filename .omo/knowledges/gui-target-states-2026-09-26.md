# Mapping target ownership states

## Product behavior

The target selector now displays a localized state before the coordinates. The same label is used in its preview
and candidates, and the existing search matches the localized state. Stable endpoint identity remains the action
value; state/position/owner text is presentation, never parsed into an action identity.

The server projects current claim ownership relative to the selected Provider (including instance epoch), mappings
for the selected pattern slot, retention, and configured endpoint mode. Classification order:

1. No currently observed binding: Unobserved.
2. A different complete ProviderIdentity owns the claim: Occupied, even if a local mapping is stale.
3. A local mapping/retention exists without an observed owner: Changed, requiring server revalidation.
4. The selected slot is mapped: Mapped.
5. This Provider retains the idle claim and return path: Retained.
6. This Provider owns it but the selected slot does not map it: Owned.
7. An unclaimed endpoint is configured Local: Local mode.
8. Otherwise: Unclaimed.

Unclaimed is intentionally not called ready: actual policy, topology, native target and claim validation still happen
on action. Tooltip explanations spell out that distinction, ownership conflicts, and retention. Owned tooltips contain
the full owner UUID and provider instance epoch; all candidates retain full endpoint identity. No Claim is mutated by
projection. The selected target is retained by identity when state labels change.

This extends the NeoECO-inspired shared list/control/state treatment. It does not add another panel or shrink the
whole workspace. Target label font is 7 logical units; actual narrow Chinese captures fit retention plus coordinates.

## Verification and discovered library details

Final evidence: `.omo/evidence/gui-target-states/attempt-20260926T122505166Z`.
BUILD SUCCESSFUL; 6/6 actual-client scenarios, 944 steps, 79/79 checks. Unit suite: 244 tests, no failures/errors/skips.

The mapping scenario checks Owned -> Mapped -> Retained -> Unclaimed across real mapping, dispatch, unmapping and
release, and verifies the selected endpoint ID remains stable. The conflict scenario first proves that a rejected
competitor request does not turn the current owner's mapped target into Occupied. It then releases idle mappings
through the real controller, acquires a Claim through production authority with a generated competing identity, and
checks Occupied before any mapping attempt. This is a synthetic competing identity in the test fixture, not a second
physically placed Provider; the claim and UI data are real. The tooltip's full owner UUID is compared with that actual
acquired identity. Search finds the occupied candidate. No placeholder state is injected into the UI.

Reviewed actual captures:
- `ui.chinese-scales/88_ui-chinese-narrow-mapping.png`
- `ui.reject-claim-conflict/64_ui-target-occupied-before-mapping.png`

The initial client attempt failed because the test adapter reads Selector.text() as its stable ID, not its visible
label. Assertions now inspect `.__selector_preview__ .choice-label`. The displayed label was already correct in the
failure screenshot. LDLib2 HoverTooltips.append returns a new record; callers must retain its return value. The final
tooltip test invokes UIElement.collectHoverTooltips(), the same event/style resolution used by rendering.

```sh
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict \
  -PevidenceDir=.omo/evidence/gui-target-states \
  --dependency-verification=strict --no-configuration-cache --console=plain
```

Expected: BUILD SUCCESSFUL, all scenarios and checks pass. Keep source unchanged during the wrapper.
Ownership annotations are implemented for the exercised transitions. Unobserved/local/changed branches are explicit
source behavior but do not have dedicated new actual-client scenarios here. Broader GUI audit remains open.
