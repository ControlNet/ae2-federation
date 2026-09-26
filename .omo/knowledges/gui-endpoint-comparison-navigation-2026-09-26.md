# Multi-endpoint comparison and diagnostic graph navigation

## Implemented navigation

The diagnostic toolbar has a localized Locate action. It uses the server-confirmed scoped endpoint ID already bound
to the inspector, checks that the current graph contains it, then shows Overview and queues that exact object for
selection/centering. This is read-only client navigation; no mapping, claim or policy mutation is sent.

Queued focus waits for a nonzero graph viewport and initial fit. It runs once at the existing graph screenTick point,
keeps the current scale, updates the inspector and emphasizes the selected node/edges. Explicit user selection clears
queued focus. The call inside Wires remains explicitly qualified with FederationGraphPresenter.this.select to avoid
UIElement.select's inherited query method. No selectable scoped endpoint means the Locate action is disabled.

## Real multi-endpoint fixture

The Endpoint scenario additionally places a second production Endpoint at the first endpoint's east(2) position,
asserting the target block is empty before placement. It seeds the existing native network identity using the established
test helper, creates a real AE grid connection and waits for the node to be active/booted and both endpoints to appear
in the domain projection. The additional block is removed during fixture teardown.

The original endpoint remains owned and Federated. The second is genuinely unclaimed; the final captures show its
configured Local mode and unbound runtime. Both native nodes are ready. This verifies why native readiness and actual
endpoint binding must not be collapsed into one ready/failed label. No placeholder row or simulated UI snapshot is used.

The table checks both complete identities, filters the unclaimed endpoint, verifies its inspected identity/position,
checks exactly one selected row on reopening, and returns to the original identity through the table. The fresh menu
may initially select either endpoint because the domain list is sorted by opaque scoped ID; returning from the explicitly
selected second endpoint to the original proves a transition between distinct endpoint identities.

## Final evidence

`.omo/evidence/gui-endpoint-comparison/attempt-20260926T130422276Z`: BUILD SUCCESSFUL; 6/6 actual-client scenarios,
1149 steps, 101/101 checks. Unit suite: 244 tests, zero failures/errors/skips. git diff --check passes.

Graph navigation is checked against the exact confirmed scoped identity, not just a coordinate label. ElementBounds
uses transformed screen coordinates; tests wait until the selected node center matches the canvas center within one
logical unit. The test repeats navigation after zooming and hiding the graph to exercise deferred viewport layout.
Chinese narrow-layout tests check the selector/overview/locate bounds and both button labels.

Reviewed final screenshots:
- `ui.endpoint/143_ui-endpoint-comparison.png`
- `ui.endpoint/200_ui-diagnostics-locate-endpoint.png`

```sh
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict \
  -PevidenceDir=.omo/evidence/gui-endpoint-comparison \
  --dependency-verification=strict --no-configuration-cache --console=plain
```

Expected: BUILD SUCCESSFUL and all six scenarios/checks pass. Keep source unchanged during the wrapper.

## Remaining original scope

Diagnostic-to-graph navigation and two-endpoint comparison/selection now have direct evidence. Diagnostic-to-mapping
and diagnostic-to-policy contextual navigation are still missing. Device no-domain/ambiguous-domain presentation,
more specific runtime non-publication reasons and the final full-scope audit remain open. Do not mark the goal complete.

Follow-up: `gui-diagnostic-destinations-2026-09-26.md` implements the remaining mapping and policy destinations with live server resolution and correlated navigation confirmation. Its final client evidence supersedes the missing-destination note above.
