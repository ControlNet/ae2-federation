---
navigation:
  parent: index.md
  title: ME Federation Cable
  icon: ae2federation:cable
  position: 110
categories:
- network infrastructure
item_ids:
- ae2federation:cable
---

# ME Federation Cable

<GameScene zoom="5" interactive={true} background="transparent">
  <ImportStructure src="../assets/cable_connections.snbt" />
  <BoxAnnotation color="#dddddd" min="4 0 0" max="5 1 1">
    Federation Pattern Provider: connects by its front
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 0 0" max="3 1 1">
    Router: connects by any face
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="0 0 0" max="1 1 1">
    Processing Endpoint: connects by its front
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

Federation Cable links Switches, Routers, Federation Pattern Providers and Processing Endpoints into one
[Federation domain](../mechanics.md). It connects on every side to another Federation Cable, to any face of a
<ItemLink id="ae2federation:switch" /> or <ItemLink id="ae2federation:router" />, and to the front of a <ItemLink id="ae2federation:pattern_provider" />,
<ItemLink id="ae2federation:processing_endpoint" /> or <ItemLink id="ae2federation:federation_p2p_tunnel" />.

It is not an ME cable: ME cables and devices do not attach to it, it carries no channels and it uses no power. It has
no length limit. The pulses inside it are decoration; use **Live flow** in the Federation screen to see real traffic.

<RecipeFor id="ae2federation:cable" />

Any colour of <ItemLink id="ae2:fluix_glass_cable" />, mixed if you like, makes 16 cables.
