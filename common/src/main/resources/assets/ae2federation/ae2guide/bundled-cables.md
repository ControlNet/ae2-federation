---
navigation:
  parent: index.md
  title: Bundled Cables
  icon: ae2federation:cable
  position: 24
---

# Bundled Cables

One <ItemLink id="ae2federation:cable" /> carries at most 8 networks and devices (see [Network Capacity](network-capacity.md)).
A **bundled cable** turns a block of Federation Cables into one thick cable that carries more: one shell the size of
its cross-section, with a beam for each cable running inside it.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="assets/bundled_cables.snbt" />
  <BoxAnnotation color="#dddddd" min="0 0 2" max="1 2 4">
    Router, 2×2: covers the whole end face, so the bundle connects
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="1 0 2" max="7 2 4">
    Bundled cable, 2×2 and 6 long: capacity 48
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0 4" max="5 2 6">
    Branch: the same 2×2, across the full side, over two layers
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="5 0 0" max="7 2 2">
    Turn: a branch from the last two layers
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Forming a bundle

Place ordinary Federation Cables to fill the box the bundle will take. Then, holding an AE2 wrench (or any other
wrench), right-click three of them:

1. one corner of the cross-section;
2. the opposite corner, in the same plane: a square of 2×2 or 3×3;
3. a cable straight along the cross-section, which sets the length (at most 64).

The whole box turns into one bundled cable at once. Every position in it must hold an ordinary Federation Cable that
is not bundled yet. For a bundle one layer long, make the third click inside the cross-section, on a face that points
along the bundle. Right-click air to start over.

## Capacity

A bundle counts its networks and devices like any other cable (see [Network Capacity](network-capacity.md)); only its
limit is larger:

| Cross-section | Cables' worth | With the default 8 |
|---|---|---|
| 2×2 | 6 | 48 |
| 3×3 | Unlimited | Unlimited |

When the server changes a cable's capacity, every bundle's limit changes with it.

## Extending, branching and turning

Hold Federation Cables and right-click the bundle. An outline shows where the new cables go.

* An **end face** adds one layer, using one cable for each cell of the cross-section: 4 for a 2×2.
* A **side** starts a branch with the same cross-section. It covers the side's full width and takes as many layers of
  the bundle as it needs: a 2×2 branch takes two layers, and a 3×3 branch three. The branch is centred where you click. Extend it from its end face.
* A **turn** is a branch from the last layers.

Sneak to place a single ordinary cable instead.

## Connections

* A bundle never connects to ordinary Federation Cables, and two bundles side by side stay apart. To go from a bundle
  to ordinary cables, run both into a <ItemLink id="ae2federation:router" />.
* A bundle connects only to device faces that take Federation cable and are built of enough blocks: a Router or
  <ItemLink id="ae2federation:switch" /> face, a <ItemLink id="ae2federation:beam_former" />'s back, a
  <ItemLink id="ae2federation:beam_converter" />'s front or a <ItemLink id="ae2federation:quantum_cube" />'s face centre.
  Its **end face** connects when one device covers all of it: a 2×2 bundle needs a 2×2 Router face.
* A **side** connects to a device that covers the side's full width: a 2×2 bundle's side connects to a Router face
  1×2 across it, but not to a single Router block.

Anything less is not connected. With Jade installed, looking at a bundle shows its cross-section, its capacity and how
many ends and sides are connected, and names a device that covers only part of a face.

## Taking a bundle apart

Breaking a bundled cable, or Shift+right-clicking it with a wrench, removes that one cable and drops it. The rest of
its layer turns back into ordinary Federation Cables, and the bundle splits there into two. To repair it, put the
cable back and form that layer again with the wrench: bundles of the same cross-section whose ends meet join into one.
