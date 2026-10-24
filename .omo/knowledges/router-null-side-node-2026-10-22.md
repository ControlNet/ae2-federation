# Router/Switch: AE2 asks for the node on a null side (2026-10-22)

`IInWorldGridNodeHost.getGridNode(Direction)` may be called with `null`: in AE2 a null side means "not tied to a face"
(the cable bus's center). AE2 iterates `Platform.DIRECTIONS_WITH_NULL` when it wants every node of a block:
`GridsCommand.visitGridAt` (`/ae2 grids export <serial>`), `PartHostTooltips` (Jade/TOP), `PlotTestHelper`, and the
cable bus itself. Hosts must return null there, not throw.

`RouterBlockEntity` keeps one `RouterFacePort` per face in an `EnumMap`, so `facePorts.get(null).node()` threw a
NullPointerException. `getGridNode` now returns null for a null side (the nodes all sit on faces), like
`federationDomainPort(null)` already did. GameTest `router.null-side-node` (`routernullsidenode`) asks a Router and a
Switch through `GridHelper.getNodeHost` for the null side. `RouterTopologyContractTest` pins the `router.*` manifest
set, so a new `router.*` case is added there too, and to the router case list in `gradle/federation-qa.gradle`.

In-game reproduction: dedicated server, `setblock 0 -60 0 ae2federation:switch` next to `setblock 1 -60 0
ae2:pattern_provider` (a block pattern provider targets all six sides), find the provider's grid serial with console
`ae2 grids export N` (the one that fails, or exports more than one grid), then the player types `/ae2 grids export N`
in chat (`tools/visual/game_input.py command`; `execute as` from the console sends the error to the console, not the
chat). Before: "An unexpected error occurred trying to execute that command"; hover shows the
`RouterFacePort.node()` NPE. After: "Exporting 6 grids". Images: `build/router-null-side-check/before-after.png`.

The release jar in `neoforge-1.21.1/build/libs` is rebuilt by `federationVerify`; build a pre-fix jar from a
detached worktree and confirm it with `javap -c` before using it as "before".
