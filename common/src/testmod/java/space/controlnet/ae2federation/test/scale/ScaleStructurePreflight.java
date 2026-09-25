package space.controlnet.ae2federation.test.scale;

import java.util.Set;
import java.util.TreeSet;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ScaleStructurePreflight {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScaleStructurePreflight.class);

    private ScaleStructurePreflight() {
    }

    public static void verify(GameTestHelper helper) {
        var bounds = helper.getBounds();
        helper.assertTrue(bounds.getXsize() == 36 && bounds.getYsize() == 8 && bounds.getZsize() == 36,
                "Large structure bounds must be 36x8x36, actual=" + bounds);
        var origin = helper.absolutePos(BlockPos.ZERO);
        helper.assertTrue(bounds.minX == origin.getX() && bounds.minY == origin.getY() + 1
                        && bounds.minZ == origin.getZ() && bounds.maxX == origin.getX() + 36
                        && bounds.maxY == origin.getY() + 9 && bounds.maxZ == origin.getZ() + 36,
                "Large structure placed origin/bounds mismatch: origin=" + origin + " bounds=" + bounds);

        Set<Long> chunks = new TreeSet<>();
        int checked = 0;
        for (int x = 2; x <= 32; x++) {
            checked += check(helper, new BlockPos(x, 2, 2), chunks);
        }
        checked += check(helper, new BlockPos(2, 1, 2), chunks);
        checked += check(helper, new BlockPos(3, 3, 2), chunks);
        checked += check(helper, new BlockPos(4, 2, 3), chunks);
        for (int pod = 0; pod < 16; pod++) {
            int centerX = 6 + 8 * (pod % 4);
            int centerZ = 5 + 8 * (pod / 4);
            for (int x = 2; x <= centerX; x++) {
                checked += check(helper, new BlockPos(x, 2, centerZ - 2), chunks);
            }
            for (int z = centerZ - 2; z <= centerZ; z++) {
                checked += check(helper, new BlockPos(centerX, 2, z), chunks);
            }
            int[][] devices = {
                    {0, 0, 0}, {-1, 0, 0}, {1, 0, 0}, {2, 0, 0},
                    {2, 0, 1}, {3, 0, 1}, {2, -1, 0}, {0, 0, -1}
            };
            for (int[] offset : devices) {
                checked += check(helper, new BlockPos(centerX + offset[0], 2 + offset[1], centerZ + offset[2]),
                        chunks);
            }
        }
        Set<Long> placedChunks = new TreeSet<>();
        for (int chunkX = Math.floorDiv((int) bounds.minX, 16);
                chunkX <= Math.floorDiv((int) bounds.maxX - 1, 16); chunkX++) {
            for (int chunkZ = Math.floorDiv((int) bounds.minZ, 16);
                    chunkZ <= Math.floorDiv((int) bounds.maxZ - 1, 16); chunkZ++) {
                placedChunks.add(ChunkPos.asLong(chunkX, chunkZ));
            }
        }
        helper.assertTrue(placedChunks.containsAll(chunks), "Candidate chunks must belong to placed structure bounds");
        LOGGER.info("AE2F_SCALE_STRUCTURE_PREFLIGHT origin={} bounds={} checked={} candidateChunks={} placedChunks={}",
                origin, bounds, checked, chunks.stream().map(ChunkPos::new).map(ChunkPos::toString).toList(),
                placedChunks.stream().map(ChunkPos::new).map(ChunkPos::toString).toList());
    }

    private static int check(GameTestHelper helper, BlockPos relative, Set<Long> chunks) {
        var absolute = helper.absolutePos(relative);
        var bounds = helper.getBounds();
        helper.assertTrue(bounds.contains(absolute.getX() + 0.5, absolute.getY() + 0.5, absolute.getZ() + 0.5),
                "Large structure candidate outside placed bounds: relative=" + relative + " absolute=" + absolute);
        var level = helper.getLevel();
        helper.assertTrue(level.isLoaded(absolute),
                "Large structure candidate unloaded: relative=" + relative + " absolute=" + absolute);
        var blockEntity = level.getBlockEntity(absolute);
        helper.assertTrue(blockEntity == null,
                "Large structure candidate occupied BE: relative=" + relative + " absolute=" + absolute
                        + " BE=" + blockEntity);
        var block = level.getBlockState(absolute);
        helper.assertTrue(block.isAir() || block.is(Blocks.BARRIER),
                "Large structure candidate not air/barrier: relative=" + relative + " absolute=" + absolute
                        + " block=" + block);
        chunks.add(new ChunkPos(absolute).toLong());
        return 1;
    }
}
