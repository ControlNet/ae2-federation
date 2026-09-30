# Dedicated Federation Pattern Provider GUI: decisions and implementation (2026-09-30)

## Problem

Right-clicking the Provider opens the domain workspace. Its "Edit patterns" button switches to AE2's Provider screen,
and that screen's "Federation pattern mapping" button switches back. The user found the two-mode switching awkward.
Neither screen fits the Provider:

- AE2's screen has no mapping.
- The workspace is built for a whole domain.

## Decisions (user, 2026-09-30)

1. **A new, dedicated Provider screen.** It is not AE2's screen and not the domain workspace, and right-click opens
   it directly.
2. **Scope.** It shows only this Provider's own mapping for editing, and has no topology graph.
   - Other Providers' mappings may be **shown read-only**, for example who holds an Endpoint's Claim.
   - They cannot be edited here.
3. **Visual style: the Federation theme.** It uses the same frame, colours and fixed GUI scale as the workspace
   (`FederationTheme`, `domain.lss`, `FederationGuiScale.Fixed`).
4. **Mapping form: wires** from pattern slots to Endpoints, like the workspace's processing mapping page. The Endpoints
   are those of the domain the Provider's Federation face joins.
5. **AE2 settings built into the screen, with no jump to AE2 sub-screens:**
   - blocking mode, lock crafting and pattern access terminal visibility as toggles;
   - priority as a number field.
6. **The workspace keeps its Provider mapping page editable.** Mappings can be edited in both places.

## Still to settle during design

- Pattern slots under the Federation theme need a bridge from AE2's `PatternProviderMenu`:
  - the slot filter (processing patterns only);
  - shift-click;
  - JEI/EMI drag;
  - settings sync.
- Proposal: a MenuType subclassing `PatternProviderMenu` on the server, with the Federation-themed screen on the
  client. Check how LDLib2 ModularUI can host vanilla/AE2 slots before choosing.
- Claim release and the release confirmation (`FederationReleaseDialog`) move into the new screen.

## Design boards (2026-09-30)

- The boards are in the existing canvas https://claude.ai/artifact/RjetUoACLghDXje7FNzjfd, in the row titled
  "ME联邦样板供应器 · 专用界面提案" (y 10120):
  - `ProviderGui` is interactive, with a `mode` prop.
  - `ProviderDrag`, `ProviderOther`, `ProviderRelease` and `ProviderNoFace` import it with a mode.
- The user asked for the design in that canvas, not a new one, and in the style of its v2 boards (`V2Topology` proc
  tab). So the boards copy the v2 tokens:
  - Noto Sans SC 500/700 and 2px borders, on 1330×800 boards;
  - Provider card 300 wide with 60px header and thumbnail;
  - Endpoint cards 280 wide with 18px round ports;
  - wires in the Provider network's accent, `#9CD3FF` when selected, and white dashed while pending;
  - facts box `#2F2A34`, and footer acks "已提交，等待服务端确认…".
- They also reuse v2's sample world: Provider 主基地 @118, 12, -28, and Endpoints ep1–ep6 in 自动化塔/矿场.
- **The Return Inventory row is dropped** (user agreed 2026-09-30).
  - AE2's Provider screen shows `logic.getReturnInv()` as 9 `STORAGE` slots (`PatternProviderMenu`,
    `screens/pattern_provider.json`, "物品返回栏").
  - On the Federation Provider that logic is `NativeProviderOwnerLogic`. It never executes, and the Provider registers
    no item or fluid capability, so those 9 slots are always empty.
  - Real returns land in each Lane's own return buffer (`provider.nativeLane(i).getReturnInv()`), which is filled
    through the Endpoint's return binding.
  - A non-empty buffer blocks release with `rejected-pending-return`.
- The design now shows the per-Lane buffers instead:
  - an "↩ n" badge on the Endpoint card;
  - a "回流缓冲" fact in the aside;
  - a "回流缓冲 · 按端点" list under the canvas;
  - release disabled with a reason while a buffer holds results (board `ProviderBlocked`).

## Implementation (2026-09-30)

- **Menu.** A `PlayerUIMenuType` id `ae2federation:pattern_provider`, registered in `FederationDomainPolicyMenu`:
  - `openProvider(player, pos)` builds `FederationDomainPolicySession.forDevice`, the same session as the
    workspace's device entrance, and opens `FederationProviderMenuHolder` with `ui/provider.xml` and
    `lss/provider.lss`.
  - `FederationPatternProviderBlock.useWithoutItem` calls it, sneaking or not. AE2's screen opens only if it fails.
  - The workspace's `#return_provider` ("Edit patterns") now opens this screen through `FederationMenuHolder.returnProvider()`.
  - `openDevice` still opens the workspace on the Provider, for tests and the AE2 screen's "Federation mapping" button.
