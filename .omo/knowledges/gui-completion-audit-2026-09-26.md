# GUI completion audit against the original direction

The user authorized the original screen direction and continued polishing. This audit treats completion as unproven.
The latest six-case suite is evidence for its exercised workflows, not a substitute for the missing features below.
No requirement is removed merely because its current implementation is smaller or easier to verify.

## Current authoritative snapshot

Reviewed `domain.xml`, `domain.lss`, `FederationWorkspace`, `FederationGraphPresenter`,
`FederationDomainPolicySession`, `FederationDomainPolicyEntrance`, `FederationReleaseDialog`, request/reply handling,
and the Task 33 actual-client scenarios. Latest complete UI evidence:
`.omo/evidence/gui-compact-endpoint/attempt-20260926T141955132Z`: 6/6 scenarios, 1296 steps, 131/131 checks,
`BUILD SUCCESSFUL`. Earlier request protocol probe is documented in `gui-request-feedback-2026-09-26.md`.

## Requirement-by-requirement disposition

| Original requirement | Current evidence | Disposition / required next work |
| --- | --- | --- |
| Shared shell, four tasks, consistent controls and retained selection | XML/LSS, local page switching, English/Chinese screenshots, scale 2–4 and narrow 320x240 tests | Implemented for tested entrances and viewports. Preserve these during the remaining work. |
| Real relationship graph, two layers, selection, stable pan/zoom, search and contextual action | Graph projection uses live membership and claims; presenter and `ui.graph-controls` exercise layers, selection, focus, search and editor navigation | Implemented for current fixtures. The object list shares the inspector side rather than occupying a separate left pane; no information is fabricated. Policy permissions are not implied by ownership edges. |
| Searchable policy rule list plus selected editor | `FederationPolicyBrowser` shows real domain-scoped configured records, search, revision and direction; actual-client checks verify atomic key selection and highlight | **Implemented.** Browser/editor layout preserves space at narrow widths. Evidence and source details are in `gui-policy-browser-2026-09-26.md`. |
| Configured enabled/disabled/unconfigured and effective-state reasons | `ruleText()` separates configured data from last published binding observations, missing required operations and storage provenance diagnostics; see `gui-policy-runtime-observation-2026-09-26.md` | **Partial.** Crafting/Energy now expose revision-matched historical discovery reasons and crafting cycles. Missing provider and missing CPU transitions are exercised in the real client. Inactive relationship/domain-reference/consumer-interface reasons are implemented; energy-source absence, cycle creation/removal and real policy revision invalidation now have client/integration evidence. Other inactive branches and final audit remain open. Published snapshots are not current ACTIVE authorization. |
| Pattern summaries, search, output quantities, components, multi-output/fluid details | GenericStack projection and `ui.mapping` tests custom name, 64-bit amount, secondary-output search and fluid icon identity | Implemented for item/fluid fixtures. Inputs are first-possible-resource summaries, not a substitution editor. |
| Mapped/available/retained/conflicting target states | Target candidates and preview show mapped/owned/retained/unclaimed/other-owner states from live claims, with full ownership tooltips and state search; `gui-target-states-2026-09-26.md` records actual transitions | **Implemented for tested ownership transitions.** Unclaimed does not promise backend readiness. Unobserved/local/changed branches still need dedicated visual coverage in the final audit. |
| Retained release confirmation, cancellation, stale confirmation and authoritative result | Both languages' narrow dialogs, genuine dispatched-work retention, external mapping invalidation, cancel/confirm server assertions | Implemented for exercised paths. Existing authority checks remain mandatory. |
| Filterable diagnostic object table with status, owner and reason | A virtualized observation table shows node readiness, configured/runtime modes, owner and last claim result; localized search and full identity tooltips preserve the existing inspector | **Implemented for tested one- and two-endpoint observations.** `gui-endpoint-comparison-navigation-2026-09-26.md` verifies real distinct identities, ownership filtering, selected-row highlight and inspector switching. |
| Contextual links back to graph/mapping/policy from diagnostics | Locate centers the exact endpoint; Owner mappings and Processing policy atomically select the server-resolved relationship and wait for a correlated receipt | **Implemented for tested loaded-owner and unclaimed paths.** See `gui-diagnostic-destinations-2026-09-26.md`; navigation does not mutate claims/mappings/policies. |
| Bridge attachment context, initial attached-network focus, unavailable explanation | Actual host-grid MemberId selects/centers once, user view is preserved, reasons are localized; real outer attachment removal verifies disabled editing and readable explanation | **Implemented for tested attachment and disconnection paths.** See `gui-bridge-entry-2026-09-26.md`; remaining reason branches belong in the final audit. |
| Provider native menu retained, mapping entry and return | Real native PatternProviderScreen navigation is exercised in `ui.mapping` | Implemented. Preserve inventory/settings authority and container/distance checks. |
| Direct read-only Endpoint entry, actual/configured mode, native network, owner and face/return information | Direct entry and shared inspector tests; this iteration adds actual native ME network and owner provider instance epoch | Implemented for the fixture's confirmed network and owned endpoint. Unconfirmed network uses an explicit localized state. No arbitrary mode switch is added. |
| Loading/empty/error states and stale/conflict messages | Search empty states, no-provider text, pending/disabled controls, correlated replies, request conflict and font-thread guard | **Partial.** Real disabled Bridge presentation is now exercised. Device no-domain presentation is implemented and exercised with a real isolated powered Endpoint. Actual candidate-count and disabled-action assertions now verify ambiguous scope. Local position replaces the empty selector; opening before the first network tick and reopening after confirmation exercise unconfirmed and no-domain scope. See `gui-local-endpoint-lifecycle-2026-09-27.md`. |
| Final visual/interaction audit | Current screenshots and real text/hit tests cover implemented surfaces; remaining reason/empty-state branches still need evidence | **Not complete.** Repeat the full audit after implementing remaining scope. |

