---
navigation:
  parent: examples/index.md
  title: A Local Endpoint
  icon: extendedae:ex_pattern_provider
  position: 70
---

# A Local Endpoint

**Goal:** run a furnace on a small network of its own straight from ExtendedAE's Extended Pattern Provider, with no
Federation Pattern Provider, no Federation Cable and no rule. A pattern provider placed against a
<ItemLink id="ae2federation:processing_endpoint" />'s front puts it in **Local mode**. This page appears because
ExtendedAE is installed.

**You need:** your main network with a crafting CPU, a crafting terminal, storage, power and cobblestone; an
<ItemLink id="extendedae:ex_pattern_provider" />; a Processing Endpoint; a furnace, a hopper and an
<ItemLink id="ae2:storage_bus" />; a <ItemLink id="ae2:quartz_fiber" />.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/local_endpoint.snbt" />
  <BoxAnnotation color="#915dcd" min="6 0 0" max="8 2 1">
    Your main network: crafting terminal, crafting CPU, drive and energy cell
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="5 0 0" max="6 1 1">
    Extended Pattern Provider on your main network, pushing into every side, the Endpoint's front among them
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4 0 0" max="5 1 1">
    Processing Endpoint: its front faces the provider, its other faces carry the furnace's subnet
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="5 1.375 0.375" max="5.375 1.625 0.625">
    Quartz Fiber: shares your network's power with the subnet without joining the two networks
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3.125 2 0.125" max="3.875 2.3 0.875">
    Storage Bus on the furnace's top: inputs go straight in
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0 0" max="4 1 1">
    Hopper under the furnace: pushes the stone into the Endpoint's side
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="3 0 0" max="5 3 1">
    The furnace's subnet
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

A Local Endpoint belongs to no Federation domain, so right-clicking it opens its panel alone and read-only, with its mode
shown as Local. What it does for your network is still this: inputs go out to it and results come back, and the
subnet does not share your network's power through it:

<FederationTopology>
  <Network key="main" label="Main network" color="#915dcd" column="0" row="0" details="Crafting CPU, terminal|Extended Pattern Provider" />
  <Endpoint key="furnace" label="Endpoint · furnace" owner="main" energy="false" details="Local mode|Furnace subnet" />
</FederationTopology>

## Build it

1. **Place the Extended Pattern Provider** on your main network.
2. **Place the Endpoint against it** with its front on the provider: the front faces the block you click when placing
   it. A new provider pushes into every side, so it pushes into the Endpoint's front without any setting, and the
   Endpoint switches to Local mode.
3. **Build the furnace's subnet** on the Endpoint's other faces: an ME cable from its top to a Storage Bus on the
   furnace's top, and a hopper under the furnace pushing into the Endpoint's side. The subnet must not connect to your
   main network.
4. **Power the subnet.** A Local Endpoint does not share power. Put a Quartz Fiber between a cable of your main network
   and a cable of the subnet: it shares the power and keeps the networks apart.
5. **Give the furnace fuel.** The Storage Bus only fills the top slot.
6. **Add the patterns.** Encode a processing pattern from one cobblestone to one stone and put it in the Extended
   Pattern Provider, beside your other smelting patterns: it holds many more than AE2's provider (see
   [ExtendedAE's guide](extendedae:epp_intro/extended_pattern_provider.md)). Every pattern it pushes goes through the
   Endpoint into the same furnace.
7. **Order stone** on your main network. The cobblestone goes into the subnet's storage, which is the furnace; the
   stone comes back through the hopper and the Endpoint into the provider, and from there into your network.

## How Local mode works

* What touches the Endpoint's front picks its mode. A pattern provider of another network that pushes into the front,
  a block or a cable part, AE2's or an addon's, selects Local mode. Federation Cable, a Switch, a Router, a Federation
  Pattern Provider's front or nothing selects Federated mode.
* Inputs pushed into the front go into the subnet's storage. A machine that pushes results into any other face of the
  Endpoint sends them back to that provider.
* An Endpoint has one front, so it serves one provider. While it is in Local mode no Federation Pattern Provider can map
  it; replace the provider with Federation Cable and it can be mapped again.

## Try it

With a wrench, click the top of the Extended Pattern Provider, then order stone. The provider now pushes only
downwards, away from the Endpoint: the Endpoint leaves Local mode, no cobblestone reaches the furnace and the job
waits. Click the top twice more: the provider pushes into every side again, the Endpoint is back in Local mode and the
job finishes.
