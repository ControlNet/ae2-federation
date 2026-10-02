package space.controlnet.ae2federation.test.world;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Unloads and reloads the block entities of a region in one tick, the way a chunk unload and a later load treat them:
 * each is saved, told its chunk unloaded and removed without its block being broken (AE2 cancels a crafting CPU's job
 * only when the block is broken), then a new block entity is loaded from the saved data. When every node of a Grid is
 * in the region, AE2 builds the Grid and its services anew, as after a restart.
 */
public final class BlockEntityReload {
    private BlockEntityReload() {
    }

    /**
     * @param from one corner of the region, relative to the test
     * @param to the opposite corner, relative to the test
     * @return each reloaded block entity's saved data, by relative position
     */
    public static Map<BlockPos, CompoundTag> reload(GameTestHelper helper, BlockPos from, BlockPos to) {
        var level = helper.getLevel();
        var registries = level.registryAccess();
        var saved = new LinkedHashMap<BlockPos, CompoundTag>();
        for (var relative : BlockPos.betweenClosed(from, to)) {
            var entity = level.getBlockEntity(helper.absolutePos(relative));
            if (entity != null) saved.put(relative.immutable(), entity.saveWithFullMetadata(registries));
        }
        for (var relative : saved.keySet()) {
            var absolute = helper.absolutePos(relative);
            level.getBlockEntity(absolute).onChunkUnloaded();
            level.removeBlockEntity(absolute);
        }
        saved.forEach((relative, data) -> {
            var absolute = helper.absolutePos(relative);
            var entity = BlockEntity.loadStatic(absolute, level.getBlockState(absolute), data, registries);
            helper.assertTrue(entity != null, "A saved block entity must load again at " + relative);
            level.setBlockEntity(entity);
        });
        return saved;
    }
}
