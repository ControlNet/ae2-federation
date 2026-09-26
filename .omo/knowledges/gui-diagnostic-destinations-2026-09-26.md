# Diagnostic mapping and policy destinations

## Implemented behavior

The diagnostic inspector now offers Owner mappings and Processing policy below its scrollable detail columns.
The toolbar's earlier Locate action remains available. Endpoint choices project whether the current complete owner
identity matches a loaded mapping controller in the authorized domain. Policy availability additionally requires two
distinct confirmed network members. Unavailable actions are disabled with a localized explanation, not pointed at an
arbitrary Provider/network. Request-progress state also disables these server-dependent navigation actions.

A navigation request uses SELECT_TARGET with endpoint_mapping or endpoint_policy, the scoped endpoint ID and a fresh
UUID correlation suffix. Existing container/nonce/sequence/domain/revision authority checks remain in place. The
session re-resolves the endpoint, current owner/controller and network identities at handling time.

Mapping navigation changes Provider, endpoint and the first currently mapped slot together; a retained endpoint with
no mapped slot uses slot zero while retaining the actual endpoint selection. Policy navigation changes consumer,
provider and PROCESSING together. It supports an unconfigured related rule without creating or enabling that rule.
The consumer is the owning Provider's native network, and the provider is the endpoint's native network.

Only successful resolution publishes the exact navigation receipt. The client switches pages after observing the
matching receipt in the same server snapshot as the confirmed selections. A fresh UUID prevents a previous successful
navigation snapshot from satisfying a later request. Rejected requests clear pending navigation through the existing
reply/progress integration. This receipt is presentation correlation, not authorization.

## Verification

Final evidence: `.omo/evidence/gui-diagnostic-destinations/attempt-20260926T131158798Z`.
BUILD SUCCESSFUL; 6/6 actual-client scenarios, 1168 steps, 105/105 checks. Unit suite: 244 tests, no failures/errors/skips.

The endpoint scenario navigates from an owned endpoint to its actual mapped slot and target, then to the actual
PROCESSING consumer/provider pair. It verifies policy revision, existing mapping and claim epoch are unchanged.
The real second unclaimed endpoint has both navigation actions disabled. Existing identity switching and exact graph
centering tests still pass. Chinese narrow-layout checks cover both new buttons and retain independently scrollable
facts. The suite exercises the loaded-owner and unclaimed cases; there is no dedicated packet-race test that changes
owner between click and handler. The server implementation performs live resolution rather than trusting availability
flags received from the client.

Reviewed captures:
- `ui.endpoint/43_ui-diagnostic-owner-mapping.png`
- `ui.endpoint/52_ui-diagnostic-processing-policy.png`
- `ui.chinese-scales/157_ui-chinese-narrow-diagnostics.png`

```sh
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict \
  -PevidenceDir=.omo/evidence/gui-diagnostic-destinations \
  --dependency-verification=strict --no-configuration-cache --console=plain
```

Expected: BUILD SUCCESSFUL and all six scenarios/checks pass. Keep sources unchanged during the wrapper.

The original graph/mapping/policy contextual destinations now have implementation and actual-client evidence. Remaining
scope is device no-domain/ambiguous-domain presentation, more specific runtime non-publication diagnostics, and the
final audit of the complete original direction. The goal remains active.
