# GUI trim batch (2026-10-03)

Commits 7c4f7a2 .. the lang cleanup on dev. What changed, and what was learned on the way.

## Player-facing changes

- **Highlight buttons.** The highlight buttons are plain buttons that carry the `selected` class (AE2
  `button_highlighted.png`) while their blocks are outlined. A second press ends the outline early
  (`WorldHighlight.hide`). The map and 3D view blink in step by reading `WorldHighlight.brightness(dimension,
  groups)`. There is no "Outlining N blocks" note.
- **3D view.** No colour is added to blocks; `RenderUtils.renderBlockOverLay` is additive and washed blocks out.
  Instead, the surroundings are dimmed through the scene hook's colour multiplier, and the network's blocks get
  edge cuboids from each block's shape bounds. These use depth test on and `depthMask(false)`.
- **Endpoint panel.** It shows "Active patterns" (icon, name, Provider) instead of identity and epoch diagnostics.
- **Pattern rows.** The whole row drags its wire, except the item slot.
- **Rule editor.**
  - No storage operations row; `RuleTerms` was deleted.
  - No energy note.
  - No footer text after an accepted edit. ACCEPTED maps to the empty `domain.status.ready`, so the footer hides.
  - An unwritten rule reads "Off".

## Lessons

- **Synthetic drags skip row move events.** The LDLib2 test driver's `dragTo` updates hover before
  `screen.mouseMoved`, so `MOUSE_MOVE` targets the drop element. A drag threshold must be watched from a
  `ui.rootElement` capture listener, not from the pressed row.
- **Verifier probes can rot silently.** `federationUiVerifierSelfTest -PresultFile=<attempt>/result.json` is not
  part of `federationUiTest`. Two probes had become no-ops:
  - One replaced a footer suffix that no longer existed.
  - One mutated the first of two steps sharing a `caseId`, while the verifier reads the last.

  Probes should mutate with `findAll`. Run the self-test after changing what a scenario attaches.
- **Finding dead lang keys.** The generic helpers `tr(key)` prepend one of these:
  - `ae2federation.ui.topology.`
  - `ae2federation.ui.workspace.`
  - `ae2federation.ui.processing.`
  - `ae2federation.ui.provider.`
  - `ae2federation.ui.location.`

  So combine those prefixes with every string literal. Treat literals ending in `.` or `_` (other than these
  bare helpers) as dynamic prefixes. Then grep each candidate's suffix across main, testmod, test, gradle and XML.

  Keep these even though no code names them:
  - `block.*`, `item.*` and `mod.*` keys;
  - `ae2federation.domain_policy` and `ae2federation.pattern_provider`, which are the menu ids' language keys.
- **Evidence and the working tree.** The harness records `source-diff.patch`, so lang keys found in evidence may come only
  from that file. Do not edit the tree while a UI run is in progress.
