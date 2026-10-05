---
navigation:
  parent: examples/index.md
  title: A Market Hub
  icon: ae2federation:router
  position: 25
---

# A Market Hub

**Goal:** several districts trade through one market network. Each district has rules with the market only, and the
market passes on what its suppliers make. Connect another supplier to the market, and every district can order from it
without a new rule of its own.

**You need:** a district network with a crafting CPU, a crafting terminal, storage and power; a market network with
storage; a supplier network, here a sawmill whose <ItemLink id="ae2:pattern_provider" />s and
<ItemLink id="ae2:molecular_assembler" />s turn logs into planks; a <ItemLink id="ae2federation:router" /> they all
touch.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/market_hub.snbt" />
  <BoxAnnotation color="#915dcd" min="4 0 0" max="7 2 1">
    District: crafting terminal, crafting CPU, storage, and the energy cell that powers all three networks
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="2 2 0" max="5 3 1">
    Market: storage only, with no CPU and no patterns
  </BoxAnnotation>
  <BoxAnnotation color="#5ccd78" min="0 0 0" max="3 2 1">
    Sawmill: pattern providers next to Molecular Assemblers
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0 0" max="4 1 1">
    One Router: each face joins the network it touches
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

The Federation screen then shows the three networks and the rules between them:

<FederationTopology>
  <Network key="district" label="District" color="#915dcd" column="0" row="0" details="Crafting CPU, terminal|Energy cell" />
  <Network key="market" label="Market" color="#5CA7CD" column="1" row="0" details="Drives" />
  <Network key="sawmill" label="Sawmill" color="#5ccd78" column="2" row="0" details="Pattern providers|Molecular Assemblers" />
  <Rule user="district" source="market" capability="crafting" />
  <Rule user="district" source="market" capability="storage" />
  <Rule user="market" source="sawmill" capability="crafting" state="reexport" />
  <Rule user="market" source="sawmill" capability="storage" />
  <Energy first="district" second="market" />
  <Energy first="market" second="sawmill" />
</FederationTopology>

## Build it

1. **Join the three networks** on the faces of one Router, as in the scene.
2. **Switch on Crafting under "District uses Market's"**. Its Storage comes on with it.
3. **Switch on Crafting under "Market uses Sawmill's"** and step it on once more, to Enabled with re-export. The
   market now passes the sawmill's recipes on to every network that uses the market's Crafting.
4. **Switch on ME power** for the district and the market, and for the market and the sawmill. Power pools along the
   chain, so all three run on the district's energy cell.
5. **Order planks from the district.** The sawmill's recipe is listed among the district's craftables.

## How the job runs

The district's crafting CPU runs the job. It sends the logs straight to the sawmill's pattern providers, and the planks
come straight back to it. The market takes no part: it needs no CPU, and nothing passes through it. The rules only
decide who may reach whom.

## Try it

Step "Market uses Sawmill's" Crafting back to Enabled. The planks disappear from the district's craftables, while the
market can still order them itself. Step it to re-export again and they come back.
