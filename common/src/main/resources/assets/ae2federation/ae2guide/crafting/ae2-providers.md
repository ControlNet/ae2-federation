---
navigation:
  parent: crafting/index.md
  title: Another Network's Pattern Providers
  icon: ae2:pattern_provider
  position: 10
---

# Another Network's Pattern Providers

Say network B has the machines: ordinary AE2 <ItemLink id="ae2:pattern_provider" />s holding their patterns, next to
<ItemLink id="ae2:molecular_assembler" />s or processing machines, set up the usual AE2 way. Network A wants to order
from them.

1. **Connect the two networks** with a Bridge or Switches; see [Getting Started](../getting-started.md).
2. **Switch on "A uses B's" Crafting** in the Federation screen. Storage is not needed: A's crafting CPU pays with
   what A can see, here A's own storage.
3. **Switch on ME power** for the pair. Network B then runs on network A's power and needs no energy cell of its own.
4. **Order from network A.** A's terminals list B's patterns among A's own craftables. Request one as usual.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/native_projection.snbt" />
  <BoxAnnotation color="#915dcd" min="5 1 0" max="6 2 1">
    Network A's crafting CPU: plans and runs the job
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="5 0 0" max="6 1 1">
    Network A's energy cell: powers both networks through ME power
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="4.125 0.125 0" max="4.875 0.875 0.2">
    Network A's crafting terminal: lists network B's patterns to order
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0.25 0.25" max="3.375 0.75 0.75">
    A Bridge (or Switches) connects the two networks
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="1 0 0" max="2 1 1">
    Network B's AE2 Pattern Provider: receives the ingredients from network A's CPU
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="1 1 1">
    Molecular Assembler: the result goes back to network A
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

In the Federation screen:

<FederationTopology>
  <Network key="a" label="Network A" color="#915dcd" column="0" row="0" details="Crafting CPU, terminal|Storage, energy cell" />
  <Network key="b" label="Network B" color="#5CA7CD" column="1" row="0" details="Pattern provider|Molecular Assembler" />
  <Rule user="a" source="b" capability="crafting" />
  <Energy first="a" second="b" />
</FederationTopology>

Network A's crafting CPU plans and runs the job, with the materials network A can see. It pushes each step's
ingredients straight to B's pattern provider, the machine works as usual, and the results go back to A's CPU as soon
as they enter network B. Network B needs no crafting CPU.

* **Network A needs a crafting CPU.** Without one, AE2 reports that no CPU is available, as usual.
* **Chains work with re-export.** If A uses B's Crafting, and B uses C's Crafting with re-export, A can also order
  from C's pattern providers.
* **Priority, blocking mode and busy providers** work as on one network: A's CPU chooses among the pattern providers
  by AE2's own rules.
* **Level emitters with a Crafting Card** on network B are not offered to network A.
* **Storage is optional.** Switch on Storage under "A uses B's" too, and A's CPU can also pay with B's materials.
* **Leftovers stay on network B:** byproducts the pattern does not list, and the results of a job cancelled on
  network A. Network A sees and takes them only while "A uses B's" Storage is on.
* **If the networks are disconnected** while a job runs, or network A is not loaded, results that arrive in the
  meantime wait in network B's storage and A's CPU keeps waiting. Once the networks are linked again, network B hands
  them to the job. If someone on network B uses them up first, the job waits until network B stores that much again.

For a complete build, see [Order from an Assembly Workshop](../examples/remote-assembly.md).
