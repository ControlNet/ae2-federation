package space.controlnet.ae2federation.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.beam.BeamObstruction;
import space.controlnet.ae2federation.test.perf.PerfMeasure;

/**
 * TEST-ONLY, manual-only benchmark for the planned Beam Former's path check (plan
 * {@code .omo/plans/beam-former.md}, D12): the cost of checking a 3x3 x 1024 beam volume in a real level, read
 * the way the link service reads it (loaded chunks only, all-air sections skipped whole), with the real
 * {@link BeamObstruction} rule (D4).
 */
@PrefixGameTestTemplate(false)
public final class BeamScanBenchmarkGameTests {
    private static final int LENGTH = Integer.getInteger("ae2federation.perf.beamLength", 1024);
    private static final int SIDE = 3;

    private BeamScanBenchmarkGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1200, required = true, manualOnly = true)
    public static void perfBeamScan(GameTestHelper helper) {
        var perf = new PerfMeasure("perfbeamscan");
        var level = helper.getLevel();
        var origin = helper.absolutePos(BlockPos.ZERO);
        // One 3x3 x LENGTH volume along +x per case, each in its own 16-block section row high above the floor.
        var base = new BlockPos(origin.getX() + 32, (origin.getY() & ~15) + 64, (origin.getZ() & ~15) + 36);
        var openSky = base;
        var airInMixedSections = base.above(16);
        var glass = base.above(32);
        var stoneAtFarEnd = base.above(48);
        for (var x = base.getX() >> 4; x <= (base.getX() + LENGTH) >> 4; x++) {
            level.getChunk(x, base.getZ() >> 4);
        }
        // A block beside the volume in every section, so no section of this case is all air.
        for (var x = 0; x < LENGTH; x += 16) {
            level.setBlock(airInMixedSections.offset(x, 0, SIDE + 1), Blocks.STONE.defaultBlockState(), 2);
        }
        fill(level, glass, Blocks.GLASS.defaultBlockState());
        fill(level, stoneAtFarEnd, Blocks.GLASS.defaultBlockState());
        level.setBlock(stoneAtFarEnd.offset(LENGTH - 1, SIDE - 1, SIDE - 1), Blocks.STONE.defaultBlockState(), 2);

        helper.assertTrue(scan(level, openSky) == -1, "open sky must be clear");
        helper.assertTrue(scan(level, airInMixedSections) == -1, "air must be clear");
        helper.assertTrue(scan(level, glass) == -1, "glass must pass");
        helper.assertTrue(scan(level, stoneAtFarEnd) == LENGTH * SIDE * SIDE - 1, "the far stone must block");

        var volume = (double) LENGTH * SIDE * SIDE;
        var sky = perf.nanosPerOp("openSkyVolume", 20_000, 300, () -> scan(level, openSky));
        var air = perf.nanosPerOp("airVolume", 2_000, 500, () -> scan(level, airInMixedSections));
        var full = perf.nanosPerOp("glassVolume", 2_000, 500, () -> scan(level, glass));
        perf.nanosPerOp("stoneAtFarEndVolume", 2_000, 500, () -> scan(level, stoneAtFarEnd));
        perf.record("airPerBlock", air / volume, "ns/block");
        perf.record("glassPerBlock", full / volume, "ns/block");
        perf.record("openSkyPerBlock", sky / volume, "ns/block");
        perf.count("volumeBlocks", (long) volume);
        helper.succeed();
    }

    private static void fill(ServerLevel level, BlockPos start, BlockState state) {
        for (var x = 0; x < LENGTH; x++) {
            for (var y = 0; y < SIDE; y++) {
                for (var z = 0; z < SIDE; z++) {
                    level.setBlock(start.offset(x, y, z), state, 2);
                }
            }
        }
    }

    /** The index of the first blocking position in scan order, or -1 when the volume is clear. */
    private static int scan(ServerLevel level, BlockPos start) {
        var pos = new BlockPos.MutableBlockPos();
        var index = 0;
        for (var chunkX = start.getX() >> 4; chunkX <= (start.getX() + LENGTH - 1) >> 4; chunkX++) {
            var chunk = level.getChunkSource().getChunkNow(chunkX, start.getZ() >> 4);
            var fromX = Math.max(start.getX(), chunkX << 4);
            var toX = Math.min(start.getX() + LENGTH, (chunkX << 4) + 16);
            if (chunk == null) {
                index += (toX - fromX) * SIDE * SIDE;
                continue;
            }
            for (var x = fromX; x < toX; x++) {
                for (var y = start.getY(); y < start.getY() + SIDE; y++) {
                    var section = chunk.getSection(chunk.getSectionIndex(y));
                    if (section.hasOnlyAir()) {
                        index += SIDE;
                        continue;
                    }
                    for (var z = start.getZ(); z < start.getZ() + SIDE; z++) {
                        var state = section.getBlockState(x & 15, y & 15, z & 15);
                        if (BeamObstruction.blocks(state, chunk, pos.set(x, y, z))) return index;
                        index++;
                    }
                }
            }
        }
        return -1;
    }
}