- **Shared request authority.** `FederationMenuAuthority` holds:
  - the nonce, sequence and client copy;
  - the one request in flight;
  - the container/nonce/sequence/context checks.
  Both holders implement `FederationMenuHolder`, and `FederationDomainPolicyMenu`'s static dispatch, reply and
  receipt route through that interface. `TaskThirtyFiveHardeningContractTest` checks the checks live there.
- **Provider screen actions.** It accepts only `SELECT_TARGET` (`slot:`/`target:` only), `SET_MAPPING` and the three
  release actions.
  - It requires `session.editsOpenedProvider()`. `forDevice` pins `mappingProviderIdentity` to the opened Provider,
    so if that Provider leaves the domain, the session cannot fall back to another one.
- **Server data.** `session.providerChoices()` reads the 9 slots from the block entity, so they show even with no
  domain (`face=false`). It adds:
  - `target` choices via the extracted `targetChoice()`, with `ownerPatterns` for an Endpoint another Provider holds;
  - `lanes`, each Lane's return buffer (`returns`) and `pendingSend`, also copied onto the target as
    `returnKinds`/`pendingSend`;
  - `mappings`, the wires kept while off the domain.
  `providerStatusCode()` is `ready`, `noface` or `stale_context`.
- **Settings and priority** are LDLib2 two-way `DataBindingBuilder.intVal` bindings, not requests, so they work with
  no domain.
  - The server setter checks `session.isStillValid` and writes `getConfigManager()` (`LaneConfigManager`, which
    reaches every Lane) or `setPriority`.
  - The client shows the value in `remoteSetter` and sends its next choice in `remoteGetter`, with initial value -1
    (`Integer.MIN_VALUE` for priority), so nothing is sent before the first sync.
- **Real slots.**
  - `ItemSlot` + `ItemHandlerSlot(handler, i).setCanPlace(PatternDetailsHelper::isEncodedPattern)` and
    `<inventory-slots id="player_inventory"/>`.
  - The server binds `provider.getLogic().getPatternInv().toItemHandler()`. The client binds an `ItemStackHandler(9)`
    mirror, which vanilla slot sync fills.
  - Both sides add the slots in the same order in `createUI`, parked in `#pattern_slots` (display none).
  - `FederationProcessingGraph.setSlotElements` reparents each slot into its `#processing_pattern_<i>` row and gives
    every slot a row, with a port only when the slot holds a pattern.
  - **Gotcha:** LDLib2 sets `event.hasHandler = true` for every listener on the bubble path. A row or canvas
    MOUSE_DOWN listener above an `ItemSlot` therefore swallows the vanilla slot click unless it resets `hasHandler`
    to false (`passSlotClick`).
