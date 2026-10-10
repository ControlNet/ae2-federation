---
navigation:
  parent: index.md
  title: Nexus Core
  icon: ae2federation:nexus_core
  position: 100
categories:
- misc ingredients blocks
item_ids:
- ae2federation:nexus_core
- ae2federation:nexus_processor_press
- ae2federation:nexus_processor
- ae2federation:printed_nexus_circuit
---

# Nexus Core

<ItemImage id="ae2federation:nexus_core" scale="4" />

The ingredient in every Federation device. It is made the way AE2 makes its processors and cores: print a circuit,
press it into a processor, then craft the processor into a batch of cores. The circuit needs a press of its own first.

## Inscriber Nexus Press

In an <ItemLink id="ae2:inscriber" />: an <ItemLink id="ae2:engineering_processor_press" /> in the top slot, a
<ItemLink id="ae2:logic_processor_press" /> in the bottom slot and an Ender Pearl in the middle. All three are used
up and make one <ItemLink id="ae2federation:nexus_processor_press" />. The two presses also work the other way round.

<Recipe id="ae2federation:nexus_processor_press" />

Like AE2's presses, it copies itself: the Nexus Press in the top slot and a Block of Iron in the middle make another
one, and the press stays.

<Recipe id="ae2federation:nexus_processor_press_from_iron" />

## Printed Nexus Circuit

In the Inscriber: the Nexus Press in the top slot and an Ender Pearl in the middle. The pearl becomes a
<ItemLink id="ae2federation:printed_nexus_circuit" />; the press stays.

<RecipeFor id="ae2federation:printed_nexus_circuit" />

## Nexus Processor

In the Inscriber: the printed circuit in the top slot, Redstone Dust in the middle and a
<ItemLink id="ae2:printed_silicon" /> in the bottom slot, as for AE2's own processors. All three are used up. The
circuit and the silicon also work the other way round.

<RecipeFor id="ae2federation:nexus_processor" />

Both steps can be automated like AE2's own processors: an Inscriber fed from one side puts each input in its slot by
itself.

## Nexus Core

A <ItemLink id="ae2:fluix_crystal" />, an <ItemLink id="ae2:ender_dust" /> and a
<ItemLink id="ae2federation:nexus_processor" /> in a row of a crafting table make two cores, like AE2's
Formation Core. Another mod's Ender Pearl dust works as well.

<RecipeFor id="ae2federation:nexus_core" />

Used in: <ItemLink id="ae2federation:bridge" />, <ItemLink id="ae2federation:cable" />,
<ItemLink id="ae2federation:router" />, <ItemLink id="ae2federation:switch" />, <ItemLink id="ae2federation:pattern_provider" /> and
<ItemLink id="ae2federation:processing_endpoint" />.
