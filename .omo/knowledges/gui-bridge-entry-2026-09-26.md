# Bridge entry focus and unavailable explanations

## Implemented behavior

A Bridge session captures the confirmed NetworkId of its real mainGrid from BridgeRightClickContext. It publishes
initialGraphFocus only if the current scoped domain still contains that network. The value is the same MemberId
used by the graph projection, not an arbitrary first member or a coordinate guess.

The graph waits for nodes and viewport dimensions, fits the initial view, selects the supplied member and centers
it at the fitted scale. Initial selection is applied once. Explicit user selection consumes the pending initial
focus, so later snapshots preserve the user's selection and zoom. Initial focus is client presentation only and
does not mutate policy selection or world authority.

Bridge entrance diagnostics now return localized Components, covering missing main/outer attachment, same grid,
unsupported outer attachment, removed Bridge and the absence of a unique domain with at least two networks. The
unsupported reason is phrased generally because MultipartBridgePart also uses that reason for non-air neighbors
that fail native attachment resolution; it is not proof that a Federation cable block is present.

When there is no selected network pair, policy_direction displays one complete unavailable sentence instead of
interpolating three copies of the no-selection placeholder. Existing disabled edit controls and context checks remain.

## Verified evidence

Final: `.omo/evidence/gui-bridge-entry/attempt-20260926T124102713Z`.
BUILD SUCCESSFUL; 6/6 client scenarios, 967 steps, 83/83 checks. Unit suite: 244 tests, no failures/errors/skips.

The multipart scenario opens the actual Bridge without manually clicking Fit, compares the inspector's full member
identity against the real mainGrid-derived identity, and verifies user selection/zoom survive subsequent server ticks.
It removes the actual outer attachment block, waits for production MISSING_OUTER_ATTACHMENT, reopens the menu, then
checks the readable reason, disabled policy action, full unavailable sentence and footer text fit. This is a real
world topology transition, not a fabricated UI status.

Reviewed captures:
- `ui.multipart-attachments/27_ui-multipart-attachments.png`: real host member selected and centered, inspector populated.
- `ui.multipart-attachments/51_ui-bridge-disconnected.png`: unavailable explanation and disabled editor.

```sh
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict \
  -PevidenceDir=.omo/evidence/gui-bridge-entry \
  --dependency-verification=strict --no-configuration-cache --console=plain
```

Expected: BUILD SUCCESSFUL and all six scenarios/checks pass. Keep sources unchanged during the wrapper.

## Important Java/LDLib2 interaction

Wires extends UIElement, which already has select(String), returning a stream of matching descendants. An unqualified
select(initialFocus) inside Wires resolves to that inherited method rather than the outer presenter's selection
method. javap confirmed the wrong invocation in the failed attempts. The correct call is
FederationGraphPresenter.this.select(initialFocus). Otherwise the canvas repeatedly centers but the inspector never
selects a node and the one-time flag never flips.

An intermediate hypothesis that screenTick was not running was incorrect. Moving initialization into rendering did
not fix the inherited-method collision. That experiment was reverted; the final code retains screenTick and qualifies
the outer call. The final client evidence proves this path executes. Test waits are based on actual selection rather
than a small fixed frame count.

## Remaining scope

Bridge initial focus and the actual outer-disconnection workflow are now exercised. Other Bridge reason translations
are implemented but not each separately exercised in the client. Device no-domain/ambiguous-domain behavior, diagnostic
overview/contextual navigation, richer policy runtime non-publication reasons and final scope audit remain open.