- **Provider-screen marks** (only when slot elements are set):
  - another Provider's Endpoint gets a dashed ring and a lock;
  - a "↩ n" badge shows results waiting in the return buffer;
  - the aside shows the facts "Its wires" (the owner's patterns) and "Returns";
  - release is disabled, with the reason, while the buffer holds results or a send is pending.
- **Tests.**
  - `ui.mapping` now checks the right-click screen:
    - 9 rows with real slots;
    - Blocking reaching the Lanes;
    - priority 7 → 0;
    - click to place and shift-click to take back;
    - Edit patterns → the Provider screen.
  - The verifier requires `providerScreen` evidence and the `ui-provider-screen`, `ui-provider-slot-filled` and
    `ui-provider-return` captures.

### Verification notes (2026-09-30)

- Synthetic UI-test shift-clicks do not quick-move: LDLib's `shiftClick` holds Shift only in its own `KeyState`, and
  vanilla `hasShiftDown()` reads GLFW. The scenario tests quick move on the server instead, with
  `menu.clicked(index, 0, ClickType.QUICK_MOVE, player)` (`TaskThirtyThreeWorldFixture.quickMove*`). The menu index
  comes from `menuSlot`: a player slot is one whose `container` is the player's inventory; pattern slots are the other
  slots, in order.
- An empty pattern row keeps its real slot but has no `processing_port_<i>` id or `processing-port` class.
- Slots use vanilla's sunken look (`.provider-shell item-slot { background: built-in(ui-mc:RECT_1); }`) over
  `modern.lss`'s dark default, as the design boards do.
- The header summary carries the pattern count, mapping count and Endpoint count, as in the `ProviderGui` board.
- Design renders are in `.omo/evidence/design/Provider*.png`. Port 8765 may already be taken by an older
  `http.server` serving another folder, which renders a blank 18 KB page, so use a free port.
- Verified: T33 (6 cases) green, 353 unit tests, and 152/152 fast GameTests (the 3 known SHARED batch-only failures).
- Pattern slots show the pattern's output, as AE2's Provider does (`RestrictedInputSlot.getDisplayStack` →
  `EncodedPatternItem.getOutput`): `FederationPatternSlot` overrides `ItemSlot.drawItemStack` only, so clicks,
  quick moves and the tooltip still use the pattern item. It overrides `name()` to return `item-slot`: a subclass
  without `@LDLRegister` would be named after its class and lose every `item-slot` style.
- A fluid output is a `WrappedGenericStack`, which has an empty model. AE2's `GuiGraphicsMixin` hook draws it at once
  with `AEKeyRendering`, while the slot background still waits in LDLib's batched buffer and LDLib turns the depth test
  off after drawing items, so the background covered the fluid. Flush with `guiContext.graphics.flush()` before
  drawing a wrapped stack.
- Screenshots with many Endpoints: at the end of `ui.mapping`, the showcase opens the host's Provider screen in
  the showcase domain (5 Endpoints: 2 in use, 2 owned by other Providers, 1 free) and captures
  `ui-showcase-provider`, `-own` and `-other`. The verifier requires `showcaseProviderClaims =
  free-1;in_use-2;occupied-2`. Endpoint cards carry a `claim-<code>` class for tests, because the state line's text
  changes ("Patterns mapped: N" for one in use).
- On the Provider screen, other Providers' wires are not drawn, so an occupied Endpoint used to take the "no owner
  wires" red ring. There it is now a grey dashed card whose state line names the owner and its patterns
  (`owner_line`). Selecting it shows the yellow `read-only-banner` notice (`readonly_notice`) instead of the red
  state fact, and the legend adds `legend_readonly`. The legend width is measured from the line it actually shows.
- The Endpoint card order is not stable between runs: accents and order follow random network and Endpoint ids.

### Pre-release review fixes (0.0.2, 2026-09-30)

- **Replaced Provider (duplication):** `DevicePolicyEntrance` now holds the block entity it was opened on.
  `present()` requires `level.getBlockEntity(pos) == entity && !entity.isRemoved()`, as AE2's `AEBaseMenu` does, and
  `providerEntity()` returns that object. AE2's `onRemove` drops the patterns but leaves the old inventory filled, so
  a stale screen on a Provider broken and replaced within one tick could take them again. GameTest:
  `providerscreenreplacedprovider` (in the manifest).
- **Two-way binding echo:** LDLib2 two-way `intVal` bindings resend a received value next tick, so two values in
  flight bounced (typing "12", double clicks). Settings and priority now use an `intValS2C` display plus a two-way
  string request `"<n>:<value>"`. The server applies each number once, and each side's getter returns its own latest
  request, so an echo settles. T33 checks that a double click and a two-digit priority settle.
- **Never use a C2S-only LDLib2 binding (`*C2S`) in a PlayerUI menu.** `UISyncManager.writeInitialData` writes every
  binding. The client's C2S-only value throws in `readSyncData` ("does not accept sync") before it consumes its bytes,
  so every later binding in the initial pack is misread (a null reached an `intValS2C` setter), and the Provider
  choices never arrived. Symptom in the client log: `Note: This is an unexpected behavior, may be attacks by`. The
  Task 33 UI run now fails when that line appears.
- **Emptied slots:** mappings belong to the slot, so after the pattern is taken out its wires stay. An empty row with
  mappings now gets a muted port, so the wires are drawn and can be unlinked. A PATTERN selection on an empty slot is
  dropped. The server `toggleEndpoint` refuses to add an Endpoint to an empty slot (`rejected-invalid-selection`);
  unmapping is still allowed.
- **Load:** the Provider payload is rebuilt at most every 5 ticks (`CHOICES_TICKS`) and at once after an accepted
  action. The canvas rebuild signature ignores `laneSent`/`laneReturned`.
- **Header:** with no face the header uses `summary_noface`; a changed domain uses `summary_stale` and hides the
  no-face notice (`stale` flag in `providerChoices`). Sync text is "Synced" / "已同步", as in the workspace. The
  Provider screen's legend has three lines. The menu title key `ae2federation.pattern_provider` was added. zh uses
  样板/供应器 consistently in sentences.
