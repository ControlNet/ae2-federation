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
     * Federation Cables join each other at the core's full width, so a cable's arm narrows only toward a Router,
     * Provider or Endpoint front; a straight tube needs a cable on both ends.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 100, required = true, manualOnly = true)
    public static void cableNecksOnlyAtDevices(GameTestHelper helper) {
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
        helper.assertTrue(kind(helper, A, Direction.EAST) == CableVisualConnections.CABLE,
                "A must join the cable B at full width");
        helper.assertTrue(straight(helper, b) && straight(helper, c), "B and C must be straight tubes");

        helper.setBlock(d, RouterRegistration.ROUTER.get());
        helper.assertTrue(kind(helper, c, Direction.EAST) == CableVisualConnections.DENSE,
                "C must narrow toward the Router");
        helper.assertTrue(straight(helper, b) && !straight(helper, c),
                "B must stay a tube and C, beside the Router, must become a core");
        PolicyEvidence.write("cablenecksonlyatdevices", 4, Map.of("necks", "devices-only"));
        helper.succeed();
    }

    private static int kind(GameTestHelper helper, BlockPos cable, Direction side) {
        return CableVisualConnections.kind(CableVisualConnections.connections(helper.getLevel(),
                helper.absolutePos(cable)), side);
    }

    private static boolean straight(GameTestHelper helper, BlockPos cable) {
        return CableVisualConnections.straight(CableVisualConnections.connections(helper.getLevel(),
                helper.absolutePos(cable)));
    }
}
