# Topology "all related" scope layout (design, 2026-10-10)

Problem: `FederationTopologyView.layout()` puts every shown network on one ellipse. In the related scope that mixes
domains and draws rule links across the canvas, although rules only exist inside one domain and domains meet only
through shared networks.

## Decided: proposal A

The user picked proposal A. It changes only where the cards sit, plus a translucent plate behind each domain. Cards,
links, end marks, rule labels and their placement rules stay as they are.

- Domains in columns by hop distance from the opened domain. The client derives hops itself: two domains are
  adjacent when a network's `domains` holds both. The payload has no hop count. Within a column, a domain sits beside
  its neighbours in the previous column and is pushed down when it would overlap.
- A shared network sits at the centroid of its domains' centres.
- A domain's own networks lie on its small ellipse, in the widest gap between the directions of its shared networks.
  They follow the rule chain from the shared network at one end of that gap to the one at the other end, so links
  follow the arc instead of crossing it.
- A crowded domain spreads its own ellipse, as `TopologySpacing` spreads the whole canvas today. Anything between
  domains widens the gaps between columns and rows.
- Plates are the convex hull of a domain's cards, grown by 22 with round corners. The opened domain's plate is pale
  and labelled "本域" / "This domain", matching the existing "this domain" wording; the user kept the current
  localization as is, so no "Local domain" and no rename to "当前域". A related domain's plate is light blue and labelled "域 XXXX" only. The plate does not say read
  only, because its cards and labels already show that with dashes and a padlock.
- With one domain shown, this reduces to today's single ellipse.

Rejected: B (a fan of rings, too tall for the GUI), C (one lane per domain with aliases, too wide), and D (related
domains collapsed into chips; adds an interaction).

Design boards: the "AE2 Federation GUI" canvas, row "建议 A · 只改布局和域底板". The generator scripts that drew them
lived in a session scratchpad and were not kept.

## Implementation

- `client/policy/DomainClusterLayout` is a pure function with JUnit tests. It takes the cards in shown order, each
  with its shown domains (the opened one as `""`), the linked pairs, and a `LabelExtent` callback (the view passes
  `labelExtent`). It returns corners and domain centres. `problems(...)` lists overlaps; `plate`/`plates` return the
  rounded hull; `rows` turns a plate into one-pixel `[y, left, right]` fill rows.
- A member's domains come from its `domains` field (the shown related domains it is in) plus `""`. The payload has no
  id for the opened domain: `ConnectedDomains.reach` leaves it out.
- A domain that no shown network leads to (the 64-network cap can hide the joint) gets a column after the farthest.
- The view switches to the cluster layout only when `showRelated` is on and there are related networks; otherwise
  it keeps the old ellipse and `TopologySpacing.factor`. The Endpoint loop (spread ×1.15) and `labelSpots` run in
  both cases.
- Plates are filled with `GuiGraphics.fill` rows in `Links.drawBackgroundAdditional`, before the links. The name is
  drawn above the top-left corner. Cards are placed with a margin of 8 + 22 + 12 so plates and names stay at positive
  coordinates; `extent()` includes the plate outlines.
- Lang: `ae2federation.ui.topology.domain_label` ("Domain %s" / "域 %s") replaced the three kind keys;
  `plate_opened` is "This domain" / "本域". `RelatedDomainLabel.kind` stays for the via line.
- Nested domains: a domain whose shown networks all belong to another shown domain (typically a Bridge's direct
  domain between two networks of the opened domain) takes no column. `hosts(...)` maps it to the smallest domain
  holding it that is not nested itself; the opened domain is never nested, and equal related domains both nest in
  the same host. `place` positions cards as if nested domains were their hosts. The nested plate uses
  `NESTED_PADDING` (10), drawn after the host's, so its name lands in the strip between the host's top edge and the
  cards; names on the same spot go side by side. Found in the UI scenario: the north Bridge domain held only the
  opened domain's two networks, got its own column, and pulled the chain into a long diagonal.
- Camera: switching to the related scope (and any later refit, such as a window resize) fits the opened domain's
  plate, its name and this domain's Endpoints (`focusBounds`); the canvas Fit button still fits every domain.
- Related rules carry `runtime` in the payload (`runtimeObservation`, the same global per-key lookup as this domain's
  rules). Without it every related label read "not active yet" yellow, and the energy chip never turned quartz,
  because `sharesEnergy` reads the same runtime.
- A test lesson: the rule-chain ordering only showed in a test that checks links of one domain do not cross. A check
  for links passing over cards passed without it.

## Domain ids and the "路由域 / 桥接域" label

- `FederationDomainId` takes one of two forms:
  - `direct:<bridge source>`: one Bridge part, which joins its main and outer networks.
  - `physical:<n>`: Switches, Routers and Providers joined by Federation Cable. `n` comes from
    `FederationDomainRegistry.physicalSequence`. It is never reused, and a domain keeps it while it grows, shrinks or
    absorbs another; on a split, one part inherits it.
- The client never shows the raw id. `RelatedDomainLabel.of` picks a kind from the prefix (`direct` gives "桥接域",
  `physical` gives "路由域") and a 4-hex tag from `String.hashCode` folded to 16 bits. Two domains can share a tag
  (1 in 65536).
- User decision (2026-10-10): players never see a domain's kind. Every domain reads "域 XXXX" ("Domain XXXX"), with
  no "路由域" or "桥接域". The design boards already do this. In code, the `domain_label.*` lang strings collapse to
  one, and `RelatedDomainLabel`'s kind no longer reaches the UI. Done in the implementation.
