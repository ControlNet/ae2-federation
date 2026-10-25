---
navigation:
  parent: mechanics/index.md
  title: Rules
  icon: ae2federation:switch
  position: 210
---

# Rules

A rule lets one network use something of another's. Rules belong to a pair of networks and a direction: "A uses B's
storage" and "B uses A's storage" are two rules. A rule set in one domain applies wherever else the same two
networks meet. Every rule starts disabled.

| Rule | What it allows | States |
|---|---|---|
| Storage | See, insert and extract the other network's items, fluids and other resources | Disabled, Enabled, Enabled with re-export |
| Crafting | Order from the other network's pattern providers, from your own terminals and automation | Disabled, Enabled, Enabled with re-export |
| ME power | Join both networks' AE energy into one pool | Disabled, Enabled |

In the Federation screen, click the line between two networks to list their rules. Left-click a rule to step it
forward, right-click to step it back.

* **Storage and Crafting are independent.** With Crafting alone, your own crafting CPU runs the job, takes the
  ingredients from what your network can see and receives the result; the other network needs no CPU. Add Storage in
  the same direction when the CPU should also use the other network's materials, or to reach leftovers that stay
  there. See [Crafting Across Networks](../crafting/index.md).
* **ME power** has one switch for the pair, and energy flows both ways. See [Power](power.md).
* **Processing** with a Federation Pattern Provider and a Processing Endpoint uses no rule: any provider can use any
  Endpoint in its domain.

## Re-export: passing access along

The third state, **Enabled with re-export**, lets a network pass its access on. If A uses B's storage and B uses C's
storage with re-export, A reaches C's storage through B. Crafting works the same way, and two networks may each
craft with the other's providers. The chain may cross from one domain into the next.

The scene below shows that case. Network A is joined to B by one Bridge, and B to C by another. Each Bridge forms a
domain of its own, so A and C never meet and no screen offers an "A uses C's" rule. A still uses C's storage, because
B re-exports it.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/across_domains.snbt" />
  <BoxAnnotation color="#915dcd" min="5.375 0 0" max="8 1 1">
    Network A: a terminal, and the energy cell that powers all three networks
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="5 0.25 0.25" max="5.375 0.75 0.75">
    Bridge between A and B: a domain of these two networks
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="3.375 0 0" max="5 2 1">
    Network B: a drive of its own, and the network that passes C's access on
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0.25 0.25" max="3.375 0.75 0.75">
    Bridge between B and C: a second domain, of B and C
  </BoxAnnotation>
  <BoxAnnotation color="#5ccd78" min="0 0 0" max="3 2 1">
    Network C: drives full of items, running on A's power
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

In the A–B Bridge's Federation screen, with connected domains shown:

<FederationTopology>
  <Network key="a" label="Network A" color="#915dcd" column="0" row="0" details="Terminal|Energy cell" />
  <Network key="b" label="Network B" color="#5CA7CD" column="1" row="0" details="Drive" />
  <Network key="c" label="Network C" color="#5ccd78" column="2" row="0" details="Drives" />
  <Domain key="ab" label="This domain" networks="a,b" opened="true" />
  <Domain key="bc" label="Domain 7E2A" networks="b,c" />
  <Rule user="a" source="b" capability="storage" />
  <Rule user="b" source="c" capability="storage" state="reexport" />
  <Energy first="a" second="b" />
  <Energy first="b" second="c" />
</FederationTopology>

### Set it up

1. **Join the networks** with two Bridges, as in the scene: one between A and B, one between B and C.
2. **Switch on ME power** for A and B in the A–B Bridge's screen, and for B and C in the B–C Bridge's screen.
3. **Right-click the B–C Bridge** and switch on Storage under "B uses C's", then step it once more, to Enabled with
   re-export.
4. **Right-click the A–B Bridge** and switch on Storage under "A uses B's". Enabled is enough here; this rule would
   need re-export only for a further network to reach B and C through A.

### What network A sees

A's terminal lists C's items together with its own and B's. A takes them out of C's drives and stores into them as
into its own storage.

* **Items move directly** between C's drives and A. B must stay loaded, with both Bridges in place, for its rules to
  work.
* **The re-export on "B uses C's" decides it.** B sees C's items in either state; re-export controls whether networks
  using B see them too.
* **Longer chains** work the same way, as long as every network along the way re-exports to the next. In a chain
  that loops back, each network still sees its own items once.

### Crafting through the chain

With "B uses C's" Crafting on Enabled with re-export and "A uses B's" Crafting on, A's terminals list the patterns of
C's <ItemLink id="ae2:pattern_provider" />s. A's crafting CPU pushes the ingredients straight to C's pattern
providers, and the results come back to A; B needs no CPU. A's CPU takes the ingredients from A, or from B when "A uses
B's" Storage is on. Leftovers such as byproducts come back to A too when C has no crafting CPU of its own; otherwise
they stay on C, where A reaches them only through "B uses C's" Storage with re-export.

### ME power across the chain

ME power has no re-export state. A shares with B and B with C, so the three pools join into one and all three run on
A's energy cell.

### In the Federation screen

The A–B Bridge's screen opens on **this domain**: A, B and the rules between them. The other scope button switches
to **with connected domains (read-only)**, which adds the B–C domain on a plate of its own, with network C and the
"B uses C's" rule in its real state, coloured as re-export. Edit that rule from the B–C Bridge.

### Try it

Step "B uses C's" Storage back to Enabled. C's items disappear from A's terminal and stay in B's. Step it to re-export
again and they come back.
