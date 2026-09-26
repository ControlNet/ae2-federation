# Diagnostic endpoint observation table

## Implemented workspace behavior

The diagnostic toolbar now has an endpoint overview action alongside the existing selector. It opens a virtualized
observation table and retains the two-column detail inspector behind it. This preserves useful inspector width on a
320x240 logical viewport. Selecting a row submits the existing server-authorized endpoint selection using its full
scoped ID and returns to details. No new mutation or alternate authorization path is introduced.

The server adds observations to each domain-scoped endpoint choice: position, configured/runtime modes, native node
readiness (isActive && hasGridBooted), endpoint identity, current owner UUID/instance, and last claim result. Rows show
three aligned columns (position and ME-node state, configured -> runtime mode, owner) plus a full-width last-request
line. The selected row is highlighted. Full endpoint and owner identities, owner instance and unabridged explanations
are retained in tooltips.

Search matches coordinates, full endpoint/scoped IDs, full owner UUID, localized modes, node state and last claim
explanation. No matches and no observed endpoints have separate messages. The title includes the total observed
endpoint count. The close action says Back to details / 返回详情.

The native node state is not a promise that any operation is authorized. The reason line is explicitly Last claim
request, because a rejected competitor request can coexist with a valid current owner. Runtime mode and configuration
are displayed separately. The table does not synthesize live transfer activity or invent current failure causes.

## Verification

Final evidence: `.omo/evidence/gui-endpoint-table/attempt-20260926T125129030Z`.
BUILD SUCCESSFUL; 6/6 client scenarios, 1066 steps, 93/93 checks. Unit suite: 244 tests, zero failures/errors/skips.

English checks cover empty results, actual claim-result search, coordinate search, genuine ready native node,
full endpoint tooltip identity, per-column/reason text fit and returning to the inspector through a row click.
Chinese checks exercise localized mode search at 320x240 and table/control bounds plus per-column/reason text fit.
The fixtures currently exercise a single observed endpoint; multi-endpoint comparison/selection remains useful final
audit coverage rather than something proved by this run.

Reviewed final actual-client screenshots:
- `ui.endpoint/114_ui-endpoint-observations.png`
- `ui.chinese-scales/172_ui-chinese-narrow-endpoint-table.png`

```sh
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict \
  -PevidenceDir=.omo/evidence/gui-endpoint-table \
  --dependency-verification=strict --no-configuration-cache --console=plain
```

Expected: BUILD SUCCESSFUL, all six scenarios and all checks pass. Keep source unchanged during the wrapper.

## LDLib2 layout finding

Button defaults to FlexDirection.ROW. A compound table row must explicitly use COLUMN to place the aligned column
strip over the reason line. The initial actual-client attempt passed its interaction tests but its screenshot exposed
missing position/mode content due to the wrong direction. The final patch corrects direction, fixes each column width,
and adds actual wrapped-text fit checks. Green interaction tests alone are not visual validation. Style.tooltips takes
Component varargs, so the accumulated list is converted to Component[].

## Remaining scope

Diagnostic-to-graph/mapping/policy contextual navigation is still missing. No-domain/ambiguous-domain device behavior,
more specific policy non-publication reasons and the complete final audit remain open. In particular, a direct device
without an authorized domain may still have local inspector data while the domain-scoped table is empty; that scope
must be presented explicitly during the no-domain follow-up.

Follow-up: `gui-endpoint-comparison-navigation-2026-09-26.md` supersedes the single-endpoint verification limitation with two real endpoints and adds exact diagnostic-to-graph navigation, including screen-space centering checks. Mapping/policy contextual navigation remains open.
