package space.controlnet.ae2federation.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The same GameTest, addressed from another origin, so two Phase 4 bases and a survey area can stand side by side
 * outside the test's own template. Every helper method resolves positions through {@link #absolutePos}.
 */
final class PlaythroughOffsetHelper extends GameTestHelper {
    private final BlockPos offset;

    PlaythroughOffsetHelper(GameTestHelper helper, BlockPos offset) {
        super(helper.testInfo);
        this.offset = offset;
    }

    /** A helper whose origin is the absolute position {@code origin}. */
    static PlaythroughOffsetHelper at(GameTestHelper helper, BlockPos origin) {
        return new PlaythroughOffsetHelper(helper, origin.subtract(helper.absolutePos(BlockPos.ZERO)));
    }

    @Override public BlockPos absolutePos(BlockPos pos) { return super.absolutePos(pos).offset(offset); }

    @Override public BlockPos relativePos(BlockPos pos) { return super.relativePos(pos.subtract(offset)); }
}
