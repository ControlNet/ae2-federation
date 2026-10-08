# Remote Federation connections

Status: multiple forms under exploration; quantum-bridge-style jumping is the user's first candidate.

Discussion started: 2026-10-02. Research expanded: 2026-10-03. [Idea index](README.md).

## Intent and confirmed context

The user initially left the form open, then proposed a first direction: a quantum-bridge-like mechanism
that lets a point in the Federation connection jump to a remote location. The user explicitly clarified that
other forms remain open and requested a subagent survey of technology mods on CurseForge and Modrinth.
The first concept is a remote link
between Federation connection points, crossing the intervening distance without a continuous cable route.
Exact pairing, topology semantics and device requirements have not been selected.

Earlier discussion considered a wireless / cross-dimensional Federation connection under the temporary name
"Federation quantum bridge". This page collects that related context alongside the new exploration rather than
assuming a final device. The final name, appearance, visual theme and physical structure remain undecided.

[Federation P2P](../features/federation-p2p.md) is implemented separately, including cross-dimensional support.
It does not set a delivery date or first-release scope for this idea.
Whether this becomes one wireless bridge feature or a broader family of remote connections remains open.

## First candidate: a jump within the Federation connection

The connection at one Federation location continues at another remote location. Players can attach local
Federation infrastructure at each end. Quantum bridging is the user's functional reference, not a selection
of AE2's appearance, multiblock structure, crafting requirements or naming.

A useful interpretation to discuss is cable-like continuity across the jump: ordinary Federation connectivity
and policy would continue to determine access. This has not yet been separately confirmed as a same-domain
contract, unlike the explicit cable-equivalence decision for Federation P2P. One-to-one versus one-to-many links,
carrier ME network dependence, operating costs and first-release dimension scope remain open.

## Candidate player experiences

The quantum-bridge-style connection is the first candidate, not the final or exclusive direction. The following
additional forms are research-inspired suggestions; none has been selected by the user.

| Experience | How players would establish connections | Reference and gameplay distinction |
|---|---|---|
| Paired sites | Give two connection points matching link tokens | AE2 quantum bridging; an explicit connection between two sites |
| A stored destination | Record a remote endpoint on a card and install it locally | Refined Storage; the interaction is selecting a specific remote address |
| A group of sites | Join a named/identified group or copy its link credential | Integrated Dynamics Omni connectors; expand to many sites without pairing every edge |
| A hub and its outposts | Pair each outpost with a hub and manage its connections individually | ExtendedAE's multi-port Hub; explicit capacity and per-connection controls instead of an undifferentiated group |
| Directional links | Align connection points within a bounded distance | Integrated Dynamics Mono connectors; placement in the world helps determine connections |
| Coverage infrastructure | Define a service area and connect eligible local devices or clusters | Wireless Utilities has actual directional ME/RS network machines; Federation eligibility and enrollment behavior would need separate design |
| Relay stations | Build a route from several short wireless hops | Actually Additions, Botania Corporea and local LaserIO links; construction remains meaningful without continuous cable |
| Radio-style infrastructure | Place and improve stations whose reach depends on infrastructure or surroundings | Immersive Engineering radio towers, OpenComputers and XNet provide signal/range/progression references; Federation restrictions are not selected |
| Published services | Choose which services a station publishes to remote participants | XNet's selected channels; this would need separate discussion about how it relates to existing Federation policy |
| A remote endpoint proxy | Bind a local device to one remote endpoint | Entangled supplies a block-interface reference; this is an additional possible feature, not a redefinition of the first jump candidate |

Frequency selection, named-network management, card copying and visible color identifiers are also useful
interaction references from resource-transfer mods. Their shared inventories or energy pools must not silently
replace Federation's independent ME networks or policy-controlled capabilities.

The [expanded wireless technology-mod survey](../../.omo/knowledges/wireless-tech-mod-survey-2026-10-03.md)
covers 100 independently counted projects: 90 with relevant mechanism evidence and ten inconclusive screening
records. It includes the original eight projects once each, and distinguishes direct network links, shared resource
services, player access, local coverage and signal/data communication. See that record for official sources and
version/verification limits. Distance restrictions, operating costs and chunk loading are separate design choices;
they are not automatically inherited from any reference mod.

