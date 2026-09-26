# Theme details and graph observation language

Previous goal turn made verified progress: shared AE-inspired theme plus actual-client visual evidence.
This follow-up addresses visible raw provider state codes and remaining default dark scrollbar chrome.

- All 10 ProviderTargetState values have English and Chinese translations. The graph inspector explicitly prefixes
  them with "Last target check" because projection reads `runtime.lastResolution()`, not a live authorization query.
  ACTIVE therefore describes the last authorized target, not perpetual readiness.
- Selector arrows use a copied LDLib icon tinted with the shared dark text color; global LDLib icons are not mutated.
- Scroller track and thumb use the shared palette and bevels. Runtime screenshot review verifies the former black
  rounded chrome is gone from the diagnostic columns.
- Actual-client English and Chinese graph checks exercise the real fixture's POLICY_DENIED observation. Chinese
  checks also verify wrapped text fits the compact inspector. Other provider states have translation coverage only,
  not dedicated runtime fixtures. A Python source/locale comparison confirmed all 10 enum values in both locales.

Evidence: `.omo/evidence/gui-theme-detail-states/attempt-20260926T133640857Z`.
BUILD SUCCESSFUL; 6/6 scenarios, 1216 steps, 112 checks, zero failed checks; 246 unit tests, zero failures/errors/skips.
Reviewed Chinese provider observation and narrow diagnostic screenshots. The provider screenshot includes the existing
identity tooltip over part of the graph, so it is evidence for readable inspector text, not an unobstructed overview.
An initial compile attempt failed due to an incorrect Icons package; corrected to `gui.texture.Icons` before this run.

```sh
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict \
  -PevidenceDir=.omo/evidence/gui-theme-detail-states \
  --dependency-verification=strict --no-configuration-cache --console=plain
```
Expected: BUILD SUCCESSFUL, all six scenarios/checks pass. Keep sources unchanged during the run.

## Next substantive audit item: runtime non-publication reasons

Read actual backend discovery and reconciliation sources while this suite ran:
- Crafting backend discovery fails separately for unconfirmed network identity and unavailable active provider/CPU.
  It currently throws CraftingBackendUnavailableException with messages; introduce typed reasons if exposing these,
  rather than parsing exception text. Reconciliation also rejects inactive policy and empty domain references.
- Energy discovery fails for unconfirmed identity and no native public extractable source. It excludes directional
  virtual sources. Its reconcile path also requires domain references and a consumer source.
- UI must read recorded observations, never call capability(), discover(), reconcileAll() or binding.isCurrent().
  Historical diagnostic observations need scope/revision handling and cleanup so an older reason cannot be presented
  as current readiness. Remaining requirement is still open; no runtime diagnostic implementation was added here.
