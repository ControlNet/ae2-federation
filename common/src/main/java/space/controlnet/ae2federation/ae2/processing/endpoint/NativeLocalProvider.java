package space.controlnet.ae2federation.ae2.processing.endpoint;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.networking.IGridNode;
import appeng.helpers.patternprovider.PatternProviderLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record NativeLocalProvider(PatternProviderLogic logic, IGridNode sourceNode, BlockPos providerPosition,
        Direction targetSide, GenericInternalInventory returnInventory) {
    public NativeLocalProvider {
        if (logic.getGrid() != sourceNode.getGrid() || logic.getReturnInv() != returnInventory) {
            throw new IllegalArgumentException("Local Provider context must identify its native logic, node, and return inventory");
        }
    }
}
