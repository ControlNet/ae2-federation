# Multiblock assembly and rendering reference

The user selected design discussion before implementation, prioritized Switch/Router multiblocks over
bundled cables, required an isolated worktree, and requested later rendered images under `./build`.
Worktree: the sibling `ae2-federation-multiblock` worktree, branch `design/multiblock-devices`, based
on `fad910e1`. No runtime code or artwork was changed and no render was produced in this phase.

## Pinned AE2 source inspection

Read the locally cached `org.appliedenergistics:appliedenergistics2:19.2.17` sources JAR, rather than
assuming behavior from current upstream development. Relevant entries:

- `appeng/me/cluster/MBCalculator.java`: derives candidate bounds, checks for valid same-family
  blocks immediately outside each face, verifies the interior, updates/destroys clusters, and
  prevents recursive modifications. It does not use a flood-fill algorithm; a proposed component
  search for Federation must not be described as AE2's exact implementation.
- `appeng/me/cluster/implementations/CraftingCPUCalculator.java`: checks every position in the
  cuboid for a crafting block entity and requires at least one positive-storage block. The scale
  check rejects coordinate differences greater than 16, not a directly stated 16-block inclusive
  length. No Federation size limit or crafting-storage requirement is inferred from this code.
- `appeng/blockentity/crafting/CraftingBlockEntity.java`: formed state is backed by the server
  cluster and synchronized through blockstate; unformed crafting blocks expose no connectable
  Grid sides. Model data describes neighboring crafting-block directions, and blockstate changes
  request model-data refresh. Its client connection helper checks the neighboring block family,
  not a synchronized cluster identity; the stronger membership rule in our proposal is our own.
- `appeng/blockentity/crafting/CraftingCubeModelData.java`: carries connection directions.
- `appeng/client/render/crafting/CraftingCubeBakedModel.java`: draws corner and horizontal/vertical
  ring pieces around the body; panels extend to 0 or 16 on connected directions. This is geometry
  plus UV/material composition, not merely texture switching. It is package-private and not a
  public API base class for direct subclassing by this project.

## Current project evidence

- `RouterBlock` and `RouterBlockEntity` are shared by Switch and Router; ordinary ME attachment
  is enabled only for Switch. Each physical face has independent port state.
- `RouterFacePort` hides an ME boundary node when facing a Federation port; multiblock work must
  preserve network isolation even during rebuilds.
- Both devices currently have real artist-authored models/textures. The feature page's historical
  missing-texture statement is stale relative to the checked-in assets and `docs/art/README.md`.
- `models/block/router.json`: 51 elements, body inset to 1..15, eight corners, twelve edges, six
  face pieces, and grouped face details. `models/block/switch.json`: 73 elements with body/faces,
  eight corners and twelve multi-element edge groups. Both use 16x16 texture images, inspected
  directly. All element rotations are zero, and overall geometry lies within 0..16.
- JSON `groups` provide authoring organization; they do not enable runtime conditional parts.
- `DeviceOcclusion.ROUTER` is used by both devices and hides no neighbor faces. A formed model
  needs intentional internal-face suppression rather than blindly switching to full occlusion.
- `CableBakedModel` already demonstrates model wrapping, connection-driven model data and cached
  baked geometry. `CableCoreFaces` maps six face orientations to texture-space edge directions.
  These provide local integration examples, not ready-made device multiblock assembly.

## Proposal and verification boundary

The [design proposal](../../docs/ideas/multiblock-device-assembly-and-appearance.md) recommends
homogeneous solid bodies, single-block validity, explicit invalid-state behavior, and continuous
outer frames with repeating inner panels. These details are assistant recommendations, not accepted
user decisions. Assembly, lifecycle, rendering and performance remain untested in game.

Only documentation formatting and local link targets are verified in this phase. No images need
to be moved: none were generated. Later previews belong under the worktree's `build/` directory.
