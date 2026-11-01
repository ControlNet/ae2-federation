---
navigation:
  parent: automation/index.md
  title: Example Programs
  icon: ae2federation:automation_interface
  position: 60
---

# Example Programs

Each program below runs as it is. Paste it into a new program, push it, and install it with the arguments it asks
for.

## Keep an item stocked

Crafts what is missing up to a target. What running jobs will still make counts as stocked, so a job already under way
is not ordered again. Install it with the trigger **Every** 100 ticks, or the event `stock_changed` for the item.

<FederationProgram src="../assets/programs/keep_stocked.ae2f" />

## Feed a furnace

An <ItemLink id="ae2federation:automation_interface" /> faces a furnace. The program puts raw iron in from above and
takes the ingots out below.

<FederationProgram src="../assets/programs/smelt_iron.ae2f" />

## Harvest wheat

The interface faces the wheat itself, level with it, with the farmland one block lower. Install it with the event
`crop_grew` at that interface.

<FederationProgram src="../assets/programs/harvest_wheat.ae2f" />

## Share power while there is plenty

Turns the power rule from the base network to the farm on while the base holds more than 100,000 AE, and off
otherwise.

<FederationProgram src="../assets/programs/share_power.ae2f" />

## Write a missing pattern

Writes a pattern for the item on a blank pattern from storage and puts it in a pattern provider that faces a machine
running one of the item's recipes: a Molecular Assembler for a crafting recipe, a furnace for a smelting one. An item a
pattern already makes is left alone, so the program writes each pattern once. Storage needs blank patterns. Install it with the item and the trigger **Every** 200 ticks.

<FederationProgram src="../assets/programs/learn_pattern.ae2f" />

## Light a lamp while every CPU is busy

The interface lights a lamp in front of it while no crafting CPU of the domain is free. Install it with the trigger
**Every** 20 ticks.

<FederationProgram src="../assets/programs/all_cpus_busy.ae2f" />

## Trash a surplus

A cable in front of the interface carries an ME Export Bus on top, facing a trash can above it. The program fills the
bus's first filter slot with the item while storage holds more than `keep`, and empties the slot otherwise. Install it
with the item and the event `stock_changed` for the item.

<FederationProgram src="../assets/programs/trash_surplus.ae2f" />

## Connect new P2P tunnels

The tunnel facing the interface is the input. Each run links every unlinked tunnel of the same kind on its network as
one of its outputs, so a tunnel placed later joins it without a memory card. Tunnels of other kinds and tunnels already
linked stay as they are. Install it with the trigger **Every** 100 ticks.

<FederationProgram src="../assets/programs/link_new_tunnels.ae2f" />

## Channel alarm

Lights the lamp while devices anywhere in the domain wait for a channel. The count it returns shows in the log.
Install it with the trigger **Every** 100 ticks.

<FederationProgram src="../assets/programs/channel_alarm.ae2f" />
