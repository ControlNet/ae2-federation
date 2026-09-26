# Generic-resource pattern presentation

## Problem and implementation

The workspace previously projected an output item registry ID and `ItemStack.getCount()`. Reconstructing an item from
that ID discarded components and could not correctly describe wrapped AE resources or output amounts above int range.

Pattern choices now encode decoded production inputs and outputs with AE2 `GenericStack.CODEC` and registry-aware
JSON operations. The client decodes these resources using its registry access, caches decoded objects per snapshot,
uses AE key display names, and preserves ItemStack components with `AEItemKey.toStack()` for item icons. Other AE
resources use `GenericStack.wrapInItemStack`, allowing AE2's wrapped-resource renderer to draw fluid icons.

Rows show the primary output, AE full amount formatting, an additional-output count, input amounts and mapping count.
Tooltips list every decoded output and each input's first possible resource with its multiplier applied. Search in
the pattern list includes secondary output names. Inputs with alternatives still display their first possible resource;
this is a summary, not a complete substitution editor. AE full amount formatting supplies fluid units; it should not
be interpreted as proof of exact decimal formatting for every third-party resource type or extreme fluid amount.

NeoECO's output-icon/name/quantity summary and separate detail presentation informed this change. The implementation
uses the installed AE2 API rather than reconstructing resources from visible labels or copying fixture data into UI.

## Verification

The actual-client mapping scenario installs two genuine encoded processing patterns as test fixtures: a custom-named
diamond output of 4,000,000,000 with 1,500 units of water as a secondary output, and a primary water output of 2,500.
These fixtures are not production data or hardcoded production presentation. Assertions check the custom name and
long amount in rendered tooltips, secondary-output search, and the resource identity/amount stored in the fluid icon.

Final evidence: `.omo/evidence/gui-generic-patterns/attempt-20260926T111558750Z`.

- `BUILD SUCCESSFUL`; 6/6 scenarios, 650 steps, 44/44 checks.
- 241 unit tests, zero failures/errors/skips; unchanged unit tests reused their up-to-date result in the final run.
- Screenshot: `ldlib2/screenshots/ui.mapping/209_ui-generic-patterns.png`.
- English and Chinese JSON parse, and `git diff --check` passes.

The first run found two obsolete test assumptions: an ungrouped amount string and an icon at child index zero of a
Button (whose first child is its own text). The next run passed client checks but exposed the same old amount string
in the outer evidence verifier. Both expectation sites now require the visible grouped amount, and the icon check
locates the actual ItemStackTexture. Failed evidence remains available; only the final run is full acceptance evidence.

```sh
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict \
  -PevidenceDir=.omo/evidence/gui-generic-patterns \
  --dependency-verification=strict --no-configuration-cache --console=plain
```

Expected: all six scenarios and all checks pass, with `BUILD SUCCESSFUL`. Keep source unchanged during execution.
The broader GUI completion audit, explicit in-flight request/rejection feedback and smaller window layouts remain open.
