# Unavailable Bridge diagnostic panel

The original screen direction requires compact diagnostics for unavailable Bridge entry, rather than an empty editor.
The previous outer-disconnection screenshot still showed workspace tabs and an empty policy page. This is now replaced
by a 360x160 maximum logical window with the entrance context, localized recovery guidance and authoritative reason.
The four workspace pages and tabs are hidden for a Bridge session without a domain. Normal Bridge entry keeps the
full workspace and actual host-network initial focus. Existing session authority and request handling are unchanged.

The server chooses a registered diagnostic menu resource ID before client initialization, so centering uses the final
dimensions. The shared holder now accepts preferred width/height instead of an Endpoint-specific boolean. Endpoint
and normal workspace dimensions remain 440x280 and 640x400, clamped to the available screen.

Evidence: `.omo/evidence/gui-bridge-diagnostic/attempt-20260926T142558155Z`.
BUILD SUCCESSFUL; 6/6 scenarios, 1294 steps, 132/132 checks. Viewed the final real outer-disconnection screenshot
`ui.multipart-attachments/49_ui-bridge-disconnected.png`; the reason and guidance fit. The scenario verifies actual
attachment removal, hidden empty editors, compact dimensions and disabled policy mutation. This run does not add
a dedicated Chinese unavailable-Bridge screenshot or exercise every Bridge reason enum; retain those audit limits.

```sh
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict \
  -PevidenceDir=.omo/evidence/gui-bridge-diagnostic \
  --dependency-verification=strict --no-configuration-cache --console=plain
```

Expected: BUILD SUCCESSFUL and all six scenarios/checks pass. Keep source unchanged during the test.
