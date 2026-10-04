package space.controlnet.ae2federation.ae2.processing.endpoint;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.helpers.patternprovider.PatternProviderLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The pattern provider that runs a Local Endpoint: {@code logic} is the crafting provider its grid node offers, AE2's
 * {@link PatternProviderLogic} for AE2's providers and their subclasses, an addon's own object otherwise.
 * {@code returnInventory} is the return inventory the provider offers to blocks around it.
 */
public record NativeLocalProvider(ICraftingProvider logic, IGridNode sourceNode, BlockPos providerPosition,
        Direction targetSide, GenericInternalInventory returnInventory) {
    public NativeLocalProvider {
        var identified = returnInventory != null && (logic instanceof PatternProviderLogic nativeLogic
                ? nativeLogic.getGrid() == sourceNode.getGrid()
                : sourceNode.getService(ICraftingProvider.class) == logic);
        if (!identified) {
            throw new IllegalArgumentException("Local Provider context must identify its native logic, node, and return inventory");
        }
    }
}
