---
navigation:
  parent: index.md
  title: ME Federation Beam Former
  icon: ae2federation:beam_former
  position: 433
categories:
- network infrastructure
item_ids:
- ae2federation:beam_former
---

# ME Federation Beam Former

<BlockImage id="ae2federation:beam_former" scale="6" />

Two Beam Formers facing each other link their Federation cables through the air, as if cable ran between their
fronts. Build one at each end of a straight line, with nothing solid between them, and the two ends join one
[Federation domain](../mechanics/index.md).

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/beam_former.snbt" />
  <BoxAnnotation color="#dddddd" min="4 0 0" max="5 1 1">
    A Beam Former facing east. Its back takes a cable, like a Router face
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="7 0 0" max="8 1 1">
    Glass does not block the beam
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="12 0 0" max="15 2 1">
    Network A
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="3 1 1">
    Network B
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Building

A Beam Former is one block, or a square of 2×2 or 3×3 blocks one block thick. It faces where you look when you place
it. Its front sends the beam; its back joins Federation cable, a [bundle](../mechanics/bundled-cables.md) of the same square or
any Federation block, like a Router face.

Right-click a formed 2×2 or 3×3 with a wrench to turn it front to back. A single block turns to face the side you
clicked.

## Linking

A Beam Former links with the **nearest** former in front of it on the same line, if that one faces back and has the
same square. The fronts may be up to 256 blocks apart; the server can set up to 1024. Formers touching front to front
link too.

Any block with a collision box in the beam's path blocks it, across the whole square. Glass, glass panes, water and
AE2's quartz glass let it through. A block put in the way breaks the link after one second, and the link returns one
second after the path clears. A link holds while both ends are loaded; the chunks between them may be unloaded.

Look at a former with Jade to see its beam: linked and how long, blocked and where, or why it has no partner.

## Capacity and power

A linked pair counts as cable of its square for [network capacity](../mechanics/capacity.md): a single pair carries one
cable's limit, a 2×2 pair six cables', and a 3×3 pair has no limit. The beam shows its load like a cable's.

The domain pays for each linked pair once, by its square and length, on top of its other blocks:
2 × (the square's blocks) × L^1.05 FE per tick, L being the blocks between the fronts (at least 2).

| Pair | Blocks between | FE per tick |
|---|---|---|
| 1×1 | 16 | 37 |
| 1×1 | 64 | 158 |
| 2×2 | 64 | 630 |
| 3×3 | 64 | 1,418 |

The former blocks themselves cost nothing. A linked pair also counts as cable of its square when the domain's
[Beam Converters](beam_converter.md) are sized: a 3×3 pair needs a 3×3 converter.

<RecipeFor id="ae2federation:beam_former" />
