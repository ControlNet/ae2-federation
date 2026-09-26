# Bilingual Bridge diagnostic verification

The unavailable Bridge panel now says "Bridge domain unavailable" and asks the player to resolve the reported
condition before reopening. This also fits a valid connection without a usable domain; the earlier wording implied
every failure was a disconnected cable. Actual specific reasons remain server-provided.

Added a real Chinese 320x240 logical viewport scenario: remove the fixture's outer attachment, wait for the production
reason, open the Bridge menu, check hidden empty pages, text bounds and containment, and capture the diagnostic panel.
The initial passing run's screenshot exposed an untranslated `north` in the entrance caption. Bridge captions now reuse
the existing localized face components. English attachment assertions expect `North`, while machine evidence remains
the original `north:bridge;cable-extension:5`. The final Chinese test requires the rendered north translation.

Final evidence: `.omo/evidence/gui-bridge-bilingual-final/attempt-20260926T143352769Z`.
BUILD SUCCESSFUL; 6/6 scenarios, 1308 steps, 137/137 checks. Viewed the final Chinese screenshot
`ui.chinese-scales/255_ui-chinese-narrow-bridge-unavailable.png`: localized north, readable title/help/reason, no clipping.
This closes the specifically missing Chinese unavailable-Bridge coverage. Other reason-specific scenarios and the
full completion audit remain open. The user requested pausing after this batch; finish commit/push before pausing.

```sh
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict \
  -PevidenceDir=.omo/evidence/gui-bridge-bilingual-final \
  --dependency-verification=strict --no-configuration-cache --console=plain
```

Expected: BUILD SUCCESSFUL and all six scenarios/checks pass. Keep source unchanged during execution.
