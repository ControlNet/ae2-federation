---
navigation:
  parent: examples/index.md
  title: A Singularity Mint
  icon: neoecoprototype:simplify_singularity_cell
  position: 585
---

# A Singularity Mint

**Goal:** make singularities on one network and spend them from others. Neo ECO Prototype's L1 Singularity Cell makes
120 singularities every 30 seconds out of nothing. It sits on a mint network whose only power source is an Energized
Superconductive Interface, and two district networks take its singularities and run on its power. This page appears
because Neo ECO Prototype is installed.

**You need:**

* on the mint: a formed L1 storage system, with its host
  (<ItemLink id="neoecoprototype:simplify_storage_controller" />), its drives
  (<ItemLink id="neoecoprototype:simplify_drive" />) and its interface; a
  <ItemLink id="neoecoprototype:simplify_singularity_cell" /> in one drive; a
  <ItemLink id="neoecoprototype:superconductive_interface" /> on the mint's cable. No energy cell besides the storage
  system's own.
* two district networks with a terminal each. No power of their own.
* a <ItemLink id="ae2federation:switch" />.

<GameScene zoom="3" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/singularity_mint.snbt" />
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="5 3 2">
    The mint: the smallest L1 storage system, its singularity cell in the middle drive
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="5 1 1" max="7 3 2">
    The mint's cable and its Energized Superconductive Interface: 4,000 AE/t, the only power source here
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="7 1 1" max="8 2 2">
    Switch: one face per network
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="8 1 1" max="9 2 2">
    District A: a terminal, running on the mint's power
  </BoxAnnotation>
  <BoxAnnotation color="#5ccd78" min="7 0 1" max="8 1 2">
    District B: a terminal, running on the mint's power
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

In the Federation screen, each district uses the mint's storage and shares its energy:

<FederationTopology>
  <Network key="a" label="District A" color="#915dcd" column="0" row="0" details="Terminal" />
  <Network key="m" label="Mint" color="#5CA7CD" column="1" row="0" details="L1 storage system|Singularity cell|Superconductive interface" />
  <Network key="b" label="District B" color="#5ccd78" column="2" row="0" details="Terminal" />
  <Rule user="a" source="m" capability="storage" />
  <Rule user="b" source="m" capability="storage" />
  <Energy first="a" second="m" />
  <Energy first="m" second="b" />
</FederationTopology>

## Build it

1. **Build the L1 storage system** as Neo ECO Prototype's guide shows for the
   [L1 storage system](neoecoprototype:neoecoprototype_intro/storage_system.md). The smallest is 5 blocks long, 3 high
   and 2 deep. Put the singularity cell into one of its drives: it starts counting when the drive first reads it, see
   the [L1 Singularity Cell](neoecoprototype:neoecoprototype_intro/singularity_cell.md).
2. **Put the superconductive interface on the mint's cable**, beside the storage system's interface. It generates
   4,000 AE/t with nothing to burn, see the
   [Energized Superconductive Interface](neoecoprototype:neoecoprototype_intro/superconductive_interface.md).
3. **Connect the mint and the two districts** to their own faces of the Switch.
4. **Switch on Storage under "District A uses Mint's"** and under "District B uses Mint's", and switch on **ME power**
   for both pairs: the districts then run on the superconductive interface.
5. **Check it.** After 30 seconds, both districts' terminals list the cell's first 120 singularities. They are the
   same 120: what district A takes, district B no longer sees. Every 30 seconds, 120 more appear on both.

The districts share one stock rather than each getting a cell of their own. The cell sits on the mint, so neither
district needs a storage system or power of its own to draw from it.

## Try it

Take the singularity cell out of its drive. The singularities leave both districts' terminals. Put it back: they
return.
