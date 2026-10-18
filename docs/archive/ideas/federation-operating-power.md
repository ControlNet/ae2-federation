# Optional Federation operating power — archived discussion

Archived: 2026-10-18, after the owner confirmed the idea was implemented.

See [implemented operating power](../../features/operating-power.md) for current behavior. The discussion below is
historical: its status line and the "Open design questions" reflect the stage before implementation. They were
settled as follows: the ME Federation Beam Converter takes FE through every face but its front; a domain pools the
buffers of the converters that match its largest cable section and pays every block's cost per tick
(`power.requireOperatingPower`, on by default, and `power.operatingCostMultiplier`); a domain that cannot pay stops
as one over capacity does, its cables turn red, and it restarts on its 32nd paid tick; the topology gauge, Jade and
the statistics page's Federation power card show supply and use. Still deferred: drawing AE from an ME network, and
moving power between domains through one.

Discussion date: 2026-10-09.
Status: user-proposed feature direction; the Beam Converter's name, shape, scope, input and outage behavior were settled on
2026-10-17, and the costs the same day. Implementation remains open.

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

## User decisions 2026-10-17: the Beam Converter

**Name (user, 2026-10-17).** **ME Federation Beam Converter** / **ME联邦光束转换器**, ID `ae2federation:beam_converter`;
the creative variant is **Creative ME Federation Beam Converter** / **创造ME联邦光束转换器**, `creative_beam_converter`.
It replaces the working names 光束充能器, 联邦光束供能器 and Beam Charger: AE2's Charger (充能器) charges items, and
"光电" would wrongly call FE electricity. Like a real fiber media converter, it turns one form into the beam.
The glow inside Federation cables stays "beam" (光束), not "laser", as the guide and the renderer already say.

The user settled its shape and role:

- **Shape.** A solid-cuboid multiblock of exactly M x N x 2·max(M, N), where M x N is the largest Federation cable
  cross-section in its domain. Exact, not a minimum: a domain that gains a larger bundle needs a rebuilt converter.
- **Sizes.** Deliberately not ordered by capacity: a 1x3 domain needs 1x3x6 (18 blocks) while a 2x2 domain needs
  2x2x4 (16 blocks), because finding two blocks of height may be no easier than a long run; the player chooses.

  | Largest cross-section | Converter | Blocks |
  |---|---|---|
  | 1x1 (no bundle) | 1x1x2 | 2 |
  | 1x2 | 1x2x4 | 8 |
  | 1x3 | 1x3x6 | 18 |
  | 2x2 | 2x2x4 | 16 |
  | 2x3 | 2x3x6 | 36 |
  | 3x3 | 3x3x6 | 54 |

  "Largest" means the highest-capacity cross-section (1x1 < 1x2 < 1x3 < 2x2 < 2x3 < 3x3), so a domain holding both a
  1x3 and a 2x2 bundle needs a 2x2 converter. If the largest cross-section changes, the old converter stops matching and
  the domain is unpowered until it is rebuilt (accepted).
- **Facing.** The converter is directional. Only its front face connects to Federation cable, and that face's cross-section
  must equal the domain's largest cable cross-section.
- **Scope.** One converter serves one whole Federation domain; another domain needs its own. Carrying beam energy
  between domains through an ME network was mentioned as a possible later method, with no current plan.
- **Several converters.** Like AE2 Energy Acceptors: energy can enter at several places in a domain and adds up.
- **Input.** FE. Drawing AE directly from an ME network was proposed and then deferred, because a network whose own
  power arrives only through the Federation could never restart it (the cold-start question below).
- **Insufficient energy.** As with capacity overload: the whole domain stops sharing, the world is not changed, and
  sharing resumes when power returns.
- **Default.** Required by default; the configuration can turn the requirement off, which means unlimited energy.
- **Creative variant.** A creative converter is wanted, following other mods' creative energy blocks.
- **Beam links.** A [line-of-sight beam](line-of-sight-beam-links.md) should cost more than the same number of cables.

