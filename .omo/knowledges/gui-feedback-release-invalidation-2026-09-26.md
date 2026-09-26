# Mapping feedback and prepared-release invalidation

## Reference and implementation

NeoECO's `ComputationTaskCards` separates localized state text from state accents and exposes secondary details in
tooltips. Federation now follows that presentation principle for authoritative mapping results: success, waiting,
rejection and neutral states have readable English/Chinese messages and matching color classes. Raw result codes
remain available in the status tooltip. Unknown or malformed results never receive a success presentation.

`MappingFeedback` is a pure Java presentation adapter. Keep Minecraft `Component` construction in the session;
the isolated unit-test runtime does not include Minecraft classes. Four added tests cover accepted slot identity,
malformed results, distinct release/stale-cleanup results, and specific refusal reasons.

Prepared releases are revalidated while the server builds workspace choices. The session discards a preparation
when its policy authority, provider retention, or complete controller confirmation changes. The client also clears
its saved preparation and requested sequence when authority disappears. A stale confirmation cannot be treated as
a fresh user decision. Existing server-side release authorization remains in place.

The actual-client mapping scenario now opens a confirmation, modifies the mapping through the real Provider API,
checks that the obsolete dialog disappears while the new mapping survives, removes that mapping, and verifies the
old dialog does not reopen. A fresh preparation and explicit confirmation then release the retained endpoint.
Retention is established by real native processing dispatch, not by setting a test-only retained flag.

Graph object label lookup now uses an ID-indexed descriptor map instead of repeated linear scans. This removes one
quadratic search path; it is not a large-domain performance qualification.

## Evidence

- Feedback snapshot: `.omo/evidence/gui-feedback/attempt-20260926T110018341Z`, six scenarios, 595 steps, 40 checks.
- Final invalidation snapshot: `.omo/evidence/gui-release-invalidation/attempt-20260926T110602419Z`, six scenarios,
  605 steps, 42/42 checks, and `BUILD SUCCESSFUL`.
- Unit tests: 241 tests, zero failures/errors/skips.
- `git diff --check` passes.
- The feedback snapshot's `ui.graph-controls/154_ui-graph-controls.png` provides a clean graph overview without
  an obstructing tooltip. Earlier hovered graph captures should not be interpreted as proof of a runtime cursor bug.

Reproduce:

```sh
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict \
  -PevidenceDir=.omo/evidence/gui-release-invalidation \
  --dependency-verification=strict --no-configuration-cache --console=plain
```

Expected: `BUILD SUCCESSFUL`, all six scenarios and all assertions passing, zero off-render-thread font accesses.
Do not edit source while the wrapper is running; it verifies source/product identity.

## Remaining audit

The broad polish goal remains open. In particular, pattern projection currently reconstructs an icon from its item
registry ID, which does not preserve component-rich stacks or represent all AE generic resources accurately.
Further work also needs to assess request-in-flight feedback with explicit rejection acknowledgment, smaller window
layouts, and the complete original screen-direction checklist. Passing this regression does not certify those areas.
