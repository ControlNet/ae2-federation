---
navigation:
  parent: examples/index.md
  title: A Factory That Takes Orders
  icon: ae2:molecular_assembler
  position: 27
---

# A Factory That Takes Orders

**Goal:** let another player's network order from your factory without seeing the factory's stock. A Crafting rule
shares only the factory's recipes: the customer pays with its own items, and the factory's drive stays private.

**You need:** the customer's network, with a crafting terminal, its own crafting CPU and a drive holding the
ingredients it pays with; your factory, with a <ItemLink id="ae2:pattern_provider" /> next to a
<ItemLink id="ae2:molecular_assembler" />, a drive for your private stock and an energy cell; a
<ItemLink id="ae2federation:bridge" /> where the two touch.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/order_desk.snbt" />
  <BoxAnnotation color="#915dcd" min="3.375 0 0" max="6 2 1">
    Customer: crafting terminal, crafting CPU, and a drive with the ingredients it pays with
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0.25 0.25" max="3.375 0.75 0.75">
    Bridge: on the customer's cable, its outer side touching the factory's cable
  </BoxAnnotation>
  <BoxAnnotation color="#5ccd78" min="0 0 0" max="3 2 1">
    Factory: a pattern provider next to a Molecular Assembler, a drive with the private stock, and the energy cell that powers both networks
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

The Federation screen then shows the two networks and the one rule between them:

<FederationTopology>
  <Network key="customer" label="Customer" color="#915dcd" column="0" row="0" details="Crafting CPU, terminal|Drive" />
  <Network key="factory" label="Factory" color="#5ccd78" column="1" row="0" details="Pattern provider|Private drive, energy cell" />
  <Rule user="customer" source="factory" capability="crafting" />
  <Energy first="customer" second="factory" />
</FederationTopology>

## Build it

1. **Place the Bridge** on the customer's cable, with its outer side touching the factory's cable.
2. **Switch on ME power** for the pair, so the customer runs on the factory's energy cell.
3. **Switch on Crafting under "Customer uses Factory's"**, and leave Storage off.
4. **Order from the customer's terminal.** The factory's recipes are listed among the customer's craftables.

## How an order runs

The customer's crafting CPU runs the job, and the customer pays: the CPU takes the ingredients from the customer's own
drive and pushes them straight to the factory's pattern provider, and the results come straight back to the customer.
The customer's terminal lists none of the factory's stock, and its CPU cannot use it.

Leftovers, such as byproducts and the results of cancelled jobs, land in the factory. They stay yours, out of the
customer's reach.

## Try it

Switch on Storage under "Customer uses Factory's". The factory's stock appears in the customer's terminal. Switch it
off again, and the stock disappears, while the factory's recipes stay listed and the customer can still order them.
