# Bilingual narrow layouts and release dialogs

## Findings and fixes

The English mapping scenario now resizes the real window to 960x720 at GUI scale 3 (320x240 logical pixels), checks
all four pages, and completes release cancellation, external mapping invalidation and fresh confirmed release at that
size. The Chinese scale-4 scenario additionally opens and cancels its release dialog at 1280x960 (also 320x240 logical).
Both restore the normal window and include teardown restoration after failures.

Baseline `attempt-20260926T112853247Z` demonstrated that the English action labels and mapping feedback overflowed.
The compact-action font declaration lost to the generic button selector's specificity. Corrected specificity now
applies the intended size 7 text. Compact tabs also use size 7 so "Pattern mapping" fits. The target column uses
slightly tighter compact gaps, and mapping feedback has its own vertical scroll viewport, preserving long messages
without covering the session footer.

An intermediate run caught a collapsed feedback label: applying `flex: 0` to content in this layout did not provide
the desired intrinsic sizing. Removed flex from the label entirely. The viewport owns remaining space while the
label uses adaptive height and does not shrink, matching the already verified diagnostic scrolling approach.

## Evidence

Final run `.omo/evidence/gui-narrow-bilingual/attempt-20260926T113349296Z` reports `BUILD SUCCESSFUL`, 6/6 scenarios,
764 steps and 62/62 checks. Added checks measure rendered text, control containment and scroll-to-final-line geometry.
The existing authoritative server checks verify retained ownership on cancellation and actual release on confirmation.
Screenshots include all four English narrow pages, both languages' narrow release dialogs, and final release feedback.

- `ui.mapping/252_ui-english-narrow-mapping.png`
- `ui.mapping/282_ui-release-confirmation.png`
- `ui.mapping/322_ui-release-complete.png`
- `ui.chinese-scales/94_ui-chinese-narrow-release.png`

```sh
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict \
  -PevidenceDir=.omo/evidence/gui-narrow-bilingual \
  --dependency-verification=strict --no-configuration-cache --console=plain
```

Expected: all six scenarios and all checks pass with `BUILD SUCCESSFUL`. Do not edit source while this runs.
The outstanding narrow English/modal coverage from the previous knowledge file is now addressed for these fixtures.
Request-in-flight feedback and the final original-scope audit remain open; this does not complete the broad goal.
