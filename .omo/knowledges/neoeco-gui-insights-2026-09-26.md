# NeoECO GUI follow-up insights

Source snapshot: DancingSnow0517/NeoECOAEExtension, commit
`c33f736a821683c6aa4715665ec7c3dadad7765f`. This follow-up reads source only; no NeoECO client was launched.

## Concrete findings and Federation applications

1. **Pattern identity should be visual.** `gui/widget/PatternItemSlot.java` subclasses LDLib2 `ItemSlot`, registers
   the `pattern-item-slot` element, and draws an encoded pattern's nonempty output ItemStack instead of its pattern
   icon. It falls back to normal item rendering otherwise. Federation can show output icons, names and slot identity
   in mapping rows. This is a presentation reference, not justification to enable inventory transfers in a diagnostic
   list. Multi-output and non-item AE resources need a broader representation than this specific implementation.

2. **Task summaries and detail are separate.** `gui/task/ComputationTaskCards.java` draws output icon, clipped name,
   compact amount, progress and a state accent. Tooltips contain CPU and crafting details. States RUNNING, QUEUED and
   WAITING_OUTPUT have distinct colors and localization keys. Federation should present mapped/unmapped/retained/
   conflict states with explicit text and a small accent; expose owner UUIDs and epochs in detail. Avoid inferring
   job progress merely from endpoint ownership or mode; telemetry must exist before adding progress bars.

3. **Formatting is a reusable component concern.** `gui/common/HostText.java` contains compact and full numeric
   formatting, BigInteger support, width-aware formatting and exact-value tooltips. `HostElements.java` supplies
   consistent labels/rows, no-shadow text and hover-roll titles. Federation should centralize resource quantities,
   direction labels, identity display and translated states. Keep exact quantities accessible; shrinking text to
   fit and permanently scrolling primary labels are not preferred defaults.

4. **Theme covers states and texture geometry.** `gui/theme/NETextures.java` centralizes bordered sprite textures for
   panels/buttons/slots and AE checkbox textures; `assets/neoecoae/lss/eco.lss` applies them to component classes and
   interaction states. Federation needs its own coherent shell, node/inspector palette and text roles. KilaGraph-style
   dark canvas can use the same status colors as NeoECO-inspired inspector/list components without mixing whole themes.

5. **Secondary controls do not occupy the main workspace permanently.** `StorageHostActionUI.java` composes opener
   controls with floating build/priority panels. `StoragePriorityUI.java` changes window visibility locally while
   priority actions use server callbacks. Federation should keep selection, search and primary actions visible;
   disclose low-frequency diagnostics and settings in a panel, and scope retained-release confirmation to its target.
   Preserve Federation's existing nonce/sequence/revision-checked request path rather than copying simpler callbacks.

6. **State lights reflect synchronized state.** `CraftingHostPanelUI.statusIndicator` binds a boolean S2C and changes
   the lamp classes when that value changes. Its toolbar tooltip also has synchronized content. Federation should
   distinguish pending, accepted and rejected operations visibly, and display confirmed values consistently across
   labels, toggles and tooltips. Presentation animation must not announce success before acknowledgment.

7. **Updates have identities and budgets.** `HostTaskListElement.java` uses task IDs to construct updates/removals/order/
   total fields and a sequence in CompoundTag payloads. Its current limits are 96 tasks and an estimated 24,000-byte
   task-data budget, not a certified total network packet limit. It draws only visible task cards under scissoring,
   allocates hitboxes for visible rows, and clamps scroll offset across updates. This is custom visible-row rendering,
   not use of LDLib2 VirtualScrollerView. Federation already has virtual lists: preserve them, introduce structured
   stable-ID rows, and update existing entries. Delta protocols need initial-state/resync/generation handling before
   adoption; do not assume this implementation establishes arbitrary packet-loss/reordering guarantees.

8. **Animation can explain numeric change.** `StorageHostAnimatedRatio.java` animates toward confirmed usage targets
   over 500 ms with a cubic Bezier easing, restarting from the current interpolated value when the target changes.
   Federation can use restrained visual transitions for suitable measured quantities and highlights. Discrete error,
   permission and claim state should remain prompt. Its negative ratio sentinel encoding is not a recommended new
   domain-state format for Federation.

9. **Synchronization topology constrains dynamic UI.** `StorageHostPanelUI.createLeftPanel` explicitly constructs
   both variants on both logical sides, then synchronizes visibility. `HostElements.syncedDisplay` is a bound boolean
   wrapper. When introducing tabs/conditional panels, keep synchronization registration deterministic; client-only
   presentation nodes can remain local where they do not participate in those bindings.

## Recommended order

- First: shared theme, output-identifiable pattern rows, explicit status text and detailed tooltips.
- Next: direct selection, selected-object inspector and secondary panels, retaining current authoritative actions.
- Then: measured incremental-rendering and synchronization improvements, plus restrained numeric animation where useful.

This changes the initial review's emphasis: NeoECO is a reference for information hierarchy, reusable domain widgets
and synchronized state presentation, in addition to textures. It is not a complete responsive-layout solution;
several panels have fixed dimensions. Combine its components with Federation's compact/wide layout design.

## Source navigation

All paths above are relative to `src/main/java/cn/dancingsnow/neoecoae/` except the stylesheet, which is under
`src/main/resources/`.

- https://github.com/DancingSnow0517/NeoECOAEExtension/tree/c33f736a821683c6aa4715665ec7c3dadad7765f/src/main/java/cn/dancingsnow/neoecoae/gui
- https://github.com/DancingSnow0517/NeoECOAEExtension/blob/c33f736a821683c6aa4715665ec7c3dadad7765f/src/main/resources/assets/neoecoae/lss/eco.lss