Operation/filter editing, batch mutations, invented live traffic and unsupported repair actions remain outside the
original initial scope. This is an explicit original boundary, not a new exclusion used to declare completion.

## This iteration's implemented diagnostic context

`endpointDetailText()` now displays the full server-confirmed native network ID from the endpoint subnet node.
Unconfirmed network identity is described explicitly. `endpointIdentityText()` includes the provider instance epoch
when the endpoint is owned, distinguishing an owner UUID from the particular provider instance. No unclaimed epoch
is fabricated. Both fields are localized and remain accessible through the existing independent scroll areas.

New actual-client checks compare displayed values to the real fixture network/provider identities, verify direct
Endpoint entry preserves the network identity, and scroll the narrow operation column to expose its final line.

```sh
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict \
  -PevidenceDir=.omo/evidence/gui-diagnostic-context \
  --dependency-verification=strict --no-configuration-cache --console=plain
```

Expected: `BUILD SUCCESSFUL`, 6/6 scenarios and all checks pass. Keep sources unchanged while the wrapper runs.
Next priority: policy rule list and genuine activation reasons, followed by diagnostic overview/navigation and Bridge
entry/empty-state behavior. The goal stays active until these outstanding original requirements have direct evidence.

Latest selected-policy follow-up: `.omo/evidence/gui-policy-runtime-observation/attempt-20260926T121516846Z`, 6/6 scenarios, 905 steps, 76/76 checks; 244 unit tests pass. Runtime reasons remain partial as recorded above.

Latest target-state evidence: `.omo/evidence/gui-target-states/attempt-20260926T122505166Z`, 6/6 scenarios, 944 steps, 79/79 checks; 244 unit tests pass. Diagnostic overview/navigation, Bridge entry/empty states and more specific runtime non-publication reasons remain open.

Latest Bridge evidence: `.omo/evidence/gui-bridge-entry/attempt-20260926T124102713Z`, 6/6 scenarios, 967 steps, 83/83 checks; 244 unit tests pass. Initial focus and actual outer-disconnection behavior are verified. Full goal remains open for the remaining requirements above.

Latest diagnostic-table follow-up: `.omo/evidence/gui-endpoint-table/attempt-20260926T125129030Z`, 6/6 scenarios, 1066 steps, 93/93 checks; 244 unit tests pass. Contextual navigation and final multi-endpoint audit remain open.

Latest comparison/navigation evidence: `.omo/evidence/gui-endpoint-comparison/attempt-20260926T130422276Z`, 6/6 scenarios, 1149 steps, 101/101 checks; 244 unit tests pass. Remaining contextual destinations, no-domain behavior, runtime reasons and final audit are still open.

Latest contextual-destination evidence: `.omo/evidence/gui-diagnostic-destinations/attempt-20260926T131158798Z`, 6/6 scenarios, 1168 steps, 105/105 checks; 244 unit tests pass. Device no-domain/ambiguous-domain states, specific runtime non-publication reasons and final full-scope audit remain open.

Latest visual direction: the user explicitly requested NeoECO/Data Energistics visual style as well as interaction ideas.
The shared shell is now light gray-violet with pixel bevels, dark text, gray-blue graph canvas and pale nodes.
See `gui-ae-visual-style-2026-09-26.md` for source references, screenshot review and the invalid background syntax fix.
Latest verification has 246 unit tests passing and the six-case totals above. No-domain behavior is recorded in
`gui-device-domain-states-2026-09-26.md`. The broader goal remains open for runtime reasons and the remaining audit gaps.

Latest theme details: localized provider observations explicitly describe the last target check; selector arrows and scrollbar chrome match the shared theme. See `gui-theme-detail-states-2026-09-26.md`. Runtime non-publication reasons and remaining final audit gaps are still open.

Latest runtime reasons: `gui-backend-reasons-2026-09-26.md` records actual missing-provider display, revision-filter unit tests and the passing energy source removal/replacement GameTest. 248 unit tests pass. Remaining scope is explicitly retained above.

Latest runtime transition: `gui-runtime-reason-transitions-2026-09-26.md` records the real AE2 native-provider placement and automatic missing-provider to missing-CPU update. Six scenarios, 113 checks and 248 unit tests pass. Remaining evidence gaps are preserved above.

Latest runtime evidence: `gui-runtime-cycle-revision-energy-2026-09-27.md` covers cycle/energy/revision transitions and fixes the screenshot-discovered stale displayed revision. It also corrects the ambiguous-domain evidence inventory using the existing direct Endpoint screenshot. 248 unit tests pass; the full goal stays active.

Latest local Endpoint evidence: `gui-local-endpoint-lifecycle-2026-09-27.md`; 6/6 scenarios, 129/129 checks, 248 unit tests pass. The real pre-first-tick menu open exposed and now verifies a null-binding fix. Dedicated compact Endpoint sizing and final full-scope visual audit remain open.

Compact Endpoint follow-up: `gui-compact-endpoint-2026-09-27.md` verifies the dedicated 440x280 default, initial centering, complete visible owned-device details and retained Chinese narrow layout. This closes the previously noted excessive default whitespace; final full-scope audit remains open.
