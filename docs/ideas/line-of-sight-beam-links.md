# Line-of-sight Federation beam links

Discussion date: 2026-10-09.
Status: user-proposed connection form; detailed behavior and implementation remain open.

## Intent

Connect two distant Federation locations along an unobstructed straight line without laying cable between them.
Players trade a clear beam path and increased operating energy consumption for reduced cable construction.

## User-proposed behavior

- Introduce a device described as **光束收发器** (Beam Transceiver). This is a working description, not a finalized
  localized name, registry ID, model, or recipe.
- Two endpoints provide straight-line, long-distance transmission for a Federation connection.
- No physical Federation cable is required along the span between them.
- The path between endpoints must be unobstructed. This is a condition for an operating connection, not merely
  a restriction checked when the pair is initially established.
- This connection consumes more energy than ordinary wired connectivity when the proposed operating-power
  requirement is enabled. Exact cost and its relationship to other wireless devices remain open.

## Relationship to other ideas

This is a concrete new connection form within the broader [remote connection discussion](remote-federation-connections.md),
recorded separately because it has its own placement rules and player interaction.
It does not replace the earlier quantum-bridge-style jump candidate or finalize that candidate's name or appearance.

The [optional operating-power idea](federation-operating-power.md) supplies the proposed energy framework.
The beam-link idea and the power idea are separate features; the optional power requirement must remain configurable.

Unlike a connection that can jump across arbitrary intervening terrain, this proposal makes the physical path
between endpoints relevant. It is not a player teleporter or a new resource-sharing policy.
Federation P2P's existing behavior is documented [separately](../features/federation-p2p.md).

## Open design questions

- How do players align, pair, and recognize endpoints? Is alignment restricted to block axes or can it be diagonal?
- What is the maximum span? Are intermediate relays supported?
- Which blocks obstruct the beam? Glass, fluids, partial blocks, and moving obstructions need explicit rules.
- How does the link respond to a new obstruction, and how does it recover when the path clears?
- What happens when an endpoint or a section of the path is unloaded? Automatic chunk loading is not implied.
- Is one endpoint limited to one partner? What domain/topology relationship does an established link create?
- Is power paid at one end or both, and is consumption affected by distance or activity?
- How are misalignment, obstruction, unloaded regions, and insufficient energy distinguished for the player?

Do not infer cross-dimensional capability from this straight-line concept, or automatically inherit the
same-domain cable-equivalence contract of Federation P2P. Those semantics have not been selected here.
Implementation algorithms, block form, range values, visuals, energy rates, recipes, and identifiers remain open.
