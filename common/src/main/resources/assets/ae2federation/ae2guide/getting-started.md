---
navigation:
  parent: index.md
  title: Getting Started
  icon: ae2federation:federation_logic_processor
  position: 10
---

# Getting Started

You need two separate ME networks that each have power. Federation does not replace AE2's own cables or controllers;
it only connects networks that already work on their own.

## 1. Make Federation Logic Processors

Every Federation device needs a <ItemLink id="ae2federation:federation_logic_processor" />. Press it in an
<ItemLink id="ae2:inscriber" /> from a <ItemLink id="ae2:logic_processor" /> and a piece of
<ItemLink id="ae2:fluix_dust" />. Both are used up.

<RecipeFor id="ae2federation:federation_logic_processor" />

## 2. Connect the two networks

Choose one of these. Both give you the same Federation screen.

### Two networks side by side: a Bridge

<RecipeFor id="ae2federation:bridge" />

Place the <ItemLink id="ae2federation:bridge" /> on a cable of the first network, so that its outer side touches a
cable or device of the second network. The two networks stay separate; the Bridge only connects them for Federation.

### Networks further apart: Routers and Federation Cable

<Row>
  <RecipeFor id="ae2federation:cable" />
  <RecipeFor id="ae2federation:router" />
</Row>

Touch one face of a <ItemLink id="ae2federation:router" /> with an ME cable of the first network and another face
with an ME cable of the second network. One Router can take up to six networks, one per face. For networks far
apart, give each its own Router and join the Routers with <ItemLink id="ae2federation:cable" />. Do not place two
Routers face to face; always put Federation Cable between them.

## 3. Switch sharing on

Right-click the Router or the Bridge (stay within eight blocks) to open the Federation screen. It draws the networks
in this Federation domain as cards. Select a network card to list its rules with each other network, each with a
switch. A rule reads "network A uses network B's storage": it works in that direction only, and the other direction
has its own switch.

* Left-click a switch to step it forward (Disabled, Enabled, Enabled with re-export) and right-click to step back.
* **Storage** lets one network see, insert and extract the other's items and fluids.
* **Crafting** lets one network use the other's pattern providers. It switches the same direction's Storage on too.
* **ME power** joins both networks' energy into one pool; it has a single switch for the pair.

## 4. Check it works

Open an ME terminal on the network that uses the other's storage: the other network's items are listed there and can
be taken out. A rule's state shows beside its switch: green when active, yellow while it is not active yet, red when
something blocks it, with the reason underneath. See [Troubleshooting](troubleshooting.md).

Next: [How Federation Works](mechanics.md) and [Remote Processing](remote-processing.md).
