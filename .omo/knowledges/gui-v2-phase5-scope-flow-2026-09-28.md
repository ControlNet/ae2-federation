# GUI v2 phase 5: related-domain scope and real flow indication (2026-09-28)

## Scope toggle (`#graph_scope`)

- "Scope: domain" shows only member networks. "Scope: related" also shows the networks of other domains that share
  a member network with this one.
- The server side is `FederationDomainPolicySession.addRelated`. It finds those domains through
  `registry.federationdomainsFor(member)` and caps them at 24 networks. It writes `relatedNetworks` (id, domain, name)
  and `relatedRules`, and each rule carries its domain.
- Foreign networks are `Network.foreign()`. Their card has class `related-network`, and their `member` is `related_<id>`.
  - They cannot be renamed.
  - A pair that includes one has "Read-only: this pair belongs to domain %s" in its title, and the row's `inDomain`
    flag disables its switches. The server never accepts edits for them from this menu.
- The button sits below the zoom row (`right: 3; top: 21`), not in the toolbar. A narrow Chinese layout at GUI scale
  4 has a canvas about 97 px wide, and the toolbar row overflowed and covered `#tab_overview`.
- After a toggle the graph refits two screen ticks later (`fitDelay`). Fitting in the same tick measures the old
  children, which left the graph empty.

## Flow indication, driven only by real deliveries

- `observability/meter/PairFlowWindow` is a ring of (tick, amount) records.
  - `summarize(now)` returns events, amount and ticks since the last event within the window.
  - `LevelObservabilityService.recordPairFlow(PolicyKey, amount)` keeps one window per key,
    with `PAIR_FLOW_WINDOW_TICKS = 100` (5 s).
- Recording sites. Nothing is inferred from rule state.
  - Storage: the projection lambda in `StorageMountService`.
  - ME power: `EnergyBindingService.extract` records nanoAE for the consumer and provider grid networks.
  - Processing: `ProviderObservationRegistry.recordSend` records accepted sends. The Endpoint network is resolved
    through `controller.laneEndpoint` and `EndpointTargetBinding`.
- The session's `pairFlowText()` is throttled to every 10 ticks and feeds the holder's `flows` BindableValue into
  `graphState.acceptFlows`.
- In the UI, an enabled rule's state gets "Delivered N× in the last 5 s", and `Links` draws teal pulse dots from
  provider to consumer while the rule is flowing.

## Pitfalls found

- A replace-all edit turned `rule(key)` into a self-call, and the StackOverflowError happened at screen open.
  - The client disconnected, and every later scenario failed with "No integrated server". Look for
    "Failed to handle advanced open screen" in the log first.
- `.hover(".network-link")` needs exactly one match. With a related network there are two, so filter by text.
- The Task 33 teardown (`cleanup`) now calls `removeRelatedDomain`. Otherwise an aborted scenario leaves the extra
  Bridge in place, and the following cases time out on "extended native identities remain settled".
- `revealRule` scrolls the aside, and the scroll persists. Scroll back (`revealInAside(ctx, "#pair_title")`) before
  any layout check such as `withinWorkspace`.
- Disabled policy switches rendered as black boxes (seen since phase 1 in `ui-stale-context-rejected`). The switch
  no longer uses `opacity`; see the verification evidence below.

## Tests

- `PairFlowWindowTest` is a unit test.
- `ui.graph-controls`:
  - Installs a real second Bridge, which gives a related domain, plus a STORAGE rule.
  - Toggles the scope and checks that the refit brings the related card into view, that the pair is read-only and
    that its switches are disabled.
  - Captures `ui-scope-related`, and writes an `evidenceFor` record with `scope=related-read-only`.
- `ui.mapping`:
  - After a real dispatch, checks that the server window has recorded the flow.
  - Waits for "Delivered 1× in the last 5 s" in the rule state.
  - Captures `ui-flow-processing`, and writes an `evidenceFor` record with `processingFlow`.

Evidence: `gui-v2-phase5-t33/attempt-20260927T170028259Z` (6/6), `gui-v2-phase5-t15/attempt-20260927T170441719Z` (5/5), Task 34 green.
A UI run fails with `NoSuchFileException .../build/classes/...` if sources are edited while it runs; that is a build race, not a test failure.

## Follow-up (2026-09-28)

- The flow dots are drawn by `FederationFlowPulses` (`#graph_flow_pulses`, public `drawnDots()`).
  - The layer order is `Links`, then pulses, then pills, then cards.
  - The dots move along `FlowPath.between(...)`, the part of a link between the card edges. That part is short in a
    two-card layout (about 40 px on each side of the pill).
  - Before this, the dots were drawn in `Links` from centre to centre, and cards and pills hid almost all of them.
- Related domains get a readable name from `RelatedDomainLabel.of(domainId)`:
  - `direct:` means one Bridge ("Bridge domain XXXX").
  - `physical:` means Routers joined by federation cable ("Router domain XXXX").
  - The tag is 4 hex digits of `String.hashCode`, which is the same on every JVM.
  - The session now sends the full domain id, and the client builds the name.
- `PairFlowWindow` changes:
  - Only one bucket is kept per tick, so the window holds at most `windowTicks` buckets.
  - There is no event cap any more; the old 256-event cap silently truncated bursts.
  - Sums saturate instead of overflowing, so `Math.addExact` can no longer throw on huge nanoAE amounts.
- The legend is folded by default behind `#graph_legend_toggle` ("?"). The choice is a static field and lasts for the
  client session.
  - The legend has `setAllowHitTest(false)`.
  - Unfolded on a narrow canvas, it used to swallow clicks meant for the cards under it (`selectFirstNetworkCard`
    failed in `ui.mapping`).
- `removeRelatedDomain` also deletes the related STORAGE rule through `PolicyService.delete`, and a check verifies
  that it is gone.
- LDLib2 `UIElement.setAllowHitTest(false)` makes an element transparent to clicks; there is no style property for it.
