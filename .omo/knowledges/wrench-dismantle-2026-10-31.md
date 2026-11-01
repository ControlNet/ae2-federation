# Wrench Shift+right-click dismantle (2026-10-31)

Owner request: Shift+right-click with a wrench picks our blocks up, as AE2 does.

## How AE2 does it (19.2.17)
`appeng.hooks.WrenchHook.onPlayerUseBlockEvent` (NeoForge `RightClickBlock`): main hand, not spectator, sneaking,
`ConventionTags.WRENCH` (`c:tools/wrench`), block entity `instanceof AEBaseBlockEntity`, `level.mayInteract`, then
`AEBaseBlockEntity.disassembleWithWrench`: `Block.getDrops(... player, wrench)` into
`player.getInventory().placeItemBackInInventory`, `playerWillDestroy`, `removeBlock`, `destroy`; sound
`ITEM_FRAME_REMOVE_ITEM` 0.7; particles suppressed through a ThreadLocal in `AEBaseBlock.spawnDestroyParticles`.
Provider and Endpoint (`AENetworkedBlockEntity`) were already covered by this hook.

## Ours
`router/WrenchDismantle` mirrors it for every `FormableDevice` (Router, Switch, Beam Converter, Beam Former, Quantum
Cube); `BundleInteraction` (which owns all wrench clicks on cables) calls it for cables, bundled or not. It skips
`playerWillDestroy` (particles/sound) and fires `GameEvent.BLOCK_DESTROY` itself. Before, a Shift+wrenched bundled cell
dropped on the ground and a plain cable ignored the click.

## Test gotcha
In a GameTest a plain (non-sneak) wrench click on a Router opens its screen, and the mock player throws
"Payload neoforge:advanced_open_screen may not be sent to the client!". Use a cable for the plain-click check.
GameTest `wrenchdismantleseveryfederationblock` fails with the listener removed ("router was dismantled").
In game: `build/wrench-dismantle-check/before-after.png` (tools/shoot.sh; `/tp ... 180 35` aims reliably, `facing`
did not).
