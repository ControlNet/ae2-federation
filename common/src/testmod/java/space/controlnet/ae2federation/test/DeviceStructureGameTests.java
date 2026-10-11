package space.controlnet.ae2federation.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.port.RouterPortBinding;
import space.controlnet.ae2federation.domain.port.RouterPortKind;
import space.controlnet.ae2federation.router.RouterBlock;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.test.router.RouterEvidence;
import space.controlnet.ae2federation.test.router.RouterFixtures;

/**
 * Router and Switch bodies form only as solid cuboids: each block's connection properties name exactly its
 * neighbours inside the validated cuboid, and every block of an incomplete, hollow, branched or protruding candidate
 * has none. Forming changes only blockstates, so the faces' ports behave as before.
 */
@PrefixGameTestTemplate(false)
public final class DeviceStructureGameTests {
    private static final BlockPos BASE = new BlockPos(2, 2, 2);

    private DeviceStructureGameTests() {
    }

    /** One step of a placement script: change some blocks, then wait until {@code expectation} holds. */
    private record Step(Runnable change, Runnable expectation) {
    }

    private static void run(GameTestHelper helper, List<Step> steps, Runnable done) {
        var index = new int[] {0};
        var started = new boolean[] {false};
        helper.succeedWhen(() -> {
            while (index[0] < steps.size()) {
                var step = steps.get(index[0]);
                if (!started[0]) {
                    step.change().run();
                    started[0] = true;
                    helper.assertTrue(false, "Waiting for step " + index[0]);
                }
                step.expectation().run();
                index[0]++;
                started[0] = false;
            }
            done.run();
        });
    }