These forms could be alternatives or later coexist, but no progression or combined scope has been agreed.
Pairing or joining a group would not by itself determine which resources are shared; policy remains a separate
design concern. Whether a remote connection joins a single Federation Domain like cable or preserves separate
domains must be discussed before specification. Do not automatically inherit P2P's cable-equivalence contract.

## Interaction ideas from the expanded survey

These are proposals for future UX discussion, not additional confirmed requirements:

- Show which site/group a device is bound to, whether it is currently reachable, and whether policy permits the
  requested operation as separate states. A saved pairing should not be visually confused with a live connection.
- Offer destination highlighting, coverage previews or a connection inspection tool, following examples such as
  Modular Routers, Wireless Chargers, LaserIO and Wireless Redstone. Invisible connections need usable diagnostics.
- Let names, colors or item icons help players recognize links in-world as well as in configuration screens.
- Treat chunk availability separately from distance/dimension reach. Several surveyed mods support cross-dimensional
  transfer while explicitly requiring players to keep endpoints loaded.
- Consider whether one device family needs multiple connection modes. Wireless Redstone demonstrates broadcast
  and exclusive pairing in the same mod; this does not yet select such a design for Federation.

No range hierarchy, early/late-game progression, endpoint limit, recipe or implementation mechanism is selected by
these examples. The expanded research broadens the candidates without deciding the final name or appearance.

## Further possibilities raised by the 100-project survey

The following dimensions remain proposals, in addition to the connection forms above:

- **Network identity and status:** CESG's local/partner storage views suggest making a remote site's identity
  visible when the design preserves independent networks. Unloaded, disconnected and incorrectly configured
  endpoints can be explained differently.
- **Ownership and membership:** Quick Link and AE Wireless Transceiver provide team-scoped connection examples;
  the former also uses claim ownership. Consider how players recognize the owner/group and understand changes
  in membership, without choosing a particular team/claim integration yet.
- **Moving sites:** CC: Wireless Peripheral Sable and Create: Ender Link provide concrete references for connections
  involving moving devices. Whether Federation ever connects mobile infrastructure is an additional scope question,
  not a requirement of the first remote bridge.
- **Operating model:** Usage-based fuel, reserved capacity and continuous operating costs create different player
  experiences. The surveyed examples do not select any of these for Federation.
- **Triggered remote operations:** IC2 Classic's batch teleportation, Ender Mail's addressed deliveries and Digital
  Items' redeemable transfers suggest separate transport concepts beyond persistent network connectivity. They
  should be discussed as additional ideas if useful, rather than silently changing the user's first jump candidate.

The [research record](../../.omo/knowledges/wireless-tech-mod-survey-2026-10-03.md) contains sources, exact version
boundaries and distinctions between fixed links, mobile clients, control messages and actual resource delivery.

## Open questions for future discussion

- Does the jump provide cable-equivalent same-domain connectivity, and how many remote ends may it connect?
- How does a player select and recognize the remote destination or group?
- What distinguishes this feature from using Federation P2P, including whether a carrier ME network is involved?
- What are the intended range and dimension rules? Cross-server connectivity has not been requested.
- What are the operating requirements and unloaded-endpoint behavior? Automatic chunk loading is not implied.
- Does a later concept need a separate page, name or visual design?

No answers are needed merely to retain this idea. Concrete implementation remains deferred to future coding work.

## Related research

[P2P feasibility and early wireless discussion](../../.omo/knowledges/federation-p2p-feasibility-2026-10-02.md)
contains prior technical possibilities. Those proposals are research, not approved requirements for this page.

The subsequent [100-project P2P comparison](../../.omo/knowledges/federation-p2p-mod-survey-2026-10-03.md)
and [100-project policy comparison](../../.omo/knowledges/custom-policy-mod-survey-2026-10-03.md) investigate
transport boundaries, connection diagnostics, filtering and rule authoring separately. Their findings can inform
later remote-connection discussion without selecting this idea's final form or inheriting P2P's confirmed scope.
