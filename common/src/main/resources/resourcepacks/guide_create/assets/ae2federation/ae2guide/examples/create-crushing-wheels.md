---
navigation:
  parent: examples/index.md
  title: Create Crushing Wheels
  icon: create:crushing_wheel
  position: 60
---

# Create Crushing Wheels

**Goal:** order gravel on your main network while a pair of Create Crushing Wheels on a small network of its own
crushes the cobblestone. This page appears because Create is installed.

**You need:** your main network with a crafting CPU, a crafting terminal and cobblestone in storage; a
<ItemLink id="ae2federation:pattern_provider" />; a <ItemLink id="ae2federation:processing_endpoint" />;
<ItemLink id="ae2federation:cable" />; two <ItemLink id="create:crushing_wheel" />; a <ItemLink id="create:chute" />;
a <ItemLink id="ae2:storage_bus" />; rotational power for the wheels.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/create_crushing_wheels.snbt" />
  <BoxAnnotation color="#915dcd" min="5 0 0" max="7 2 1">
    Your main network: crafting terminal, crafting CPU and storage
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4 0 0" max="5 1 1">
    Federation Pattern Provider: front on the Federation Cable, back on your network
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 1 0" max="3 2 1">
    Processing Endpoint: front down on the cable, the Chute on its top, the subnet's cable on its back
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 2 0" max="3 3 1">
    Chute under the gap: takes the gravel from the wheels and pushes it down into the Endpoint
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2.125 4 0.125" max="2.875 4.3 0.875">
    Storage Bus over the gap between the wheels, facing down
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="1 3 1" max="2 4 2">
    Shafts behind the wheels, to your rotational power
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

The Federation screen then shows your main network wired to the Endpoint its Provider maps. Inputs go out along the
wire and results come back, and the subnet runs on your network's power:

<FederationTopology>
  <Network key="main" label="Main network" color="#915dcd" column="0" row="0" details="Crafting CPU, terminal|Pattern Provider" />
  <Endpoint key="wheels" label="Endpoint · wheels" owner="main" energy="true" details="Crushing Wheels subnet" />
</FederationTopology>

## Build it

1. **Place the Provider** with its front on Federation Cable and another face on your network.
2. **Place the Endpoint** with its front on the same cable, and a Chute on the Endpoint's top.
3. **Place the wheels** side by side above the Chute, with one block of space between them right over the Chute.
4. **Turn the wheels** with rotational power so that they turn inwards, towards each other, as Create's Ponder scenes
   show. The Endpoint does not power Create machines.
5. **Build the wheels' subnet** on another face of the Endpoint: an ME cable up to a Storage Bus over the gap between
   the wheels, facing down. The Endpoint powers this subnet from your network while the Provider uses it. It must not
   connect to your main network.
6. **Return the results.** The wheels drop their products only into Create's own Chutes and belts; anywhere else they
   throw them out as items. The Chute under them pushes the gravel down into the Endpoint.
7. **Add the pattern.** The wheels crush one cobblestone into one gravel. Encode a processing pattern from one
   cobblestone to one gravel, put it in the Provider, and in the Provider's wiring graph drag it onto the Endpoint.
8. **Order gravel** on your main network. The cobblestone goes into the subnet's storage, which is the wheels; the
   gravel comes back through the Chute and the Endpoint into your network.

## Try it

Stop the wheels, then order gravel. Nothing is crushed, no gravel comes back and the job waits. Turn the wheels again
and the job finishes.
