# Task 33 LDLib2 Fabric Workspace

## Production ownership

- `FabricPolicySession` remains the server authority for UI intent. Provider selection is resolved from the live
  `ProviderObservationRegistry` inside the session's current `FabricReference`; Endpoint detail is read from live
  `EndpointTargetBinding` entries in that same Fabric.
- Mapping changes obtain a fresh `PatternSlotHandle` immediately before calling
  `MappedPatternProvider.replaceMapping`. Policy and Claim denial states produce explicit rejection acknowledgments.
- `FabricGraphProjection` derives stable member, Provider, and Endpoint node IDs from Task 32 observation identities.
  Physical edges identify native Grid membership; capability edges exist only for actual owned Endpoint Claims. Pattern
  quantities come from decoded native inputs as `possibleInput.amount() * input.getMultiplier()` with checked arithmetic.
- Claim rejection diagnostics originate in `EndpointClaimAuthority`, pass through `EndpointTargetBinding` and
  `FabricPolicySession`, and arrive through the ordinary S2C component binding. Scenarios never mutate production labels.

## Client behavior

- The shared `fabric.xml` is a three-region workspace. It retains all Task 15 policy IDs and adds stable graph, layer,
  virtual-list, Pattern mapping, search, and Endpoint diagnostic IDs.
- LDLib2 `GraphView` owns pan/zoom. `FabricGraphLayoutCache` recomputes grouped coordinates when topology revision or the
  deterministic projected node/edge signature changes; status/data-only snapshots reuse the exact layout object.
- Member and Pattern lists use pinned LDLib2 2.2.34 `VirtualScrollerView`. Pattern filtering is client-local; authoritative
  inventory and mapping values still originate on the server.
- Pattern rows use visible ASCII ` | ` field separators. Literal tabs are unsuitable for Minecraft's font renderer because
  they appear as square control glyphs; snapshot serialization keeps its internal tab delimiters unchanged.
- English and Simplified Chinese resources share the same controls. Endpoint and Bridge placeholder models distinguish
  Federation, input, and return faces for actual-world captures.
- The workspace is capped at `396x236` logical units so it remains fully visible in Minecraft's genuine GUI scale 4
  minimum viewport (`400x240` at `1600x960`). Long policy actions stack vertically and constrain LDLib2's internal
  `.__button_text__` child; paired mapping actions use direct compact text metrics. UI-only identities are shortened to
  16 characters while evidence attachments retain full identities.
- Every Task 33 scenario clicks the production `Fit` control after the graph is laid out. This avoids initial graph
  clipping without changing topology coordinates or the layout cache contract.

## Qualification

Run from the repository root:

```bash
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict \
  -PevidenceDir=.omo/evidence/task-33 \
  --dependency-verification=strict --no-configuration-cache --warning-mode=fail
```

The six actual-client scenarios build a real Hub/Bridge Fabric, a mapped Provider, and a claimed production Endpoint.
The shared verifier checks screenshots, selector bounds, independently observed live mapping Lanes, live Endpoint
identity/mode, visible multipart attachment evidence, a real policy revision acknowledgment, actual GUI scale coverage,
rendered Chinese quantity evidence, Claim conflict rejection, and unobscured Hub/Bridge/Provider/Endpoint world captures.
Rebound probes reject forged mapping Lane state, fabricated multipart diagnostics, and Claim conflict success substitution.

Latest source-bound acceptance is `.omo/evidence/task-33/attempt-20260921T120727803Z` (`6/6` scenarios,
`14/14` checks, 10 captures, actual client exit 0). It covers GUI scales 2, 3, and 4, visibly renders `4000000000`, reports
`side north / type bridge / cable extension 5.0`, renders `OWNER_CONFLICT`, performs a real policy edit, and captures the
live Hub, north-side multipart Bridge, Provider host, and Endpoint without the workspace obscuring the world. All six UI
captures render readable inline pipe separators without square control glyphs in English or Simplified Chinese.
