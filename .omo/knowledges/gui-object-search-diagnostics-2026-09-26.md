# Graph object search and structured endpoint diagnostics

## Implemented behavior

The graph sidebar now searches all network/provider/endpoint nodes by localized kind, coordinates, and full scoped ID.
It retains the graph when results are empty, presents a no-result message, and focuses a chosen node while retaining
zoom. Object-list items are refreshed only when IDs/display text change, so status-only updates do not reset scrolling.
Full opaque identifiers in graph tooltips wrap after 32 characters. The canvas remains an observation/navigation view;
search and selection do not mutate domain policy or physical connections.

A compact root class applies below 280 GUI units of screen height. It reduces the object list to one scrollable row and
hides the redundant member-count caption, reserving space for the selected object's four-line summary and action.

Endpoint diagnostics now separate Operation from Identity and ownership. Operation shows actual position, configured
mode, installed runtime mode (or not bound), Federation face, return-binding presence, and a readable localized claim
result. Identity shows the full endpoint UUID, instance epoch, full owner-provider UUID or unclaimed, claim epoch,
runtime generation and original result code. Return-binding presence does not claim that a machine/network is powered
or that a job is active. Runtime data comes from the inspected live binding, including the direct local Endpoint entry.

The description above pages now uses adaptive height. At GUI scale 4, a fixed 24-unit description consumed space that
the identity text needed. A real font/wrapping measurement exposed vertical overflow despite an initially plausible
screenshot. The test now compares computed rendered line height/width against element content bounds; no threshold was
relaxed to make the layout pass. This assertion covers compact graph detail and both diagnostic columns.

## Verification and evidence

Final strict wrapper run `.omo/evidence/gui-object-search/attempt-20260926T105138228Z` passes 6/6 scenarios, 595 steps,
40/40 checks. Unit XML totals are 237 tests, zero failures/errors/skips; `git diff --check` passes. The font-cache guard
has zero violations and the log has no `Image is not allocated` failure.

New input checks exercise missing graph results, retained visible canvas, coordinate search, focusing a result while
preserving zoom, Chinese endpoint search, full live endpoint UUID, actual Federation face, configured/runtime mode,
compact text fit, localized owner-conflict explanation and the preserved raw rejection code.

The wrapper's old flattened diagnostic assertions were updated to validate the new two-column evidence separately.
It now checks full endpoint UUID against the live server identity, configured/runtime Federated mode, claim epoch 1,
and both readable conflict text and OWNER_CONFLICT code. Attachments include endpointIdentity as well as endpointDetail.
Do not restore the obsolete flattened-text checks or weaken these invariants.

```sh
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict \
  -PevidenceDir=.omo/evidence/gui-object-search \
  --dependency-verification=strict --no-configuration-cache --console=plain
```

Expected: BUILD SUCCESSFUL and report.txt with all six scenarios and forty checks passing. Do not edit source while
running: the wrapper checks source/product identity.

## Remaining audit points

The full GUI goal remains active. Mapping acknowledgments still contain implementation codes; richer pattern stack
components/fluid output rendering, stale-dialog/action-in-flight behavior, narrower window layouts and large-domain
behavior still require attention. Diagnostic native network/owner-instance identity context can be expanded.
Graph search currently resolves each node's label by scanning the snapshot; replace that quadratic lookup before
claiming large-domain search performance. Headless graph captures still show a tooltip around the centered selected
node despite synthetic hover moving away; investigate cursor/render synchronization before presenting those as clean
unhovered composition captures. This observation is not proof that tooltips persist incorrectly in normal play.
