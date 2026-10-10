---
navigation:
  parent: examples/index.md
  title: An Alloy Furnace Two Bridges Away
  icon: useless_mod:advanced_alloy_furnace_block
  position: 71
---

# An Alloy Furnace Two Bridges Away

**Goal:** order from UselessMod's Advanced Alloy Furnace in a workshop two Bridges from your base. The workshop and
your base each have a Bridge to a trading post, and only the post has rules with the workshop: it passes the furnace's
recipes on with re-export. The furnace keeps crafting patterns in its own pattern slots and crafts them itself, with
no Molecular Assembler. This page appears because UselessMod is installed.

**You need:**

* the workshop network: the <ItemLink id="useless_mod:advanced_alloy_furnace_block" /> on an ME cable, and a drive
  for the workshop's own stock; no power of its own.
* the trading post network: an ME cable and nothing else.
* your base network: a crafting CPU, a crafting terminal, storage and power.
* two <ItemLink id="ae2federation:bridge" />s: one on the post's cable touching the workshop's cable, and one on your
  base's cable touching the post's.

<GameScene zoom="3" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/alloy_furnace.snbt" />
  <BoxAnnotation color="#915dcd" min="4.375 0 0" max="7 2 1">
    Your base: crafting terminal, crafting CPU, storage, and the energy cell that powers all three networks
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4 0.25 0.25" max="4.375 0.75 0.75">
    Your base's Bridge: on your base's cable, its outer side touching the post's cable
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="2.375 0 0" max="4 1 1">
    The trading post: cable only, with no storage, CPU or power of its own
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 0.25 0.25" max="2.375 0.75 0.75">
    The post's Bridge to the workshop: on the post's cable, its outer side touching the workshop's cable
  </BoxAnnotation>
  <BoxAnnotation color="#5ccd78" min="0 0 0" max="2 2 1">
    The workshop: the Advanced Alloy Furnace with your crafting patterns, and a drive with the workshop's own stock
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

Each Bridge makes a domain of its own with the two networks it joins. Right-click your base's Bridge: its screen shows
this domain, your base and the post. Pick "with connected domains (read-only)" on the scope button, and the post's
domain with the workshop joins it on a plate of its own, read-only:

<FederationTopology>
  <Network key="base" label="Base" color="#915dcd" column="0" row="0" details="Crafting CPU, terminal|Storage, energy cell" />
  <Network key="post" label="Post" color="#5CA7CD" column="1" row="0" details="Cable only" />
  <Network key="workshop" label="Workshop" color="#5ccd78" column="2" row="0" details="Alloy Furnace|Drive" />
  <Rule user="base" source="post" capability="crafting" />
  <Rule user="base" source="post" capability="storage" />
  <Rule user="post" source="workshop" capability="crafting" state="reexport" />
  <Rule user="post" source="workshop" capability="storage" />
  <Energy first="base" second="post" />
  <Energy first="post" second="workshop" />
  <Domain key="d1" label="This domain" networks="base,post" opened="true" />
  <Domain key="d2" label="Domain 5E07" networks="post,workshop" />
</FederationTopology>

## Build it

1. **Put the furnace on the workshop's cable** and your crafting patterns into its pattern slots. It needs a channel,
   like other AE2 devices.
2. **Join the post to the workshop** with a Bridge on the post's cable, its outer side touching the workshop's cable.
   Right-click it, switch on Crafting under "Post uses Workshop's", and step it on once more, to Enabled with
   re-export. Its Storage comes on with it, plain Enabled. Switch on **ME power** for the pair too.
3. **Join your base to the post** with a Bridge on your base's cable, its outer side touching the post's cable.
   Right-click it: its screen shows only your base and the post. Switch on Crafting under "Base uses Post's", which
   switches its Storage on too, and ME power. Power pools along the chain, so all three networks run on your base's
   energy cell, and the furnace crafts with no FE of its own.
4. **Order from your base.** The furnace's recipes are listed among your base's craftables. Your base's crafting CPU
   runs the job: it sends the ingredients straight to the furnace, and what the furnace makes comes straight back to
   it, into your base's storage. The post takes no part and needs no CPU.

This is the [Market Hub](market-hub.md) across two domains. There, one Switch holds all three networks in
one domain; here the workshop and your base share no domain, so no screen has a rule between them. "Post uses
Workshop's" is changed from the post's Bridge to the workshop; your base's screen shows it read-only.

## What stays on the workshop

The post's Storage rule for the workshop is plain Enabled, so the workshop's drive is the post's to see, not your
base's. That does not matter for the job: its results come back to your base's waiting CPU. Anything that lands in the
workshop's storage instead, such as a by-product or the result of a job you cancelled (see
[Remote Crafting](../remote-processing.md)), is out of your base's sight. To see it from your base, step "Post uses
Workshop's" Storage on to Enabled with re-export as well.

## Try it

Step "Post uses Workshop's" Crafting back to Enabled. The furnace's recipes disappear from your base's terminal,
while the post itself keeps them. Step it to re-export again: they return, and your base can order again.
