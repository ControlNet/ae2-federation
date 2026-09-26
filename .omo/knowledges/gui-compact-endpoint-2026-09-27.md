# Compact Endpoint entry

Direct Endpoint inspection now starts at a maximum logical size of 440x280, reduced from the shared 640x400.
Both dimensions clamp to the screen minus eight logical units. The existing narrow layout and independent detail
scroll areas remain. Router, Bridge and Provider entries retain their shared workspace dimensions.

`FederationDomainPolicyMenu` registers an Endpoint-specific PlayerUIMenuType resource ID using the same holder and
session logic. Both sides know the presentation size before ModularUI.init computes centering; no asynchronous
resize or menu-authority change is required. Device opening chooses the ID from the actual server block entity type.
Navigation and synchronization nodes remain the same. The Endpoint entry is not a separate business implementation.

Actual-client evidence: `.omo/evidence/gui-compact-endpoint/attempt-20260926T141955132Z`.
BUILD SUCCESSFUL; 6/6 scenarios, 1296 steps, 131/131 checks; 248 unit tests with zero failures/errors/skips.
Added checks for compact dimensions, screen centering and control/footer containment. Chinese 320x240 logical
viewport, freshly placed Endpoint and isolated no-domain reopening also pass. Viewed `ui.endpoint/152_ui-endpoint-direct.png`:
both columns display the owned device's actual details without scrolling at the default wide-screen size, and the
footer fits. The full-scope completion audit remains separate.

```sh
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict \
  -PevidenceDir=.omo/evidence/gui-compact-endpoint \
  --dependency-verification=strict --no-configuration-cache --console=plain
```

Expected: BUILD SUCCESSFUL and all six scenarios/checks pass. Do not edit source while the wrapper runs.
