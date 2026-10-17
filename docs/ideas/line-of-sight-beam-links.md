# Line-of-sight Federation beam links

Discussion date: 2026-10-09.
Status: user-proposed connection form; form, linking, topology and obstruction settled on 2026-10-17. Range and
implementation remain open.

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

## User decisions 2026-10-17

The user settled the device's form and link semantics:

- **Form.** A **联邦光束收发器** (Federation Beam Transceiver; working name) is a solid M x N x 1 multiblock, M x N from
  1x1 to 3x3. Its back face takes a bundled cable of the same cross-section; a 1x1 transceiver takes ordinary cable.
- **Linking.** No pairing item or tool. Two transceivers of the same cross-section, aligned on one axis and facing each
  other, link automatically, and the link is drawn as a beam.
- **Topology.** The link counts as cable joined directly, following the Federation P2P precedent: both ends belong to
  one capacity region, and the span carries the capacity of its M x N cross-section (a 3x3 pair is unlimited).
- **Obstruction.** The whole M x N beam volume must be clear, not only a centre ray.
- **Loading.** Spans may cross chunks, and the link keeps working while chunks in between are unloaded.
- **Dimensions.** Same dimension only.
- **Energy.** Once operating power applies, a beam costs more than the same number of cables.
- **Reference.** [ME Beam Former](https://www.curseforge.com/projects/1351545) (two parts facing each other, up to
  32 blocks, auto-disconnect when blocked) is a useful functional reference. Its source confirms a real AE2 grid
  connection; the page's "client-only visuals" note refers to the beam's look.

The [range survey](../../.omo/knowledges/beam-link-range-survey-2026-10-17.md) found that every AE2 straight-beam
mod uses 32 blocks and none scales range with beam width. Assistant options, none selected: a fixed 32 for every
size; range growing with cross-section (for example 16·min(M, N)); or a configurable cap defaulting to 32. Most
surveyed mods let glass pass. For unloaded chunks in the path, keeping the last result and checking again on load
fits the user's rule that unloaded middle chunks do not break the link.

Still open: the range rule; which blocks obstruct the beam; the nearest-partner rule when several transceivers line
up; and relays.

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

The 2026-10-17 decisions above answer pairing, one-to-one partners, dimensions and the topology relationship.

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
