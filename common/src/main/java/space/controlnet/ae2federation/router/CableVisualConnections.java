package space.controlnet.ae2federation.router;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlock;

/** Read-only projection of the stateless Federation port registrations onto a chunk snapshot. */
public final class CableVisualConnections {
    public static final Direction[] DIRECTIONS = {
            Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN, Direction.SOUTH, Direction.NORTH
    };

    private CableVisualConnections() {
    }

    public static int mask(BlockGetter level, BlockPos position) {
        int mask = 0;
        for (int bit = 0; bit < DIRECTIONS.length; bit++) {
            var direction = DIRECTIONS[bit];
            var neighbor = level.getBlockState(position.relative(direction));
            var block = neighbor.getBlock();
            // Endpoint and Bridge do not expose FederationPortCapability and must not grow a cable arm.
            if (block instanceof FederationCableBlock || block instanceof RouterBlock
                    || block instanceof FederationPatternProviderBlock
                    && neighbor.getValue(BlockStateProperties.FACING) == direction.getOpposite()) {
                mask |= 1 << bit;
            }
        }
        return mask;
    }
}
