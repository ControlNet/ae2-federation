# Optional Federation operating power

Discussion date: 2026-10-09.
Status: user-proposed feature direction; the Beam Charger's shape, scope, input and outage behavior were settled on
2026-10-17. Costs and implementation remain open.

## Intent

Give Federation infrastructure an operating energy requirement that fits its beam-like cable appearance.
Different Federation blocks can have different energy costs, making remote wireless connectivity a meaningful
operating expense. This is a separate idea from any particular remote-link device.

## User-proposed behavior

- An energy-input device sustains Federation operation. The user's working name is **光束充能器** (Beam Charger);
  both the name and the final device form remain tentative.
- The operating-power requirement is optional through a configuration file. Players or pack authors can disable
  this requirement explicitly; it is enabled by default as part of the full intended gameplay.
- When enabled, supplied energy is required to sustain Federation operation.
- Different Federation block types can consume different amounts of energy.
- Wireless connections should be particularly energy-intensive compared with ordinary wired infrastructure.
- The separately proposed [line-of-sight beam link](line-of-sight-beam-links.md) also has increased operating cost.

No numerical costs, conversion rates, or per-device configuration schema have been selected.

## User decisions 2026-10-17: the Beam Charger

The user named the device **联邦光束供能器** (Federation Beam Charger; working name) and settled its shape and role:

- **Shape.** A solid-cuboid multiblock of exactly M x N x 2·max(M, N), where M x N is the largest Federation cable
  cross-section in its domain. Exact, not a minimum: a domain that gains a larger bundle needs a rebuilt charger.
- **Sizes.** Deliberately not ordered by capacity: a 1x3 domain needs 1x3x6 (18 blocks) while a 2x2 domain needs
  2x2x4 (16 blocks), because finding two blocks of height may be no easier than a long run; the player chooses.

  | Largest cross-section | Charger | Blocks |
  |---|---|---|
  | 1x1 (no bundle) | 1x1x2 | 2 |
  | 1x2 | 1x2x4 | 8 |
  | 1x3 | 1x3x6 | 18 |
  | 2x2 | 2x2x4 | 16 |
  | 2x3 | 2x3x6 | 36 |
  | 3x3 | 3x3x6 | 54 |

  The cross-sections are totally ordered by capacity and by area (1x1 < 1x2 < 1x3 < 2x2 < 2x3 < 3x3), so a domain
  holding both a 1x3 and a 2x2 bundle needs a 2x2 charger (assistant reading, to confirm).
- **Facing.** The charger is directional. Only its front face connects to Federation cable, and that face's cross-section
  must equal the domain's largest cable cross-section.
- **Scope.** One charger serves one whole Federation domain; another domain needs its own. Carrying beam energy
  between domains through an ME network was mentioned as a possible later method, with no current plan.
- **Input.** FE, and it may also connect directly to an ME network and draw AE from it.
- **Insufficient energy.** As with capacity overload: the whole domain stops sharing, the world is not changed, and
  sharing resumes when power returns.
- **Default.** Required by default; the configuration can turn the requirement off, which means unlimited energy.
- **Creative variant.** A creative charger is wanted, following other mods' creative energy blocks.
- **Beam links.** A [line-of-sight beam](line-of-sight-beam-links.md) should cost more than the same number of cables.

Open: whether several chargers on one domain add up and whether each must match the size; what happens while the
largest cross-section changes; the costs per block type; and what the ME-network input does when that network's
own power arrives only through the Federation (the cold-start question below).

## Relationship to existing features

The later [configurable gameplay rules](configurable-gameplay-rules.md) discussion confirms independent
power and capacity switches, both enabled by default. Energy cost settings apply only while operating power
is required. Pack authors can deliberately opt out; no expert-mode preset is required.

This proposal concerns energy consumed to operate Federation infrastructure. The existing capability to share
ME power between member networks is a related but different player function. How operating energy interacts
with that shared pool remains to be designed; this idea does not automatically replace or redefine power sharing.

The [remote connection idea](remote-federation-connections.md) supplies potential high-cost devices.
The [line-of-sight beam link](line-of-sight-beam-links.md) supplies an additional connection form.
Both should account for the optional power setting when their operating requirements are specified.

The later [multiblock and capacity brainstorm](multiblock-routing-and-cable-capacity.md) initially considered
charger-originated allocations. The user then set that role aside: do not currently design the charger as
an allocation or pathfinding origin. Capacity counts connected ME networks and endpoints independently of Policy.

## Open design questions

- Which energy systems can the input device accept: FE, AE energy, or another selected interface?
- How does operating energy reach consumers, and what area or connected domain does one charger serve?
- Can several chargers supply the same connected infrastructure, and is energy buffered?
- Are costs paid continuously, while a link is active, per operation, or through a combination of these?
- Does distance affect remote-link cost? What distinguishes beam links from other wireless-link costs?
- What stops and what remains visible when energy is insufficient? How are pending operations handled?
- If operating power uses a member ME network, how does that network restart Federation infrastructure after
  an outage without depending on power that can only arrive through the inactive Federation?
- What are the numerical default energy costs, and which costs are configurable individually?
- How do players inspect consumption, available supply, and the cause of an outage?

These questions are for future design, not additional approved mechanics. No recipe, item ID, new energy API,
power-distribution architecture, or implementation schedule has been selected.
