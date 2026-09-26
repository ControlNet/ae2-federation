# Narrow viewport layout and scrolling

The full Chinese scenario now resizes the real GLFW window from 1600x960 to 1280x960 at GUI scale 4, verifies a
320x240 logical viewport, exercises all four workspace pages, then restores the normal window. Teardown also restores
the window after a failure. Existing evidence attachments are captured at the original size; the added screenshots
record the actual narrow framebuffer dimensions separately.

## Findings and fixes

The baseline `attempt-20260926T111952718Z` failed two new checks. Mapping selection text overlapped its action buttons,
and diagnostic identity text extended below its content area. The screenshots corroborate both measured failures.

- Mapping selection labels now use adaptive height and do not shrink. Long retained-endpoint explanations wrap above
  the action buttons without overlap.
- Both diagnostic columns contain independent vertical ScrollerViews. Their labels size to their complete content;
  the viewport clips overflow and exposes a scrollbar when needed. Headings and endpoint selection remain visible.
- The test measures wrapped text and sends real wheel events over the identity area, then verifies the final line's
  bounding box can enter the viewport. It also checks graph details, policy text and mapping action labels at 320x240.

LDLib2 2.2.34 `Scroller.Vertical.onScrollWheel` uses only the sign of deltaY, taking one configured step per event.
An event with magnitude 20 does not scroll twenty steps. The intermediate run failed its bottom-reach check for that
reason; the corrected test sends twelve separate wheel events and verifies the final geometry rather than assuming
the requested magnitude was honored.

## Evidence

Final run: `.omo/evidence/gui-narrow/attempt-20260926T112444358Z`, `BUILD SUCCESSFUL`.
The report and four `ui-chinese-narrow-*` screenshots are the authoritative results. The full six-case suite includes
normal-size English scenarios, Chinese resizing, mapping and release invalidation, native Provider navigation, direct
Endpoint inspection, multipart attachment and claim-conflict handling. This is not evidence for every arbitrary
window/resource-pack/language combination; English at the narrow size and narrow release-modal interaction still
need explicit coverage in the final scope audit.

```sh
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict \
  -PevidenceDir=.omo/evidence/gui-narrow \
  --dependency-verification=strict --no-configuration-cache --console=plain
```

Expected: `BUILD SUCCESSFUL`, all six scenarios and all checks pass. Do not edit sources during execution.
The broader polish goal remains active, including in-flight request feedback and the complete screen-direction audit.
