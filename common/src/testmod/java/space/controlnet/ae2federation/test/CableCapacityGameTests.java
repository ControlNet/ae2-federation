package space.controlnet.ae2federation.test;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.domain.CableCapacity;
import space.controlnet.ae2federation.domain.CableCapacityCounter.Demand;
import space.controlnet.ae2federation.domain.CableCapacityCounter.Scope;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.test.router.RouterEvidence;
import space.controlnet.ae2federation.test.router.RouterFixtures;

@PrefixGameTestTemplate(false)
public final class CableCapacityGameTests {
    private static final BlockPos CENTER = new BlockPos(6, 6, 6);

    private CableCapacityGameTests() {
    }

    /**
     * One line along x, within three blocks of the centre like the other harness tests: Switch (two chests) - cable -
     * Router - cable - Router - cable - Endpoint, and a Switch (one chest) on top of the middle cable. The outer cables
     * touch one Router and count their own region; the trunk touches both and counts the whole domain: three networks
     * and the Endpoint. (A Switch passing a region through is covered by {@code CableCapacityCounterTest}.)
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void cableCapacityCountsRegions(GameTestHelper helper) {
        // Batched tests share the level, and an ME device left beside a Switch would count as one more network.
        BlockPos.betweenClosed(CENTER.offset(-4, -1, -1), CENTER.offset(4, 2, 1))
                .forEach(position -> helper.setBlock(position, Blocks.AIR));
        var fixtures = new RouterFixtures(helper);
        var leftSwitch = CENTER.west(3);
        fixtures.placeSwitch(leftSwitch);
        fixtures.placeNativeDevice(leftSwitch, Direction.UP);
        fixtures.placeNativeDevice(leftSwitch, Direction.NORTH);
        for (var cable : new BlockPos[] {CENTER.west(2), CENTER, CENTER.east(2)}) {
            fixtures.placeFederationCable(cable);
        }
        fixtures.placeRouter(CENTER.west());
        fixtures.placeRouter(CENTER.east());
        fixtures.placeSwitch(CENTER.above());
        fixtures.placeNativeDevice(CENTER.above(), Direction.UP);
        helper.setBlock(CENTER.east(3), ProcessingRegistration.ENDPOINT.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.WEST));
        helper.succeedWhen(() -> {
            var left = demand(helper, CENTER.west(2));
            var trunk = demand(helper, CENTER);
            var right = demand(helper, CENTER.east(2));
            helper.assertValueEqual(left, new Demand(Scope.LOCAL, 2), "The left region counts its Switch's two networks");
            helper.assertValueEqual(trunk, new Demand(Scope.DOMAIN, 4),
                    "The trunk between two Routers counts the whole domain");
            helper.assertValueEqual(right, new Demand(Scope.LOCAL, 1), "The right region counts its Endpoint");
            RouterEvidence.write("cablecapacitycountsregions", 3, Map.of(
                    "left", String.valueOf(left), "trunk", String.valueOf(trunk), "right", String.valueOf(right)));
            fixtures.close();
        });
    }

    private static Demand demand(GameTestHelper helper, BlockPos relative) {
        var demand = CableCapacity.demand(helper.getLevel(), helper.absolutePos(relative));
        helper.assertTrue(demand.isPresent(), "Waiting for the cable at " + relative + " to join a domain");
        return demand.get();
    }
}
