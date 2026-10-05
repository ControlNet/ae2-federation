---
navigation:
  parent: examples/index.md
  title: Outsourced Furnaces
  icon: ae2federation:processing_endpoint
  position: 30
---

# Outsourced Furnaces

**Goal:** order stone on your main network while the furnaces that smelt it sit on small networks of their own. The
processing pattern stays on your main network, and no rule or shared storage is needed.

**You need:** your main network with a crafting CPU, a crafting terminal and cobblestone in storage; a
<ItemLink id="ae2federation:pattern_provider" />; one <ItemLink id="ae2federation:processing_endpoint" />, furnace,
hopper and <ItemLink id="ae2:storage_bus" /> per furnace; <ItemLink id="ae2federation:cable" />.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/endpoint_furnaces.snbt" />
  <BoxAnnotation color="#915dcd" min="10 0 0" max="12 2 1">
    Your main network: crafting terminal, crafting CPU and storage
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="9 0 0" max="10 1 1">
    Federation Pattern Provider: front on the Federation Cable, back on your network
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="1 0.3 0.3" max="9 0.7 0.7">
    Federation Cable to both Endpoints
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="5 1 0" max="6 2 1">
    Processing Endpoint: front down on the cable, top face on its furnace's subnet
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4.125 3 0.125" max="4.875 3.3 0.875">
    Storage Bus on the furnace's top: inputs go straight in
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4 1 0" max="5 2 1">
    Hopper under the furnace: pushes the stone into the Endpoint's side
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 1 0" max="2 4 1">
    First furnace subnet
  </BoxAnnotation>
  <BoxAnnotation color="#cdc35c" min="4 1 0" max="6 4 1">
    Second furnace subnet
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

The Federation screen then shows your main network wired to the two Endpoints its Provider maps. Inputs go out along
the wires and results come back, and both subnets run on your network's power:

<FederationTopology>
  <Network key="main" label="Main network" color="#915dcd" column="0" row="0" details="Crafting CPU, terminal|Pattern Provider" />
  <Endpoint key="first" label="Endpoint · furnace 1" owner="main" energy="true" details="First furnace subnet" />
  <Endpoint key="second" label="Endpoint · furnace 2" owner="main" energy="true" details="Second furnace subnet" />
</FederationTopology>

## Build one furnace first

1. **Place the Provider** with its front on Federation Cable and another face on your network.
2. **Place the Endpoint** with its front on the same cable.
3. **Build the furnace's subnet** on another face of the Endpoint: an ME cable with a Storage Bus on the furnace's top.
   The Endpoint powers this subnet from your network while the Provider uses it, so it needs no power of its own.
   It must not connect to your main network.
4. **Return the results.** A hopper under the furnace pushes the stone into one of the Endpoint's faces other than
   its front.
5. **Give the furnace fuel.** The Storage Bus only fills the top slot. Put fuel in yourself, or feed it with another
   hopper from the side.
6. **Add the pattern.** Encode a processing pattern from one cobblestone to one stone, put it in the Provider, and in
   the Provider's wiring graph drag it onto the Endpoint.
7. **Order stone** on your main network. The cobblestone goes into the subnet's storage, which is the furnace; the
   stone comes back through the hopper and the Endpoint into your network.

## Add more furnaces

Build a second subnet with its own Endpoint, furnace and hopper on the same Federation Cable, and map the same
pattern onto it. Two Endpoints of one Provider must sit on different subnets. Turn on Blocking mode in the Provider:
each Endpoint then takes one batch at a time, the next batch goes to an Endpoint that is free, and a furnace that is
stuck does not hold up the others. How the batches are spread is AE2's own choice, so do not expect an exact split.

## Try it

Break the hopper while a job runs. The stone stays in the furnace and the job waits: results only count once they
enter the Endpoint, and stone left in the subnet's storage never returns. Put the hopper back and the job finishes.
