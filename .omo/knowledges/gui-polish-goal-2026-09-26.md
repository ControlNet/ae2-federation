# GUI polish goal and completion audit

The user requested continued polishing until the GUI is complete and verified. The earlier first-pass implementation is
progress, not evidence that this goal is achieved. Keep the goal active while any material workflow or visual requirement
below remains unfinished or unverified.

## Design direction

Visual thesis: a quiet AE-compatible instrument panel, with a dark relationship canvas and restrained cyan selection.
Content plan: task navigation, primary workspace, object context, explicit action and authoritative result.
Interaction thesis: stable graph pan/zoom; clear hover/selection feedback; deliberate modal confirmation for release.
Avoid ornamental motion, excessive borders and empty-slot filler in the working list.

## Requirements and evidence still needed

- [ ] Recognizable provider/endpoint labels and precise identity/location tooltips.
- [ ] Useful pattern search, clear no-result/empty-provider states, useful row quantities and mapping state.
- [ ] Graph selection visibly highlights the node and enables relevant editor navigation; filters/search for object lists.
- [ ] Readable localized operational status and structured endpoint diagnostics, including genuine pending/error states.
- [ ] Target-specific retained-release modal with explicit consequence, cancellation and unchanged server authority checks.
- [ ] Searchable object selection and clear policy direction/current effective/configured state.
- [ ] Coherent spacing, typography and hierarchy across all pages and native-device entry paths.
- [ ] Verified compact/wide layouts, English/Chinese, dropdown/modal hit testing and clipping.
- [ ] Actual-client coverage for new workflows, cancellation, stale state and server-confirmed changes.
- [ ] Final completion audit against the proposed screen direction and these requirements using current screenshots,
      runtime evidence, unit tests, and source inspection. A green six-case first-pass suite alone is insufficient.

The original scope includes Router, Bridge, Provider and Endpoint entries, shared Overview/Policies/Mapping/Diagnostics,
and reusable overlays. New policy operation/filter semantics, batch transactions and fabricated traffic remain outside
the agreed UI redesign. Do not substitute a small passing subset for the full intended end state.

## Reference-driven iteration

Re-read the checked-out references in `/tmp/ae2-gui-research-k2AQa8` on this goal turn:

- NeoECO `gui/task/ComputationTaskCards.java`: explicit output amount, readable summary, state accent, full details in
  tooltips. Applied to encoded-only pattern lists with output counts, mapping counts and searchable rows. Empty slots
  remain deliberately available through a toggle rather than filling the initial list.
- NeoECO `gui/common/HostText.java` and the earlier source review motivate reusable precise display/tooltip formatting;
  current native 64-bit quantities remain exact.
- OmniSequence `ui/omni_computation.xml`: concrete key/value hierarchy and restrained contextual labels. Device selectors
  now receive genuine block coordinates; full stable identities remain available in tooltips.
- DataEnergistics `lss/crafting_plan_tree.lss`: explicit text roles and popup hierarchy. The graph inspector has a dedicated
  contextual navigation action; release is a modal rather than another persistent panel.
- KilaGraph/LDLib2 graph interaction: selection should visibly affect the node and connected wires. Added persistent node
  emphasis and graph-to-mapping/diagnostics navigation, with server selection confirmation before changing page.

## Release confirmation implementation and verification requirement

`PREPARE_RELEASE` always clears the previous pending release before obtaining the production controller's confirmation;
`CANCEL_RELEASE` clears it without releasing. The dialog displays the endpoint UUID, coordinates, claim epoch and actual
return-path consequence. It opens only after the prepared state and newer menu authority both arrive. Confirmation still
uses the existing `RELEASE_ENDPOINT` server checks, including comparison against the current controller confirmation.
Stale revision/context states must not keep a prepared dialog available.

The first runtime attempt correctly showed that an idle, never-dispatched endpoint auto-releases on last unmap. That is
production behavior and must not be changed for the GUI test. The fixture is being extended to two distinct powered AE2
networks with an actual ME item cell. The retention check will push one iron input through the real native Provider lane,
verify native push acceptance and the input item appearing in the target subnet storage, then unmap. This tests genuine dispatched-work retention; it
is not a full crafting CPU job test and must not be represented as one. No retained/dispatched flag is set by the test.


## Follow-up visual and reload audit

The Chinese scale-4 screenshot confirmed that the target-specific dialog fits inside the workspace and wraps the
return-path consequence. The English graph screenshot exposed stretched zoom controls; constrain their maximum width
as well as flex growth, since the shared toolbar declares `flex: 1`. Empty pattern rows must not show a zero output
quantity or repeat the mapped-count line. The old inline "press again" release instruction is obsolete with a modal.

The native Provider return path reproduced `NativeImage: Image is not allocated` after language resource reloads in
attempts `20260926T100215189Z` and `20260926T100737027Z`. Explicitly awaiting the reload future did not fix it; do not
claim the crash resolved by that test improvement. The stack is in vanilla BitmapProvider/FontSet while rendering the
native PatternProviderScreen button. Further font-cache diagnosis is required.


## Verified follow-up snapshot

