package space.controlnet.ae2federation.router;

import appeng.api.parts.IPartHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import space.controlnet.ae2federation.p2p.FederationP2PTunnelPart;
import space.controlnet.ae2federation.processing.endpoint.EndpointBlock;
import space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlock;

/**
 * Read-only projection of the stateless Federation port registrations onto a chunk snapshot.
 *
 * <p>A Federation Cable renders as AE2's dense cable, and each side connects as AE2's {@code CableBusContainer} picks
 * a dense cable's connection: the smaller of the cable's type and the neighbour's. Federation Cable and Router are
 * dense, as AE2's Controller is; a Provider or Endpoint front is smart, as AE2's Pattern Provider and Interface are,
 * so the connection shrinks to a covered one with a cap against the machine; a Federation P2P tunnel's front is a
 * part on another cable bus, which AE2 joins with a covered connection and no cap.
 */
public final class CableVisualConnections {
    public static final Direction[] DIRECTIONS = {
            Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN, Direction.SOUTH, Direction.NORTH
    };
    /** Not connected on that side. */
    public static final int NONE = 0;
    /** A dense connection: another Federation Cable or a Router. */
    public static final int DENSE = 1;
    /** A covered connection ending in a cap against a machine: a Provider or Endpoint front. */
    public static final int COVERED_CAP = 2;
    /** A covered connection to a part on another cable bus: a Federation P2P tunnel's front. */
    public static final int COVERED = 3;
    /** How many distinct connection sets there are: two bits per side. */
    public static final int COUNT = 1 << (2 * 6);

    private CableVisualConnections() {
    }

    /** The cable's six sides as two bits each, in {@link #DIRECTIONS} order. */
    public static int connections(BlockGetter level, BlockPos position) {
        int connections = 0;
        for (var direction : DIRECTIONS) {
            connections = with(connections, direction, kind(level, position, direction));
        }
        return connections;
    }

    /** Which sides connect, one bit per side in {@link #DIRECTIONS} order. */
    public static int mask(BlockGetter level, BlockPos position) {
        return maskOf(connections(level, position));
    }

    public static int maskOf(int connections) {
        int mask = 0;
        for (int bit = 0; bit < DIRECTIONS.length; bit++) {
            if (((connections >> (bit * 2)) & 3) != NONE) mask |= 1 << bit;
        }
        return mask;
    }

    public static int kind(int connections, Direction side) {
        return (connections >> (bit(side) * 2)) & 3;
    }

    public static int with(int connections, Direction side, int kind) {
        int shift = bit(side) * 2;
        return connections & ~(3 << shift) | kind << shift;
    }

    /**
     * Whether the cable is one straight dense tube: exactly two opposite dense connections, as AE2's
     * {@code CableBusBakedModel.isStraightLine} asks of a cable with no attachments.
     */
    public static boolean straight(int connections) {
        int mask = maskOf(connections);
        if (Integer.bitCount(mask) != 2) return false;
        int first = Integer.numberOfTrailingZeros(mask);
        if ((first & 1) != 0 || (mask & (2 << first)) == 0) return false;
        return kind(connections, DIRECTIONS[first]) == DENSE && kind(connections, DIRECTIONS[first + 1]) == DENSE;
    }

    private static int bit(Direction side) {
        return switch (side) {
            case EAST -> 0;
            case WEST -> 1;
            case UP -> 2;
            case DOWN -> 3;
            case SOUTH -> 4;
            case NORTH -> 5;
        };
    }

    private static int kind(BlockGetter level, BlockPos position, Direction direction) {
        var neighbor = level.getBlockState(position.relative(direction));
        var block = neighbor.getBlock();
        if (block instanceof FederationCableBlock || block instanceof RouterBlock) return DENSE;
        // Provider and Endpoint expose FederationPortCapability on their front only; the Bridge exposes none.
        if ((block instanceof FederationPatternProviderBlock || block instanceof EndpointBlock)
                && neighbor.getValue(BlockStateProperties.FACING) == direction.getOpposite()) {
            return COVERED_CAP;
        }
        // A Federation P2P tunnel's front is its port.
        if (neighbor.hasBlockEntity()
                && level.getBlockEntity(position.relative(direction)) instanceof IPartHost host
                && host.getPart(direction.getOpposite()) instanceof FederationP2PTunnelPart) {
            return COVERED;
        }
        return NONE;
    }
}
