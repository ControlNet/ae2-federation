package space.controlnet.ae2federation.router;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block a Federation Cable links to through some of its faces. Read from the block state alone, so the server links
 * and the client draws an arm through exactly the same faces ({@link CableVisualConnections}).
 */
public interface CableLinkedBlock {
    /** Whether a Federation Cable against {@code face} of a block in {@code state} links to it. */
    boolean linksCable(BlockState state, Direction face);
}
