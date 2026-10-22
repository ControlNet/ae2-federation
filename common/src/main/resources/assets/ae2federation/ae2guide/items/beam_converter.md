---
navigation:
  parent: items/index.md
  title: ME Federation Beam Converter
  icon: ae2federation:beam_converter
  position: 125
categories:
- network infrastructure
item_ids:
- ae2federation:beam_converter
- ae2federation:creative_beam_converter
---

# ME Federation Beam Converter

<Row>
  <BlockImage id="ae2federation:beam_converter" scale="6" />
  <BlockImage id="ae2federation:creative_beam_converter" scale="6" />
</Row>

A [Federation domain](../mechanics/index.md) runs on FE. Its Switches, Routers, cables, Pattern Providers and Endpoints use a
little every tick, and the domain draws it from its Beam Converters. A domain whose converters cannot pay **stops**,
just as a domain over [capacity](../mechanics/capacity.md) does: its networks keep working on their own, but share no
storage, crafting, processing or power until it has FE again.

<GameScene zoom="5" interactive={true} background="transparent">
  <ImportStructure src="../assets/beam_converter.snbt" />
  <BoxAnnotation color="#dddddd" min="3 1 0" max="4 3 1">
    A 1×1×2 Beam Converter: its front faces down onto the cable. FE goes in through any other face
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="5 0 0" max="8 2 1">
    Network A
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="3 1 1">
    Network B
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## What a domain uses

| Block | FE per tick, each block |
|---|---|
| <ItemLink id="ae2federation:cable" /> | 0.5 |
| <ItemLink id="ae2federation:switch" />, <ItemLink id="ae2federation:router" /> | 2 |
| <ItemLink id="ae2federation:pattern_provider" />, <ItemLink id="ae2federation:processing_endpoint" /> | 2 |
| <ItemLink id="ae2federation:quantum_cube" />, formed | 44 |
| <ItemLink id="ae2federation:federation_p2p_tunnel" />, Beam Converter, <ItemLink id="ae2federation:beam_former" /> | 0 |

A bigger Switch or Router uses 2 for each of its blocks, and each cable in a bundle uses 0.5. The server can scale
every cost, or turn the requirement off; then no domain needs a converter. A linked pair of
[Beam Formers](beam_former.md) costs more the longer its beam; see its page. A
[Quantum Cube](quantum_cube.md) uses 44 for each block of a formed cube, linked or not.

## Size

A converter is a solid box of converter blocks, placed any way round. Its front must have the cross-section of the
**largest cable in its domain**, and the box is twice as long, toward the front, as the front's longer side:

| Largest cable | Converter |
|---|---|
| single cable | 1×1, 2 long |
| 2×2 bundle | 2×2, 4 long |
| 3×3 bundle | 3×3, 6 long |

The front joins the domain like a Switch face; build a converter against a cable or any Federation block. A converter of
another size forms and joins the domain, but supplies nothing. A linked pair of Beam Formers counts as cable of its
square, and a linked pair of Quantum Cubes as cable of its face centre. When you add a larger [bundle](../mechanics/bundled-cables.md), build a
converter to match it. Right-click a block that does not form with an empty hand to see why on the action bar; with a block in hand you
build on it as usual.

Once formed, the converter's front is one end of its length: the end most of its blocks faced, or else the end touching
a Federation cable. Right-click a formed converter with a wrench to turn it end for end.

## Power

A converter is one buffer of 16,000 FE per block. Pipe FE into any face but the front, with any mod's cables or
generators: one cable fills the whole converter. A domain adds up the buffers of all its matching converters and draws
its use from them evenly.

When they run dry, the domain stops, and keeps drawing what comes in. It runs again once it has had enough FE for more
than 30 ticks in a row, as an AE2 network restarts after a power loss. A new domain runs at once if its converters can
pay.

While a domain is stopped, its cables glow red and thin, and its topology screen turns dark red. The corner of the
topology screen shows how full the converters are: green while the domain runs, yellow while it uses more than comes in,
and red once it has stopped. Look at a converter with Jade to see its buffer and the FE it takes in, green when it
matches its domain and red when it does not.

The **Creative ME Federation Beam Converter** follows the same size rules and never runs out.

<RecipeFor id="ae2federation:beam_converter" />
