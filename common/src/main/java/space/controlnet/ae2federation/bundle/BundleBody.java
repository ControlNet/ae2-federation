package space.controlnet.ae2federation.bundle;

import net.minecraft.world.level.block.state.BlockState;

/**
 * A block, other than a Router-like device, that a bundle may link to through a whole contact: it says whether it
 * belongs to a formed body and which of its sides join that body, as a formed device's state does.
 */
public interface BundleBody {
    /** Whether {@code state} belongs to a formed body. */
    boolean bodyFormed(BlockState state);

    /** The sides on which {@code state}'s neighbour belongs to the same body, one bit per 3D data value. */
    int bodyConnections(BlockState state);
}
