# Searchable selectors and font-thread fixes

## GUI changes

Federation selectors now carry a search field inside the existing LDLib2 popup (all object/pattern selectors, excluding
the four-option capability selector). Filtering matches localized display text and the full stable ID, including device
coordinates where available. Filtering does not send a server request or replace the confirmed selection. A matching
candidate still uses the existing authorized selection request. An explicit empty-result label replaces a blank popup.
The policy page states consumer -> capability <- provider in localized prose. Rule details use the localized capability
name instead of its Java enum name. Native input tests cover missing results, coordinate matching, and selecting an
encoded pattern through a filtered popup; screenshots verify search popup placement and policy direction.

## Reproducible font-thread defects

The earlier intermittent `NativeImage: Image is not allocated` crash could not be proven resolved by passing reruns.
A test-only thread assertion established a concrete defect in LDLib2 2.2.34: integrated-server menu construction invokes
`Label.<init> -> TextElement.setText -> recompute -> getFont` on `Server thread`. `LDLib2.isClient()` checks physical
client distribution, not the render thread. Baseline `.omo/evidence/gui-font-thread/attempt-20260926T102355574Z`
failed all six scenarios opening the production workspace; the complete causal stack is in `ldlib2/report.json`.

`compat.LDLibTextThreadMixin` is client-only and cancels `TextElement.recompute` outside Minecraft's main/render thread.
The server still stores component text and bindings; independent client elements perform visual measurement/layout.
The fix passed the same six scenes with the original TextElement font-access assertion enabled in attempt
`20260926T102545477Z`.

A broader assertion at `Font.getFontSet` then caught a separate path on `Worker-Main-41`:
`SessionSearchTrees.updateRecipes -> ItemStack.getTooltipLines -> GuideMe OpenGuideHotkey.handleTooltip ->
makeProgressBar -> Font.width`. GuideMe 21.1.1 computes an interactive key-hold progress hint while vanilla builds
search indices in the background, touching the shared font cache and shared hotkey state. This is documented in
attempt `20260926T102747850Z/actual-client.log`. Its six scenario statuses passed despite a logged background exception;
therefore those statuses alone do NOT qualify the font-thread invariant.

`compat.GuideTooltipThreadMixin` skips only that interactive GuideMe tooltip handler outside the render thread. Normal
render-thread hover hints are unchanged. Both mixins are in the client list, with required injection sites for the pinned
dependency versions; neither is loaded on a dedicated server. Review these compatibility fixes when upgrading LDLib2 or
GuideMe rather than silently weakening required injections.

The permanent test assertion now guards `Font.getFontSet` for any caller. `FontThreadEvidence` accumulates violations
across the client process so swallowed asynchronous exceptions fail the final scenario's zero-violation check.
These are demonstrated unsafe paths and plausible causes of the old reload crash; do not claim evidence excludes every
possible font defect or arbitrary dependency/resource-pack combination.

## Final verification

`.omo/evidence/gui-font-thread/attempt-20260926T103005341Z`: 6/6 scenarios, 495 steps, 27/27 checks, including zero font
cache thread violations. Actual-client log has no `Image is not allocated` or font-thread assertion error. Unit XML:
237 tests, zero failures/errors/skips. `git diff --check` passes. Temporary reflection probes from the previous turn are
absent. This is progress toward the full GUI goal, not completion of the remaining diagnostics/layout/state audit.

```sh
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict \
  -PevidenceDir=.omo/evidence/gui-font-thread \
  --dependency-verification=strict --no-configuration-cache --console=plain
```

Expected: `BUILD SUCCESSFUL`, all six scenarios and all 27 checks pass, no font-thread violations in the client log.
Keep source unchanged during the run because the wrapper verifies source/product identity.