    private static List<BlockPos> box(BlockPos min, int sx, int sy, int sz) {
        var cells = new ArrayList<BlockPos>();
        for (int x = 0; x < sx; x++) for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) cells.add(min.offset(x, y, z));
        return cells;
    }

    private static void place(GameTestHelper helper, List<BlockPos> cells, Block block) {
        cells.forEach(cell -> helper.setBlock(cell, block));
    }

    private static int connections(GameTestHelper helper, BlockPos cell) {
        return RouterBlock.connections(helper.getBlockState(cell));
    }

    /** Every block in the box names exactly its neighbours inside the box. */
    private static void assertFormed(GameTestHelper helper, BlockPos min, int sx, int sy, int sz, String shape) {
        var max = min.offset(sx - 1, sy - 1, sz - 1);
        for (var cell : box(min, sx, sy, sz)) {
            int expected = 0;
            for (var side : Direction.values()) {
                var neighbor = cell.relative(side);
                if (neighbor.getX() >= min.getX() && neighbor.getX() <= max.getX() && neighbor.getY() >= min.getY()
                        && neighbor.getY() <= max.getY() && neighbor.getZ() >= min.getZ() && neighbor.getZ() <= max.getZ()) {
                    expected |= 1 << side.get3DDataValue();
                }
            }
            helper.assertValueEqual(connections(helper, cell), expected, shape + " member " + cell + " connections");
        }
    }

    private static void assertUnformed(GameTestHelper helper, List<BlockPos> cells, String shape) {
        for (var cell : cells) {
            helper.assertValueEqual(connections(helper, cell), 0, shape + " member " + cell + " must stay unformed");
        }
    }

    /**
     * Placing, extending, breaking and repairing a Switch body: it forms as a 2x2x2 cube, one extra block against
     * it unforms the whole candidate until removed, a missing corner unforms the rest, and a Router touching it stays
     * a separate standalone device.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void deviceStructureLifecycle(GameTestHelper helper) {
        var cube = box(BASE, 2, 2, 2);
        var protrusion = BASE.offset(2, 0, 0);
        var corner = BASE.offset(1, 1, 1);
        var withProtrusion = new ArrayList<>(cube);
        withProtrusion.add(protrusion);
        var withoutCorner = new ArrayList<>(cube);
        withoutCorner.remove(corner);
        var router = BASE.offset(0, 0, -1);
        var block = RouterRegistration.SWITCH.get();
        run(helper, List.of(
                new Step(() -> place(helper, cube, block), () -> assertFormed(helper, BASE, 2, 2, 2, "2x2x2")),
                new Step(() -> helper.setBlock(protrusion, block), () -> assertUnformed(helper, withProtrusion, "protrusion")),
                new Step(() -> helper.setBlock(protrusion, Blocks.AIR), () -> assertFormed(helper, BASE, 2, 2, 2, "repaired")),
                new Step(() -> helper.setBlock(corner, Blocks.AIR), () -> assertUnformed(helper, withoutCorner, "missing corner")),
                new Step(() -> helper.setBlock(corner, block), () -> assertFormed(helper, BASE, 2, 2, 2, "refilled")),
                new Step(() -> helper.setBlock(router, RouterRegistration.ROUTER.get()), () -> {
                    assertFormed(helper, BASE, 2, 2, 2, "beside a Router");
                    assertUnformed(helper, List.of(router), "different type");
                })), () -> {
                    RouterEvidence.write("devicestructurelifecycle", 6, Map.of(
                            "formed", "2x2x2", "protrusion", "unformed", "missingCorner", "unformed",
                            "repaired", "formed", "mixedTypes", "separate"));
                });
    }

    /**
     * Thin, flat and long bodies form with their true proportions; hollow, L-shaped and diagonal arrangements do not
     * join, and blocks touching only at an edge or corner stay separate single devices.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void deviceStructureShapes(GameTestHelper helper) {
        var router = RouterRegistration.ROUTER.get();
        var block = RouterRegistration.SWITCH.get();
        var line = BASE.offset(0, 0, 0);
        var plate = BASE.offset(3, 0, 0);
        var hollow = BASE.offset(7, 0, 0);
        var hollowCells = box(hollow, 3, 3, 3);
        hollowCells.remove(hollow.offset(1, 1, 1));
        var l = List.of(BASE.offset(0, 0, 6), BASE.offset(1, 0, 6), BASE.offset(0, 1, 6));
        var diagonal = List.of(BASE.offset(3, 0, 6), BASE.offset(4, 1, 6), BASE.offset(5, 2, 7));
        run(helper, List.of(
                new Step(() -> {
                    place(helper, box(line, 1, 1, 4), router);
                    place(helper, box(plate, 1, 3, 3), block);
                    place(helper, hollowCells, block);
                    place(helper, l, router);
                    place(helper, diagonal, block);
                }, () -> {
                    assertFormed(helper, line, 1, 1, 4, "1x1x4 Router");
                    assertFormed(helper, plate, 1, 3, 3, "1x3x3 Switch");
                    assertUnformed(helper, hollowCells, "hollow 3x3x3");
                    assertUnformed(helper, l, "L");
                    assertUnformed(helper, diagonal, "diagonal");
                }),
                // Removing one end of the line leaves a valid 1x1x3; filling the hollow forms the full cube.
                new Step(() -> {
                    helper.setBlock(line.offset(0, 0, 3), Blocks.AIR);
                    helper.setBlock(hollow.offset(1, 1, 1), block);
                }, () -> {
                    assertFormed(helper, line, 1, 1, 3, "shortened line");
                    assertFormed(helper, hollow, 3, 3, 3, "filled 3x3x3");
                })), () -> {
                    RouterEvidence.write("devicestructureshapes", 7, Map.of(
                            "line", "1x1x4", "plate", "1x3x3", "hollow", "unformed", "l", "unformed",
                            "diagonal", "separate", "shortened", "1x1x3", "filled", "3x3x3"));
                });
    }

    /**
     * A formed body keeps its faces' behaviour: a 2x1x1 Switch with an ME chest at each end keeps the two networks
     * separate native Grids joined only through Federation inside the body, and a 2x1x1 Router exposes no grid node.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void deviceStructureKeepsPorts(GameTestHelper helper) {
        var fixtures = new RouterFixtures(helper);
        var west = BASE.offset(1, 0, 1);
        var east = west.east();
        fixtures.placeNativeDevice(west, Direction.WEST);
        fixtures.placeNativeDevice(east, Direction.EAST);
        var first = fixtures.placeSwitch(west);
        var second = fixtures.placeSwitch(east);
        var routerWest = BASE.offset(1, 0, 5);
        var routerA = fixtures.placeRouter(routerWest);
        var routerB = fixtures.placeRouter(routerWest.east());
        var registry = FederationDomainRegistryAccess.get(helper.getLevel());
        helper.succeedWhen(() -> {
            assertFormed(helper, west, 2, 1, 1, "2x1x1 Switch");
            assertFormed(helper, routerWest, 2, 1, 1, "2x1x1 Router");
            helper.assertValueEqual(first.binding(Direction.EAST).kind(), RouterPortKind.FEDERATION,
                    "The body's inner face links through Federation");
            helper.assertValueEqual(second.binding(Direction.WEST).kind(), RouterPortKind.FEDERATION,
                    "The body's inner face links through Federation");
            helper.assertTrue(first.boundaryNode(Direction.EAST).getConnections().isEmpty()
                    && second.boundaryNode(Direction.WEST).getConnections().isEmpty(),
                    "Inner faces must not join a native Grid");
            var left = first.binding(Direction.WEST);
            var right = second.binding(Direction.EAST);
            helper.assertTrue(left instanceof RouterPortBinding.Native && right instanceof RouterPortBinding.Native,
                    "Both outer faces attach their ME chest");
            var leftGrid = ((RouterPortBinding.Native) left).attachment().grid();
            var rightGrid = ((RouterPortBinding.Native) right).attachment().grid();
            helper.assertTrue(leftGrid != rightGrid, "The two ME networks stay separate Grids");
            var leftId = FederationDomainRegistryAccess.confirmedNetworkId(leftGrid);
            var rightId = FederationDomainRegistryAccess.confirmedNetworkId(rightGrid);
            helper.assertTrue(leftId.isPresent() && rightId.isPresent(), "Waiting for native identities");
            helper.assertTrue(!registry.federationdomainsFor(leftId.get()).isEmpty()
                    && registry.federationdomainsFor(leftId.get()).equals(registry.federationdomainsFor(rightId.get())),
                    "Both networks share the body's Federation domain");
            var noNodes = true;
            for (var face : Direction.values()) noNodes &= routerA.getGridNode(face) == null && routerB.getGridNode(face) == null;
            helper.assertTrue(noNodes, "A formed Router exposes no grid node");
            helper.assertValueEqual(routerA.binding(Direction.EAST).kind(), RouterPortKind.FEDERATION,
                    "The Router body's inner face links through Federation");
            RouterEvidence.write("devicestructurekeepsports", 9, Map.of(
                    "switchFormed", "2x1x1", "routerFormed", "2x1x1", "innerFaces", "federation",
                    "nativeJoin", "false", "separateGrids", "true", "sharedDomain", "true", "routerNodes", "0"));
            fixtures.close();
        });
    }
}
