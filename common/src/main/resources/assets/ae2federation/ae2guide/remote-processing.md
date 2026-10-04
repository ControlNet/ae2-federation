---
navigation:
  parent: index.md
  title: Remote Crafting
  icon: ae2federation:processing_endpoint
  position: 30
---

# Remote Crafting

There are two ways to have another network's machines carry out your crafting jobs:

* **Use the other network's AE2 pattern providers.** Switch on a Crafting rule, and your terminals list the other
  network's patterns as if its pattern providers were on your network. The other network keeps its usual AE2 setup.
* **Send patterns through a Federation Pattern Provider.** Your own Provider holds the processing patterns and sends
  each to the Processing Endpoints you choose. This needs no rule and shares no storage.

## Using another network's AE2 pattern providers

Say network B has the machines: ordinary AE2 <ItemLink id="ae2:pattern_provider" />s holding their patterns, next to
<ItemLink id="ae2:molecular_assembler" />s or processing machines, set up the usual AE2 way. Network A wants to order
from them.

1. **Connect the two networks** with a Bridge or Routers; see [Getting Started](getting-started.md).
2. **Switch on "A uses B's" Crafting** in the Federation screen. It switches the same direction's Storage on too,
   because A's crafting CPU takes the ingredients from what A can see.
3. **Order from network A.** A's terminals list B's patterns among A's own craftables. Request one as usual.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="assets/native_projection.snbt" />
  <BoxAnnotation color="#915dcd" min="5 1 0" max="6 2 1">
    Network A's crafting CPU: plans and runs the job
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="4.125 0.125 0" max="4.875 0.875 0.2">
    Network A's crafting terminal: lists network B's patterns to order
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0.25 0.25" max="3.375 0.75 0.75">
    A Bridge (or Routers) connects the two networks
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="1 0 0" max="2 1 1">
    Network B's AE2 Pattern Provider: receives the ingredients from network A's CPU
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="1 1 1">
    Molecular Assembler: the result goes back to network A
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

Network A's crafting CPU plans and runs the job, with the materials network A can see, which include network B's
storage. It pushes each step's ingredients to B's pattern provider, the machine works as usual, and the results go
back to A's CPU as soon as they enter network B. Network B needs no crafting CPU.

* **Network A needs a crafting CPU.** Without one, AE2 reports that no CPU is available, as usual.
* **Chains work with re-export.** If A uses B's Crafting, and B uses C's Crafting with re-export, A can also order
  from C's pattern providers.
* **Priority, blocking mode and busy providers** work as on one network: A's CPU chooses among the pattern providers
  by AE2's own rules.
* **Level emitters with a Crafting Card** on network B are not offered to network A.
* **Leftovers stay on network B:** byproducts the pattern does not list, and the results of a job cancelled on
  network A. Network A still sees them through the Storage rule.
* **If the networks are disconnected** while a job runs, results that arrive in the meantime stay on network B and
  A's CPU keeps waiting; cancel the job on network A.

For a complete build, see [Order from an Assembly Workshop](examples/remote-assembly.md).

## Federation Pattern Provider and Processing Endpoint

A <ItemLink id="ae2federation:pattern_provider" /> on one network can send processing patterns to machines that
belong to another network, through a <ItemLink id="ae2federation:processing_endpoint" /> beside those machines.
The results come back to the Provider's network. Processing needs no rule in the Federation screen.

<Row>
  <RecipeFor id="ae2federation:pattern_provider" />
  <RecipeFor id="ae2federation:processing_endpoint" />
</Row>

### Setting up

1. **The machines' network.** Build a small ME network (a processing subnet) whose storage feeds the machines, for
   example with storage buses or interfaces facing them, the usual AE2 way. Place the Endpoint so that one of its
   ME faces joins this subnet. The subnet must not be the Provider's own network.
2. **Face both fronts to the Federation side.** Both blocks have one Federation face, their front, which faces the
   block you clicked when placing them. Click Federation Cable (or a Router) to place them, or put the two fronts
   against each other. The Provider's other five faces join its own ME network like a normal pattern provider, and
   it uses one channel there.
3. **Insert patterns.** Right-click the Provider and put encoded processing patterns in its nine slots.
4. **Map each pattern.** Drag a pattern onto an Endpoint in the wiring graph, or click the pattern and then the
   Endpoint. One pattern can go to several Endpoints and one Endpoint can take several patterns. Only mapped
   patterns can be requested.

A small example: the Provider's network processes through a furnace on a subnet of its own.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="assets/remote_processing.snbt" />
  <BoxAnnotation color="#915dcd" min="5 0 0" max="7 2 1">
    The Provider's network
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4 0 0" max="5 1 1">
    Federation Pattern Provider: front on the Federation Cable, back on its own network
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 0.3 0.3" max="4 0.7 0.7">
    Federation Cable
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="1 0 0" max="2 1 1">
    Processing Endpoint: front on the Federation Cable, top face on the machine subnet
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="1 1 0" max="3 3 1">
    Processing subnet, with its own power
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="0.125 2 0.125" max="0.875 2.3 0.875">
    Storage Bus: the subnet's storage, so the inputs go straight into the furnace
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="0 0 0" max="1 1 1">
    Hopper: pushes the results into a side of the Endpoint, not its front
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

When the Provider's network crafts, the inputs go into the subnet's ME storage, and from there to the machines.
Machines (or pipes) must push their results into one of the Endpoint's faces other than its front. They go to the
Provider's return buffer for that Endpoint, then into the Provider's network. Results left in the subnet's storage
do not return. For a complete build with fuel, Blocking mode and a second furnace, see
[Outsourced Furnaces](examples/endpoint-furnaces.md).

### One Provider per Endpoint

An Endpoint belongs to one Provider at a time. Mapping a pattern claims it; other Providers show it dashed and
read-only, and dropping a pattern on it is refused with the name of its owner. To hand an Endpoint over, remove its
mappings and release it from the Provider that owns it. Release waits until nothing is still being sent and its
return buffer is empty.

Two Endpoints mapped by the same Provider must sit on different subnets.

### Local mode

An Endpoint can also serve an ordinary pattern provider on another network: place an AE2
<ItemLink id="ae2:pattern_provider" />, as a block or as a part on a cable, against the Endpoint's front. Addon pattern
providers work the same way. The Endpoint then works in local mode and cannot take Federation patterns until that
pattern provider is removed.
