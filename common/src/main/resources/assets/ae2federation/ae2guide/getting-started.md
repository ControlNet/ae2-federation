---
navigation:
  parent: index.md
  title: Getting Started
  icon: ae2federation:nexus_processor
  position: 10
---

# Getting Started

You need two separate ME networks, and only one of them needs power: once they are connected, the other can run on
it. Federation does not replace AE2's own cables or controllers; it only connects networks.

## 1. Make Nexus Processors

Every Federation device needs a <ItemLink id="ae2federation:nexus_processor" />. First craft three Redstone Dust
over three <ItemLink id="ae2:ender_dust" /> into <ItemLink id="ae2federation:nexus_core" />, sixteen at a time.

<RecipeFor id="ae2federation:nexus_core" />

Then press each core in an <ItemLink id="ae2:inscriber" /> with one Ender Dust and one
<ItemLink id="ae2:printed_silicon" />. All three are used up.

<RecipeFor id="ae2federation:nexus_processor" />

## 2. Connect the two networks

Choose one of these. Both give you the same Federation screen.

### Two networks side by side: a Bridge

<RecipeFor id="ae2federation:bridge" />

Place the <ItemLink id="ae2federation:bridge" /> on a cable of the first network, so that its outer side touches a
cable or device of the second network. The two networks stay separate; the Bridge only connects them for Federation.

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

### Networks further apart: Routers and Federation Cable

<Row>
  <RecipeFor id="ae2federation:cable" />
  <RecipeFor id="ae2federation:router" />
</Row>

Touch one face of a <ItemLink id="ae2federation:router" /> with an ME cable of the first network and another face
with an ME cable of the second network. One Router can take up to six networks, one per face. For networks far
apart, give each its own Router and join the Routers with <ItemLink id="ae2federation:cable" />. Two Routers placed
face to face link directly, with no cable between them.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="assets/router_cable.snbt" />
  <BoxAnnotation color="#915dcd" min="6 0 0" max="8 2 1">
    Network A: its energy cell powers both networks
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="5 0 0" max="6 1 1">
    Router: one face on network A's cable, another on Federation Cable
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0.3 0.3" max="5 0.7 0.7">
    Federation Cable between the two Routers
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 0 0" max="3 1 1">
    Router: one face on network B's cable, another on Federation Cable
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="2 1 1">
    Network B: a drive and no power of its own
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 3. Switch sharing on

Right-click the Router or the Bridge (stay within eight blocks) to open the Federation screen. It draws the networks
in this Federation domain as cards. Click the line between two cards, or select one card and then the other network
in the list on the right. The right side then shows that pair's rules in two groups, "A uses B's" and "B uses A's",
each rule with a switch, and below them the shared energy switch. A rule works in its direction only.

* Left-click a switch to step it forward (Disabled, Enabled, Enabled with re-export) and right-click to step back.
* **Storage** lets one network see, insert and extract the other's items, fluids and other resources.
* **Crafting** lets one network use the other's pattern providers. It switches the same direction's Storage on too.
* **ME power** joins both networks' energy into one pool; it has a single switch for the pair.

In the scenes above only network A has an energy cell, so switch **ME power** on first: network B then runs on A's
power, and its drive comes online. Then switch on Storage under "A uses B's".

## 4. Check it works

Open an ME terminal on the network that uses the other's storage: the other network's items are listed there and can
be taken out. A rule's state shows beside its switch: green when active, yellow while it is not active yet, red when
something blocks it, with the reason underneath. See [Troubleshooting](troubleshooting.md).

Next: [How Federation Works](mechanics.md) and [Remote Crafting](remote-processing.md).
