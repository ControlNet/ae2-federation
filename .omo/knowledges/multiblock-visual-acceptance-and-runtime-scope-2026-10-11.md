# Accepted visual direction and runtime assessment

The user accepted the latest appearance ("perfect") after the raised Router links
and original-white Switch backing revisions. Treat `router_style='raised'` and
`switch_style='white-backed'` as the accepted visual reference. Runtime assembly,
size limits, invalid-state behavior and chunk-unload semantics remain undecided.

Runtime assessment from local code: CableBakedModel provides a connection-mask,
ModelData and cached BakedQuad integration precedent. Reuse this pattern, not its
cable-specific translucent render layer. Static device geometry belongs in chunk
meshes, not a per-frame block entity renderer. Actual Minecraft AO, culling and
resource reload behavior still require in-game inspection.

RouterBlockEntity currently owns six independent RouterFacePorts and publishes
per-block domain evidence. A multiblock must coordinate exterior ports and suppress
interior ports without merging the native ME grids attached to a Switch. Lifecycle
and topology integration carry more risk than the now-prototyped geometry. Avoid
forcing a registry rewrite merely to introduce logical structure identity.

Suggested delivery order: bounded cuboid validation plus synchronized membership
and in-game appearance; then operational port gating and device aggregation; then
merge/split, save/load and cross-chunk regression coverage. This is an assessment,
not authorization to begin implementation or an accepted operational contract.

The user explicitly requested an idea document for handoff. The canonical entry is now
`docs/ideas/multiblock-device-assembly-and-appearance.md`, with accepted style selectors,
exact artifact paths, source/tooling links, reproduction commands, verified boundaries,
suggested runtime sequencing and open operational decisions. The idea index, broader
capacity page and preview README point to this status. Local Markdown links and all
listed final artifact paths were checked; whitespace checks pass. No runtime code was
changed as part of this documentation handoff.
