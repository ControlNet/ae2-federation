---
navigation:
  parent: index.md
  title: Getting Started
  icon: ae2federation:nexus_core
  position: 100
---

# Getting Started

This page connects two ME networks and lets one of them use the other's storage. Only one of the two needs an energy
source: once they share ME power, the other runs on it.

## 1. Make Nexus Cores

Every Federation device is made with a <ItemLink id="ae2federation:nexus_core" />. In an
<ItemLink id="ae2:inscriber" />, press an <ItemLink id="ae2:engineering_processor_press" /> and a
<ItemLink id="ae2:logic_processor_press" /> around an Ender Pearl into a
<ItemLink id="ae2federation:nexus_processor_press" />. That press prints Ender Pearls into circuits, and a circuit
pressed with Redstone Dust and <ItemLink id="ae2:printed_silicon" /> becomes a
<ItemLink id="ae2federation:nexus_processor" />.

<Recipe id="ae2federation:nexus_processor_press" />

<RecipeFor id="ae2federation:printed_nexus_circuit" />

<RecipeFor id="ae2federation:nexus_processor" />

A <ItemLink id="ae2:fluix_crystal" />, an <ItemLink id="ae2:ender_dust" /> and the processor in a row make two
cores.

<RecipeFor id="ae2federation:nexus_core" />

## 2. Connect the two networks

Networks that touch need only a Bridge. Networks further apart are joined by Switches and Federation Cable, which run
on FE from a Beam Converter. Both ways open the same Federation screen.

### Side by side: a Bridge

<RecipeFor id="ae2federation:bridge" />

Place the <ItemLink id="ae2federation:bridge" /> on a cable of the first network, with its outer side touching a
cable or device of the second. The two stay separate networks, joined for Federation through the Bridge. A Bridge
uses no FE.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="assets/bridge.snbt" />
  <BoxAnnotation color="#915dcd" min="3.375 0 0" max="6 2 1">
    Network A: its energy cell powers both networks
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="3 1 1">
    Network B: a drive and no power of its own
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0.25 0.25" max="3.375 0.75 0.75">
    Bridge: on network A's cable, its outer side touching network B's cable
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

### Further apart: Switches, Federation Cable and a Beam Converter

<Row>
  <RecipeFor id="ae2federation:switch" />
  <RecipeFor id="ae2federation:cable" />
  <RecipeFor id="ae2federation:beam_converter" />
</Row>

Give each network a <ItemLink id="ae2federation:switch" /> that one of its ME cables touches, and join the Switches
with <ItemLink id="ae2federation:cable" />. A Switch has a face for each network, up to six, so networks next to one
Switch can share it.

Switches and Federation Cable use a little FE every tick. Stand two
<ItemLink id="ae2federation:beam_converter" /> blocks in a line with one end on the cable, and pipe FE into them from
another mod's generator; AE2 itself makes no FE. Until the converter has FE, the networks stay unconnected. See
[Power](mechanics/power.md).

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="assets/switch_cable.snbt" />
  <BoxAnnotation color="#915dcd" min="6 0 0" max="8 2 1">
    Network A: its energy cell powers both networks
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="5 0 0" max="6 1 1">
    Switch: one face on network A's cable, another on the Federation Cable
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0.3 0.3" max="5 0.7 0.7">
    Federation Cable between the two Switches
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4 1 0" max="5 3 1">
    Beam Converter, two blocks: its lower end on the cable, FE in through any other face
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 0 0" max="3 1 1">
    Switch: one face on network B's cable, another on the Federation Cable
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="2 1 1">
    Network B: a drive and no power of its own
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 3. Switch sharing on

Right-click the Switch or the Bridge from within eight blocks to open the Federation screen. It draws each network as
a card. Click the line between the two cards: the right side lists that pair's rules in two groups, "A uses B's" and
"B uses A's", with the ME power switch below them.

* Left-click a rule to step it forward (Disabled, Enabled, Enabled with re-export); right-click steps it back.
* **Storage** lets one network see, insert and extract the other's items, fluids and other resources.
* **Crafting** lets one network order from the other's pattern providers.
* **ME power** joins both networks' AE energy into one pool, in both directions.

In the scenes only network A has an energy cell, so switch **ME power** on first: network B then runs on A's power
and its drive comes online. Then switch on Storage under "A uses B's".

## 4. Check it works

Open an ME terminal on network A: network B's items are listed with A's own, and can be taken out. A rule's state
shows beside it, green when active, yellow while it is starting and red when something blocks it, with the reason
underneath; [Troubleshooting](troubleshooting.md) explains each reason.

Next: [How Federation Works](mechanics/index.md) and [Crafting Across Networks](crafting/index.md).
