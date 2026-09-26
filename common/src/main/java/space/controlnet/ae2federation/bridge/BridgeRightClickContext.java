package space.controlnet.ae2federation.bridge;

import appeng.api.networking.IGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record BridgeRightClickContext(BlockPos position, Direction side, BridgeOperationalReason reason,
        IGrid mainGrid, IGrid outerGrid) {
}
