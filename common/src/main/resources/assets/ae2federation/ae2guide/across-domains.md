---
navigation:
  parent: index.md
  title: Across Domains
  icon: ae2federation:bridge
  position: 25
---

# Across Domains

**Goal:** use a network you have no rule with. Network A is joined to B by one Bridge, and B to C by another. Each
Bridge forms a domain of its own, so A and C never meet and no screen offers an "A uses C's" rule. Re-export on B's
rule lets A use C's storage and crafting through B all the same.

Rules belong to a pair of networks, not to a domain (see [How Federation Works](mechanics.md)): "B uses C's" is set
once, in the domain where B and C meet, and A reaches C because B passes its access on.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="assets/across_domains.snbt" />
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

## Set it up

1. **Join the networks** with two Bridges, as in the scene: one between A and B, one between B and C.
2. **Switch on ME power** for A and B in the A–B Bridge's screen, and for B and C in the B–C Bridge's screen.
3. **Right-click the B–C Bridge** and switch on Storage under "B uses C's", then step it on once more, to Enabled with
   re-export.
4. **Right-click the A–B Bridge** and switch on Storage under "A uses B's". Enabled is enough; this rule needs
   re-export only if yet another network should reach B and C through A.

## What network A sees

A's terminal lists C's items among its own and B's, with nothing to mark where they are. A can take them out and
store into C's drives, as into its own storage.

* **Nothing passes through B.** Items move between C's drives and A directly, but B must stay loaded, with both
  Bridges in place, for its rules to work.
* **The re-export on "B uses C's" decides it.** B itself sees C's items either way; only who uses B depends on it.
* **Longer chains** work the same way, as long as every network along the way re-exports to the next. A chain that
  loops back never shows a network its own items twice.

## Crafting works the same

If "B uses C's" Crafting is Enabled with re-export and "A uses B's" Crafting is on, A's terminals list the patterns of
C's <ItemLink id="ae2:pattern_provider" />s. A's crafting CPU pushes the ingredients straight to C's pattern
providers, and the results come back to A; B needs no CPU. Neither Crafting rule needs a Storage rule. Unless "B uses
C's" Storage is on with re-export, A cannot see C's storage: A's CPU must find the ingredients on A, or on B through
"A uses B's" Storage, and leftovers, such as byproducts and the results of cancelled jobs, stay on C, out of A's reach.
See [Remote Crafting](remote-processing.md).

## Energy pools without re-export

ME power has no re-export state, and needs none. A shares with B and B with C, so all three share one pool and run on
A's energy cell alone.

## In the Federation screen

The A–B Bridge's screen opens on **this domain**: only A and B and the rules between them. Click the other scope
button, and the caption changes to **with connected domains (read-only)**: the B–C domain joins on a plate of its own,
with network C and the "B uses C's" rule in its real state, coloured as re-export. It cannot be changed there; edit it
from the B–C Bridge.

## Try it

Step "B uses C's" Storage back to Enabled. C's items disappear from A's terminal, while B still lists them. Step it to
re-export again and they come back.
