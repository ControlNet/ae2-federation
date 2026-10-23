---
navigation:
  parent: items/index.md
  title: ME Federation Quantum Cube
  icon: ae2federation:quantum_cube
  position: 434
categories:
- network infrastructure
item_ids:
- ae2federation:quantum_cube
---

# ME Federation Quantum Cube

<BlockImage id="ae2federation:quantum_cube" scale="6" />

Two bound Quantum Cubes link their Federation cables at any distance, even across dimensions, as if cable ran
between them. Like AE2's Quantum Network Bridge, they work only while both are loaded.

<GameScene zoom="2.25" interactive={true} background="transparent">
  <ImportStructure src="../assets/quantum_cube.snbt" />
  <BoxAnnotation color="#dddddd" min="4 0 0" max="7 3 3">
    A 3×3×3 cube. The cable joins the centre of its face
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="10 0 0" max="13 3 3">
    The cube bound to it; it could be in another dimension
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="13 1 1" max="17 3 2">
    Network A
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 1 1" max="4 2 2">
    Network B
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Building

A Quantum Cube is a solid cube of 3×3×3, 4×4×4 or 5×5×5 blocks. Any other shape does not form. Federation cable
joins a face only inside its centre: the middle block of a 3×3×3 face, the middle 2×2 of a 4×4×4 face or the middle
3×3 of a 5×5×5 face. The border ring of every face joins nothing, so a [bundle](../mechanics/bundled-cables.md) that touches
the border does not join.

| Cube | Face centre | Largest cable it takes |
|---|---|---|
| 3×3×3 | 1×1 | one cable |
| 4×4×4 | 2×2 | 2×2 bundle |
| 5×5×5 | 3×3 | 3×3 bundle |

## Binding

Bind two formed cubes with an AE2 <ItemLink id="ae2:memory_card" />:

1. Sneak and right-click one cube to copy it to the card.
2. Right-click the other cube with the card.

The two are now a pair. Each cube links with one other cube only: binding a cube again drops its old partner, and
the old partner is left unbound. Both cubes must be the same size; a 3×3×3 does not link with a 4×4×4.

A pair links while both cubes are formed and loaded. When one end's chunk unloads, the link breaks and each side
works on its own; it links again when the chunk loads. A cube never loads chunks itself.

Look at a cube with Jade to see where its partner is, or why it is not linked.

## Capacity and power

A linked pair counts as cable of its face centre for [network capacity](../mechanics/capacity.md): a 3×3×3 pair
carries one cable's limit, a 4×4×4 pair six cables' and a 5×5×5 pair has no limit. The networks on both sides are
counted together, as with a cable between them.

Every block of a formed cube uses 44 FE (22 AE) per tick, linked or not, as an AE2 bridge does; both cubes of a pair
pay. A block that is not part of a formed cube costs nothing.

| Cube | FE per tick, each cube |
|---|---|
| 3×3×3 | 1,188 |
| 4×4×4 | 2,816 |
| 5×5×5 | 5,500 |

A linked pair also counts as cable of its face centre when the domain's [Beam Converters](beam_converter.md) are
sized: a 5×5×5 pair needs a 3×3 converter.

<RecipeFor id="ae2federation:quantum_cube" />
