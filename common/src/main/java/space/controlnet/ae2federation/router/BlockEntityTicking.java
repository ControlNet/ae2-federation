package space.controlnet.ae2federation.router;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Whether a block entity's ticker would run now, for the blocks whose work runs on a scheduler instead of a ticker. */
final class BlockEntityTicking {
    private BlockEntityTicking() {
    }

    /**
     * The checks Minecraft makes before it ticks a block entity ({@code Level.tickBlockEntities} and the chunk's
     * ticking wrapper): the tick rate runs normally, the chunk ticks blocks, the position is inside the world border,
     * and the chunk is a block-ticking chunk with its entities loaded.
     */
    static boolean ticksAt(ServerLevel level, BlockPos position) {
        if (!level.tickRateManager().runsNormally() || !level.shouldTickBlocksAt(position)
                || !level.getWorldBorder().isWithinBounds(position)) {
            return false;
        }
        var chunk = level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(position.getX()),
                SectionPos.blockToSectionCoord(position.getZ()));
        return chunk != null && chunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING)
                && level.areEntitiesLoaded(ChunkPos.asLong(position));
    }
}