Every one of several converters must match the size (user, 2026-10-17).

**Costs (user confirmed 2026-10-17).** Calibrated on the
[AE2 and addon energy survey](../../../.omo/knowledges/ae2-addon-energy-costs-2026-10-17.md) (1 AE = 2 FE; AE2's default
device 1 AE/t; cables 0; Quantum Network Bridge 22 AE/t per block). In FE/t, all scaled by one configuration multiplier
(default 1):

| Block | Cost | Source |
|---|---|---|
| Federation Cable | 0.5 per block | assistant choice: AE2 cable is free, but here the beam is the energy's look |
| Bundled cable | 0.5 per cell | assistant choice: the same per cell; a bundle saves capacity, not energy |
| Router, Switch | 2 per block | AE2's default device and crafting-CPU block (1 AE/t); an 8x8x8 body costs 1024 |
| Endpoint, Federation Pattern Provider | 2 each | AE2's Pattern Provider and Interface (1 AE/t) |
| Beam Former link | M·N × max(2, L)^1.05 × 2, L the span | NAE2's Beam Former, max(2, L)^1.05 AE/t, per cell; 1x1 x 32 is 76, 3x3 x 256 about 6100, 3x3 x 1024 about 26000 |
| Wireless cube | 44 per block per side | AE2's Quantum Network Bridge, 22 AE/t per block; 3x3x3 is 1188 per side, 5x5x5 5500 |
| Beam Converter | 0 | it is the supply |

A stopped domain keeps drawing its use each tick, as an unpowered AE2 network does, so the buffer stays empty and
the only way back is input above use.

**Buffer (user, 2026-10-17: follow AE2).** Converters keep a small buffer, as an AE2 Controller keeps 8000 AE per block:
proposed 16k FE per converter block, the domain's converters adding up. Input fills it and use drains it every tick. The
domain stops the tick the buffer cannot pay its use, and, as AE2's `EnergyService` does, runs again only after 30
ticks in a row with power. Larger storage stays with other mods' batteries in front of the converters.

**GUI (2026-10-17).** The user asked for the domain's power to show in the domain screen. On the
[design canvas](https://claude.ai/artifact/RjetUoACLghDXje7FNzjfd), row "联邦供能":

- **Topology: a corner gauge (user picked option B).** On the legend's dark background: "Power", a bar and a percentage,
  both the buffer's fill. Green normally, yellow while use is above input and the buffer falls, red when empty. When
  stopped, the canvas turns dark red as for capacity overload and the gauge reads a red 0 %. No cause text, no header
  text and no tooltips (user: unnecessary). Options A
  (a header segment) and C (a toolbar button) were not chosen.
- **Statistics: a seventh metric (user liked it).** "Federation power" in FE/t beside "ME power" (AE/t); its card turns
  yellow while draining and red when stopped. The chart stacks use by block type with the converters' input as a line,
  red where stopped. Below: the buffer's stored % on the left, as under ME power; on the right each converter (position and
  input, red at 0) and use by block type. No size or match text (user: unnecessary). It is domain-wide, so the network filter greys out.

## Relationship to existing features

The later [configurable gameplay rules](../../ideas/configurable-gameplay-rules.md) discussion confirms independent
power and capacity switches, both enabled by default. Energy cost settings apply only while operating power
is required. Pack authors can deliberately opt out; no expert-mode preset is required.

This proposal concerns energy consumed to operate Federation infrastructure. The existing capability to share
ME power between member networks is a related but different player function. How operating energy interacts
with that shared pool remains to be designed; this idea does not automatically replace or redefine power sharing.

The [remote connection idea](../../ideas/remote-federation-connections.md) supplies potential high-cost devices.
The [line-of-sight beam link](line-of-sight-beam-links.md) supplies an additional connection form.
Both should account for the optional power setting when their operating requirements are specified.

The later [multiblock and capacity brainstorm](../../ideas/multiblock-routing-and-cable-capacity.md) initially considered
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
