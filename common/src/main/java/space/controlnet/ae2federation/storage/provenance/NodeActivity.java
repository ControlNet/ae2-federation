package space.controlnet.ae2federation.storage.provenance;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;

/**
 * {@link IGridNode#isActive()} (powered, booted and channelled) for nodes that must also be on one known Grid, with the
 * Grid's booting state read once for all of them instead of once or twice per node.
 */
public final class NodeActivity {
    private NodeActivity() {
    }

    public static boolean gridBooted(IGrid grid) {
        return !grid.getPathingService().isNetworkBooting();
    }

    /**
     * {@code node.isActive() && node.getGrid() == grid}, given {@code gridBooted(grid)}. {@code isPowered()} comes
     * first: it is false for a destroyed node, whose {@code getGrid()} throws.
     */
    public static boolean activeOn(IGridNode node, IGrid grid, boolean gridBooted) {
        return node.isPowered() && node.getGrid() == grid && gridBooted && node.meetsChannelRequirements();
    }
}
