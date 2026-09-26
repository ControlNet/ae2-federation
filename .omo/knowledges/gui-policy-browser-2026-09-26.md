# Configured policy browser and runtime-status investigation

## Implemented rule list

The policy editor has a Rules action opening a searchable virtual list. This browser/editor arrangement retains the
usable two-column editor at narrow widths rather than adding a permanently narrow third column. It is a real rule
list: the server projects configured records whose consumer and provider both belong to the authorized domain.
Tombstones, unrelated domains and self-relationships are excluded. Unconfigured combinations remain available
through the existing network/capability selectors and enable action; they are not fabricated as configured records.

Rows show capability, configured enabled/disabled state, revision and consumer-to-provider direction. Full IDs are
available in tooltips. The server-confirmed current rule is highlighted. Search includes real full IDs, capability and
localized summary text, not JSON field names (otherwise searching "enabled" incorrectly matches disabled records).
No configured rules and no search matches are separate states. Browser height is bounded by both row count and the
actual viewport, with a full-width close button.

Selecting a row sends one `SELECT_TARGET` action with `policy:<consumer>/<provider>/<capability>`. The server parses
the complete key, validates distinct members and the existence of its configured record, then updates all three
selection dimensions together under the existing menu/nonce/sequence/context/revision checks. No policy is mutated by
selection. The editor only reflects synchronized confirmed values.

## Verification

Final evidence: `.omo/evidence/gui-policy-browser/attempt-20260926T120318945Z`, `BUILD SUCCESSFUL`, 6/6 scenarios,
898 steps, 74/74 checks. The graph scenario installs a genuine disabled Crafting rule via PolicyService, exercises
no-result search, enabled/disabled exclusion, capability search, atomic endpoint selection and selected-row highlight.
The Chinese scenario searches the genuine Processing rule at 320x240 logical resolution and checks browser controls
and close-label fit. Existing mapping, release, request-conflict and diagnostic scenarios still pass.

Screenshots:
- `ui.graph-controls/221_ui-policy-rule-browser.png`
- `ui.chinese-scales/178_ui-chinese-narrow-rules.png`

```sh
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict \
  -PevidenceDir=.omo/evidence/gui-policy-browser \
  --dependency-verification=strict --no-configuration-cache --console=plain
```

Expected: all six scenarios and all checks pass, `BUILD SUCCESSFUL`. Keep sources unchanged during the wrapper.

## Runtime activation remains an explicit open requirement

The browser deliberately labels values as configured state. `ruleText()` still does not report activation. The source
investigation found these constraints for the next implementation:

- `PolicyService.activation(key, PolicyRuntimeEndpoints)` needs the genuine consumer/provider grids and backend
  readiness. Passing assumed `BackendStatus.READY` from the UI would misrepresent runtime state.
- Storage readiness includes provenance/source validation. `StorageMountService.lastDiagnostic(key)` exposes a
  `ProvenanceDiagnostic`; `effectiveProjection`/`effectiveRelationship` expose published relationships. `get(level)`
  creates a service, and reconciliation has effects; do not initialize empty services merely to manufacture a status.
- Crafting and Energy `capability(key)` call `reconcileAll()`. Their binding `isCurrent()` callbacks validate live
  authority; Energy's callback can withdraw/reconcile stale bindings. Do not call these repeatedly for every browser
  row on each serialization tick without designing the diagnostic projection and cost.
- Processing's `ProviderTargetResolution` is a last target resolution, with distinct offline, unsettled identity,
  claim, policy, disconnected-domain and native-target reasons. A single provider's last resolution is not proof that
  an entire rule is currently active; match the actual source/target and label historical observations honestly.
- The current observability `PolicyState` projection only says enabled/disabled and cannot supply activation evidence.

The rule-list requirement is now implemented. Genuine activation reasons, the remaining diagnostic/Bridge work and
the final original-scope audit remain open. Do not treat this passing list test as completion of the whole policy page.

Follow-up: `gui-policy-runtime-observation-2026-09-26.md` adds honest published-binding snapshots, operation requirements, storage source diagnostics and scrollable details. The runtime investigation above is historical; complete live activation reasons remain open.
