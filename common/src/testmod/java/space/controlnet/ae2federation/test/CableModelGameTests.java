package space.controlnet.ae2federation.test;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.router.CableVisualConnections;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;

@PrefixGameTestTemplate(false)
public final class CableModelGameTests {
    private static final BlockPos A = new BlockPos(1, 6, 9);

    private CableModelGameTests() {
    }

    /**
     * A straight Federation Cable tube draws its end face unless the cable on that end is a straight tube too: the
     * face covers the step where the tube meets a narrower dense arm, and between two tubes it would be a seam.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 100, required = true, manualOnly = true)
    public static void cableTubeEndsJoinOnlyStraightTubes(GameTestHelper helper) {
        for (int x = 0; x <= 5; x++) {
            for (var around : new BlockPos[] {A.east(x), A.east(x).above(), A.east(x).below(), A.east(x).north(),
                    A.east(x).south()}) {
                helper.setBlock(around, Blocks.AIR);
            }
        }
        // A and D end the row, B and C are straight tubes.
        for (int x = 0; x < 4; x++) helper.setBlock(A.east(x), RouterRegistration.FEDERATION_CABLE.get());
        var b = A.east(1);
        var c = A.east(2);
        var d = A.east(3);
        helper.assertTrue(!joins(helper, b, Direction.WEST) && joins(helper, b, Direction.EAST),
                "B must close its end toward the row's end and stay open toward the tube C");
        helper.assertTrue(joins(helper, c, Direction.WEST) && !joins(helper, c, Direction.EAST),
                "C must stay open toward the tube B and close its end toward the row's end");

        helper.setBlock(c.above(), RouterRegistration.FEDERATION_CABLE.get());
        helper.assertFalse(joins(helper, b, Direction.EAST), "B must close its end once C branches");

        helper.setBlock(c.above(), Blocks.AIR);
        helper.setBlock(d, RouterRegistration.ROUTER.get());
        helper.assertTrue(joins(helper, b, Direction.EAST) && !joins(helper, c, Direction.EAST),
                "C must close its end against a Router, which is no tube");
        PolicyEvidence.write("cabletubeendsjoinonlystraighttubes", 4, Map.of("tubeEnds", "artist-display"));
        helper.succeed();
    }

    private static boolean joins(GameTestHelper helper, BlockPos cable, Direction side) {
        return CableVisualConnections.joins(CableVisualConnections.model(helper.getLevel(), helper.absolutePos(cable)),
                side);
    }
}
