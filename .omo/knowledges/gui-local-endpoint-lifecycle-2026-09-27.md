# Local Endpoint presentation and lifecycle

The NeoECO/Data Energistics inspired shared theme remains the visual baseline. Re-read NeoECO `eco.lss`
and Data Energistics `CraftingPlanGraphPalette`: consistent pixel control states, pale panels, dark text,
gray-blue canvas and blue selection. No reference mod client was launched or third-party texture copied.

Local Endpoint inspection now replaces the empty domain selector with its actual block position and a read-only
description when no unique domain is available. Domain actions remain disabled. Actual fixtures verify multiple
candidate domains, a freshly placed unconfirmed device, and reopening a powered isolated device with no domain.
Chinese 320x240 logical viewport checks verify the local position, explanation and footer text fit.

The first lifecycle run failed when opening before the first network tick: `endpoint.binding()` was null and
`currentEndpoints().indexOf(null)` on the immutable empty list threw. `forDevice` now skips lookup until a binding
exists. This is a production fix exercised through an actual menu open, without spoofing identity data.
The footer describes identity at opening time because menu scope is captured then; live inspector data may already
show a subsequently confirmed network. Reopening resolves fresh scope.

Final evidence: `.omo/evidence/gui-local-endpoint-lifecycle-fixed/attempt-20260926T141434664Z`.
BUILD SUCCESSFUL, 6/6 scenarios, 1294 steps, 129/129 checks. Actual Minecraft client with synthetic input.
Viewed graph search and unconfirmed Endpoint screenshots from this run, and the Chinese narrow local Endpoint
screenshot from the preceding failed lifecycle run (that Chinese scenario passed). Wide Endpoint retains the shared
workspace dimensions and has considerable whitespace; dedicated compact sizing remains a visual audit item.

```sh
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict \
  -PevidenceDir=.omo/evidence/gui-local-endpoint-lifecycle-fixed \
  --dependency-verification=strict --no-configuration-cache --console=plain
```

Expected: BUILD SUCCESSFUL, all six scenarios and all checks pass. Keep source unchanged during execution.
