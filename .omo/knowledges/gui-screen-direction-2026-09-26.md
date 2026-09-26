# Proposed GUI screen direction

This records the original rough design direction, written before implementation. The user subsequently authorized
implementation and continued polishing. It follows the LDLib2/KilaGraph and NeoECO research recorded alongside this
file. See `gui-completion-audit-2026-09-26.md` for current implementation evidence and outstanding scope.

## Existing entry points

- Router and multipart Bridge open the shared Federation Domain policy workspace.
- Federation Pattern Provider opens its native AE2 provider menu and owns genuine native inventory/settings.
- EndpointBlock currently has no dedicated GUI interaction override. A direct endpoint inspection screen would be new.
- Cable does not need a management screen. Native terminals and crafting CPU screens remain AE2 workflows.

## Shared workspace

Use a common header, task navigation, search/list components, selected-object inspector and authoritative status footer.
Main tasks: Overview, Policies, Pattern Mapping, Diagnostics. Maintain the current selected object while navigating.
Use AE-compatible panel/slot controls, a dark node canvas inspired by KilaGraph, and NeoECO-inspired summary/detail
hierarchy. Use common status colors across graph/list/inspector, always accompanied by text where meaning matters.
Wide layouts can show list + content + inspector; compact layouts collapse secondary regions rather than essential text.

### Overview

Router opens the domain overview. Left: searchable networks/providers/endpoints. Center: real node/port/wire graph.
Right: selected-object summary and contextual actions. Initial fit once, stable pan/zoom afterward. Show actual network
membership and endpoint ownership as distinct layers. Policy overlay requires explicit policy projection; do not imply
the existing CAPABILITY claim edges already contain all policy permissions. No invented live traffic/progress.

### Policies

Searchable rule list and selected-rule editor. Direct consumer/provider selection with an explicit statement of who
uses whose capability. Show Storage/Crafting/Processing/ME Power as readable choices, configured enabled/disabled and
unconfigured states distinctly, and available effective-state reasons. Apply edits with server confirmation and keep
revision conflict feedback local to the editor. The initial scope is existing policy capability selection/enabling.
Operation/filter editing is an extension requiring additional UI requests and validation, not a cosmetic change.

### Pattern Mapping

Select provider from a searchable list, click a pattern row showing output icon/name/amount/slot, then select endpoint
targets. Distinguish mapped, available, retained and conflicting endpoints with text. Present pending changes and a
clear apply action if batch editing is implemented; otherwise explicitly acknowledge individual mutations. Batch apply
needs designed atomic/partial-failure semantics, so do not promise it as free UI behavior. Preserve retained ownership
on unmap. Release retained endpoints through a target-specific consequence dialog and existing authority checks.

### Diagnostics

Filterable endpoint/object table with readable status, owner and reason. Selected rows expose precise identifiers,
generations/revisions and available diagnostics, with links back to graph/mapping/policy. Only offer repair/navigation
actions supported by backend evidence. Distinguish an empty domain, missing provider and search with no matches.

## Entry-specific screens

- Bridge: same workspace, initially focus its attached network and show entrance attachment context. If domain access
  is unavailable, show a compact diagnostic state with specific known reasons; do not present a working empty editor.
- Provider: retain native AE2 inventory/settings as the default, add a Federation Mapping navigation entry if feasible
  without broad AE2 screen mutation. The dedicated mapping view is the shared mapping page scoped to that provider,
  with a clear return path. Keep inventory actions separate from graph selection. This is a new integration point.
- Endpoint: propose a new compact read-only inspection entry showing effective/configured mode, native network,
  claimed owner and available face/return diagnostics. Reuse the same inspector from the domain screen. Mode is
  constrained by actual lifecycle/claim state; do not introduce an arbitrary LOCAL/FEDERATED toggle. Cross-screen
  navigation requires a fresh authorized context, not client-side teleportation to any remote menu.

## Shared overlays and rollout

Create reusable searchable pickers, clear release confirmation, stale-context/conflict messages, and complete
loading/empty/error states. Selected-object names/positions and renderable recipe summaries need new structured
projection fields. UI draft selection is distinct from authoritative selection/configuration.

Delivery order: shared visual components and shell; Overview/graph; Pattern Mapping/direct selections; Policies and
Diagnostics; Provider shortcut and Endpoint direct entry. Keep phase boundaries reviewable and reuse panels across
entries instead of implementing independent copies of the same business controls.
