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

The interface faces farmland with wheat on it. Install it with the event `crop_grew` at that interface.

<FederationProgram src="../assets/programs/harvest_wheat.ae2f" />

## Share power while there is plenty

Turns the power rule from the base network to the farm on while the base holds more than 100,000 AE, and off
otherwise.

<FederationProgram src="../assets/programs/share_power.ae2f" />
