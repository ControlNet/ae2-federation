package space.controlnet.ae2federation.crafting.terminal;

import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import java.util.Objects;

public final class CapturedTerminalRequester implements ICraftingSimulationRequester {
    private final IActionSource actionSource;
    private final IGridNode gridNode;

    CapturedTerminalRequester(IActionSource actionSource, IGridNode gridNode) {
        this.actionSource = Objects.requireNonNull(actionSource);
        this.gridNode = Objects.requireNonNull(gridNode);
    }

    @Override
    public IActionSource getActionSource() {
        return actionSource;
    }

    @Override
    public IGridNode getGridNode() {
        return gridNode;
    }
}
