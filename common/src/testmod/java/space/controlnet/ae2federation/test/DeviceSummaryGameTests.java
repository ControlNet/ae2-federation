package space.controlnet.ae2federation.test;

import java.util.Map;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.router.DeviceStructure;
import space.controlnet.ae2federation.router.DeviceSummary;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.test.router.RouterEvidence;
import space.controlnet.ae2federation.test.router.RouterFixtures;

/** What Jade shows on a Router or Switch, read in a real level: its body, and the networks a Switch body attaches. */
@PrefixGameTestTemplate(false)
public final class DeviceSummaryGameTests {
    private static final BlockPos BASE = new BlockPos(2, 2, 2);

    private DeviceSummaryGameTests() {
    }

    /**
     * A 2x1x1 Switch with an ME chest on its west face, one on its east face and a third network of two chests on top
     * of both blocks, which counts once; a lone Router, which forms a 1x1x1 body; and an L of three Routers, which does
     * not form.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void deviceSummaryShowsBodiesAndNetworks(GameTestHelper helper) {
        BlockPos.betweenClosed(BASE, BASE.offset(6, 2, 6)).forEach(position -> helper.setBlock(position, Blocks.AIR));
        var fixtures = new RouterFixtures(helper);
        var west = BASE.offset(1, 0, 1);
        var east = west.east();
        fixtures.placeNativeDevice(west, Direction.WEST);
        fixtures.placeNativeDevice(east, Direction.EAST);
        fixtures.placeNativeDevice(west, Direction.UP);
        fixtures.placeNativeDevice(east, Direction.UP);
        fixtures.placeSwitch(west);
        fixtures.placeSwitch(east);
        var lone = BASE.offset(1, 0, 5);
        fixtures.placeRouter(lone);
        var corner = BASE.offset(4, 0, 4);
        for (var cell : new BlockPos[] {corner, corner.east(), corner.south()}) {
            helper.setBlock(cell, RouterRegistration.ROUTER.get());
        }
        helper.succeedWhen(() -> {
            var level = helper.getLevel();
            var body = DeviceSummary.of(level, helper.absolutePos(west));
            helper.assertTrue(body.body() instanceof DeviceStructure.Valid valid
                    && valid.max().subtract(valid.min()).equals(new BlockPos(1, 0, 0)), "The Switch forms a 2x1x1 body");
            helper.assertValueEqual(body.networks(), OptionalInt.of(3),
                    "The body attaches three networks, the one on both blocks once");
            helper.assertValueEqual(DeviceSummary.of(level, helper.absolutePos(east)).networks(), OptionalInt.of(3),
                    "Either block of the body reads the whole body");
            var single = DeviceSummary.of(level, helper.absolutePos(lone));
            helper.assertTrue(single.body() instanceof DeviceStructure.Valid valid && valid.min().equals(valid.max()),
                    "A lone Router is a 1x1x1 body");
            helper.assertValueEqual(single.networks(), OptionalInt.empty(), "A Router attaches no network");
            helper.assertValueEqual(DeviceSummary.of(level, helper.absolutePos(corner)).body(),
                    new DeviceStructure.Invalid(DeviceStructure.Reason.TOUCHING),
                    "An L of Routers does not form: from its corner, the third block touches a 2x1x1 box");
            RouterEvidence.write("devicesummaryshowsbodiesandnetworks", 6, Map.of("networks",
                    String.valueOf(body.networks().orElse(-1))));
            fixtures.close();
        });
    }
}