Final production/test source snapshot passed `:neoforge-1.21.1:federationUiTest` with the exact six-case selection in
`.omo/evidence/gui-polish/attempt-20260926T101714854Z`: 6/6 scenarios, 427 steps, 22/22 checks. Unit XML totals:
237 tests, zero failures/errors/skips. This snapshot contains no temporary reflection/font probe. It includes coordinate
labels and identity tooltips on graph device nodes, bounded zoom buttons, cleaned empty pattern summaries, modal release
copy, and explicit resource-reload future completion in the Chinese scenario.

The font crash remains an unresolved intermittent limitation, not a proven fix. A temporary read-only reflection probe
observed the cached default FontSet identical to the current FontManager map entry; this does not establish stale cache
identity as the root cause. Both a diagnostic run and the final run without diagnostics passed. Earlier failures are
retained in the evidence directories. Do not claim repeated language reload/native-screen transitions are fully stable.

Reproduce the complete verification:

```sh
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict \
  -PevidenceDir=.omo/evidence/gui-polish \
  --dependency-verification=strict --no-configuration-cache --console=plain
```

Expected signals: `BUILD SUCCESSFUL`, an evidence directory, and `ldlib2/report.txt` reporting all six scenarios and
all checks passing. Do not edit source while this task is running: the task validates its source/product identities.


## Subsequent font-thread and selector work

See `gui-search-font-thread-fix-2026-09-26.md` for direct evidence of two unsafe font-cache access paths, client-only
compatibility fixes, and the broader zero-thread-violation assertion. Final evidence `20260926T103005341Z` passes six
scenarios, 495 steps and 27 checks. Searchable dropdowns and explicit localized policy direction are implemented and
covered by real-input checks. The remaining full GUI completion audit is still open.


## Graph search and diagnostic iteration

See `gui-object-search-diagnostics-2026-09-26.md`: all-object graph search, focus preserving zoom, a compact inspector,
and localized two-column endpoint diagnostics are implemented. Final run `20260926T105138228Z` passed 595 steps and
40 checks. Compact text fits are measured with the actual rendering font/wrapping utility. Full-goal audit remains open.

## Localized mapping feedback and stale confirmations

See `gui-feedback-release-invalidation-2026-09-26.md`. Final run `20260926T110602419Z` passed six scenarios,
605 steps and 42 checks, with 241 unit tests passing. Mapping results are localized with explicit tones and raw-code
tooltips. Prepared releases disappear after an external mapping edit; stale preparation is not reused. The clean
graph overview in the feedback evidence also resolves the outstanding need for a non-obstructed graph capture.
Component-rich/generic-resource pattern presentation and the final full-scope audit remain outstanding.

## Generic-resource pattern follow-up

See `gui-generic-patterns-2026-09-26.md`. Registry-aware GenericStack projection now preserves item components and
long output amounts, renders fluid icons and searches secondary outputs. Real encoded-pattern fixtures verify these
behaviors. Final run `20260926T111558750Z` passed six scenarios, 650 steps and 44 checks. This resolves the previous
item-ID-only projection limitation for the verified item/fluid cases; the complete GUI goal audit remains open.

## Narrow viewport follow-up

See `gui-narrow-layout-2026-09-26.md`. Real window resizing exposed clipped mapping selection and diagnostic identity
text at 320x240 logical resolution. Adaptive mapping selection height and scrollable diagnostic columns resolve those
failures. Final run `20260926T112444358Z` passes the six-case suite with four narrow Chinese page captures and an actual
scroll-to-bottom check. Remaining scope is recorded explicitly in that knowledge file; the full goal is still active.

## Bilingual narrow layouts and modal coverage

See `gui-narrow-bilingual-2026-09-26.md`. English four-page coverage and both languages' narrow release dialogs now
pass. English testing exposed overridden action font size and long mapping feedback overflow; corrected style
specificity and an independently scrollable feedback region resolve them. Final run `20260926T113349296Z` passes
6/6 scenarios, 764 steps and 62 checks. In-flight feedback and the complete original-scope audit remain open.

## Request feedback follow-up

See `gui-request-feedback-2026-09-26.md`. Correlated server replies and the local progress gate now implement waiting,
duplicate-click prevention and explicit dispatch rejection feedback. Accepted dispatch also waits for newer state;
rejected dispatch does not depend on a sequence increment. Final run `20260926T114425158Z` passes 6/6 scenarios,
770 steps and 64 checks; 244 unit tests and the executable packet probe pass. The remaining full-goal work must now
be derived from an explicit audit against the original screen-direction requirements rather than assumed complete.

## Explicit original-scope audit

`gui-completion-audit-2026-09-26.md` now records requirement-by-requirement evidence and gaps. It identifies remaining
policy list/activation, diagnostic table/navigation, target state annotations, Bridge focus/reasons and unavailable-state
coverage. This iteration adds native Endpoint network and owner instance details; final run `20260926T115012184Z`
passes 6/6 scenarios, 798 steps and 67 checks. The audit disproves full completion despite the passing implemented flows.

## Configured policy browser

`gui-policy-browser-2026-09-26.md` records the implemented searchable configured-rule list, atomic server-validated key
selection, current-row highlight and narrow Chinese checks. Final run `20260926T120318945Z` passes 6/6 scenarios,
898 steps and 74 checks. Runtime activation remains open; source investigation and the hazards of assuming backend
readiness are recorded for the next iteration. The completion audit's rule-list row is updated without closing the goal.
