# GUI v2 phase 1: shell, topology and pair-editor switches (2026-09-28)

Design canvas: https://claude.ai/artifact/RjetUoACLghDXje7FNzjfd (see `gui-v2-redesign-research-2026-09-27.md`).

## What changed

- Rail tabs are icon-only (`FederationIcons`) and there are three pages: topology (`page_overview`), processing
  (`page_mapping`) and diagnostics. The old Policy page and its selectors (`consumer_next`, `policy_toggle`,
  `rule_value`, rule browser) are gone; `FederationGraphPresenter` and `FederationPolicyBrowser` were deleted.
- `FederationTopologyView` draws one card per member network (`graph_node_<memberId>`, class `graph-node-member`),
  pills for pairs with rules, and an aside: network detail (`network_detail`) or the pair editor (`pair_editor`).
  A two-member domain opens the pair editor directly.
- Pair-editor ids: `policy_section_<s>`, `policy_section_title_<s>`, `policy_row_<s>_<cap>`,
  `policy_switch_<s>_<cap>` (class `on` when enabled), `policy_state_<s>_<cap>`. Section 0 is "lower-index member uses
  the other"; members are sorted like `PolicyEditorSelection.initial`, so `policy_switch_0_storage` is the fixtures'
  default Policy key.
- Switches send `SET_POLICY` with a `PolicySwitchTarget` (`consumer/provider/CAP/1|0/observedRevision`). The server
  does per-rule compare-and-set; a mismatch sets the non-fatal `CONFLICT` status ("Rule changed elsewhere ...").

## Pitfalls found while implementing

- **Dedicated-server crash:** menus are built on both sides. An `IGuiTexture` *lambda* links against client-only
  `GuiGraphics` when created and crashes the dedicated server (`BootstrapMethodError ... invalid dist`). Build all
  custom textures with `FederationTheme.painted(...)` (a real class drawing through a `Pen`). A contract test in
  `TaskThirtyThreeUiContractTest` rejects raw texture lambdas in `client/menu`. Only the Task 34 multi-client run
  (real dedicated server) catches this; single-player UI tests do not.
- An unpublished domain sends choices without `networks`. The topology keeps its last view so the server can still
  reject a click with `STALE_CONTEXT`; otherwise the editor empties while the status still reads "Ready".
- `ScrollerView.scrollToChild` only accepts direct scroll-view children. To reveal a nested row, set
  `verticalScroller.setValue(top / (viewContainer.height - viewPort.contentHeight))` (normalized 0..1).
- LDLib2 uitest selectors must match exactly one element; use `context.all(...)` for multi-match checks.
- In a row flex container a label with `adaptive-width: false` needs `flex: 1` or it measures narrower than its
  text (`#entrance_value` failed `wrappedTextFits` in the Chinese Bridge diagnostic).
- At 320x240 with the native 9px font, the mapping actions only fit stacked; compact mode also hides the two
  target-column headings so the feedback text does not overlap the footer.
- Task 34 stale-edit semantics under CAS: Client B keeps the switch it rendered before Client A's edit and presses
  that element after the revision-one receipt. This is a real concurrent edit through the production RPC.

## Verification

```sh
./gradlew --no-daemon --dependency-verification=strict :neoforge-1.21.1:test --console=plain
./gradlew :neoforge-1.21.1:federationUiTest -Pcases=ui.router,ui.bridge,ui.shared-policy,ui.stale-context,ui.close-unsubscribe \
  -PevidenceDir=.omo/evidence/gui-v2-phase1-t15 --dependency-verification=strict --no-configuration-cache --console=plain
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict \
  -PevidenceDir=.omo/evidence/gui-v2-phase1-t33 --dependency-verification=strict --no-configuration-cache --console=plain
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=multiclient.shared-server,multiclient.conflicting-edit,multiclient.domain-split,multiclient.disconnect-cleanup \
  -PevidenceDir=.omo/evidence/gui-v2-phase1-t34 --dependency-verification=strict --no-configuration-cache --console=plain
```

Expected: every command ends in BUILD SUCCESSFUL; client logs show `scenarios : 5/5 passed` and `6/6 passed`.
Do not edit sources while a UI run is in progress. Evidence from this run: `gui-v2-phase1-t15/attempt-20260927T142318082Z`,
`gui-v2-phase1-t33/attempt-20260927T142019121Z`, `gui-v2-phase1-t34/attempt-20260927T142408861Z`.
