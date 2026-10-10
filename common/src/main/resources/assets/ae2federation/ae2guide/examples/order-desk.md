---
navigation:
  parent: examples/index.md
  title: A Factory That Takes Orders
  icon: ae2:molecular_assembler
  position: 27
---

# A Factory That Takes Orders

**Goal:** let another player's network order from your factory without seeing the factory's stock. One rule between
the two cannot do this: Crafting needs Storage in the same direction, so a "Customer uses Factory's" Crafting rule
always shows the customer the factory's storage too. Put a counter network between them, and the customer orders
through the counter.

**You need:** the customer's network, with a crafting terminal, its own crafting CPU and its own storage holding the
ingredients it pays with; a counter network with a <ItemLink id="ae2:drive" /> for the goods you show openly; your
factory, with a <ItemLink id="ae2:pattern_provider" /> next to a <ItemLink id="ae2:molecular_assembler" />, a drive
for your private stock and an energy cell; two <ItemLink id="ae2federation:bridge" />s.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/order_desk.snbt" />
  <BoxAnnotation color="#915dcd" min="5.375 0 0" max="8 2 1">
    Customer: crafting terminal, crafting CPU, and a drive with the ingredients it pays with
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="5 0.25 0.25" max="5.375 0.75 0.75">
    Bridge between the customer and the counter: a domain of these two networks
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="3.375 0 0" max="5 2 1">
    Counter: a drive with the goods the shop shows openly
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0.25 0.25" max="3.375 0.75 0.75">
    Bridge between the counter and the factory: a second domain, of these two networks
  </BoxAnnotation>
  <BoxAnnotation color="#5ccd78" min="0 0 0" max="3 2 1">
    Factory: a pattern provider next to a Molecular Assembler, a drive with the private stock, and the energy cell that powers all three networks
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

In the Federation screen of the customer's Bridge, with connected domains shown:

<FederationTopology>
  <Network key="customer" label="Customer" color="#915dcd" column="0" row="0" details="Crafting CPU, terminal|Drive" />
  <Network key="counter" label="Counter" color="#5CA7CD" column="1" row="0" details="Drive" />
  <Network key="factory" label="Factory" color="#5ccd78" column="2" row="0" details="Pattern provider|Private drive, energy cell" />
  <Domain key="front" label="This domain" networks="customer,counter" opened="true" />
  <Domain key="back" label="Domain 3C91" networks="counter,factory" />
  <Rule user="customer" source="counter" capability="crafting" />
  <Rule user="customer" source="counter" capability="storage" />
  <Rule user="counter" source="factory" capability="crafting" state="reexport" />
  <Rule user="counter" source="factory" capability="storage" />
  <Energy first="customer" second="counter" />
  <Energy first="counter" second="factory" />
</FederationTopology>

## Build it

1. **Join the networks** with two Bridges, as in the scene: one between the customer and the counter, one between
   the counter and the factory.
2. **Switch on ME power** for the customer and the counter in the first Bridge's screen, and for the counter and the
   factory in the second's. Power pools along the chain, so all three run on the factory's energy cell.
3. **Right-click the counter–factory Bridge** and switch on Crafting under "Counter uses Factory's", then step it on
   once more, to Enabled with re-export. Its Storage comes on with it; leave that at Enabled, without re-export.
4. **Right-click the customer–counter Bridge** and switch on Crafting under "Customer uses Counter's". Its Storage
   comes on with it.
5. **Order from the customer's terminal.** The factory's recipes are listed among the customer's craftables.

## What the customer sees

* **The counter's goods.** The customer's Crafting rule on the counter switches the same direction's Storage on, so
  the counter's drive is open to the customer: its terminal lists the goods, and it can take them out and store into
  the drive, as into its own storage. Keep there only what you are happy to hand over.
* **The factory's recipes**, passed on by the re-export on "Counter uses Factory's" Crafting.
* **Not the factory's stock.** That rule's Storage is only Enabled: the counter sees the factory's drive, but does
  not pass it on.

## How an order runs

The customer's crafting CPU runs the job, and the customer pays. The CPU takes the ingredients from what the customer
can see, its own storage and the counter's drive, and pushes them straight to the factory's pattern provider; the
results come straight back to the customer. The counter takes no part and needs no CPU, and nothing passes through
it. Keep nothing on the counter that the factory's recipes use, or the customer may pay with the shop's own goods.

Leftovers, such as byproducts and the results of cancelled jobs, land in the factory. They stay yours, out of the
customer's reach.

## Why two Bridges

Each Bridge makes a domain of the two networks it joins. The customer's Bridge holds only the customer and the
counter, and the factory shares a domain only with the counter, so no screen offers a "Customer uses Factory's" rule.
The customer's Bridge shows "Counter uses Factory's" only with connected domains, read-only, as in the diagram above;
it is set from the counter–factory Bridge.

## Try it

Step "Counter uses Factory's" Storage on once more, to Enabled with re-export. The factory's private stock appears in
the customer's terminal. Step it back to Enabled, and the stock disappears again, while the customer still orders the
factory's recipes.
