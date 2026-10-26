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
import space.controlnet.ae2federation.test.world.MockServerPlayers;

/**
 * Router and Switch bodies form only as solid cuboids: each block's connection properties name exactly its
 * neighbours inside the validated cuboid, and every block of an incomplete, hollow, branched or protruding candidate
 * is unformed and links nothing until the shape is fixed. A formed body's faces behave as single blocks' faces did.
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
            helper.assertTrue(RouterBlock.formed(helper.getBlockState(cell)), shape + " member " + cell + " must be formed");
        }
    }

    private static void assertUnformed(GameTestHelper helper, List<BlockPos> cells, String shape) {
        for (var cell : cells) {
            helper.assertValueEqual(connections(helper, cell), 0, shape + " member " + cell + " must stay unformed");
            helper.assertTrue(!RouterBlock.formed(helper.getBlockState(cell)), shape + " member " + cell + " must be unformed");
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
                    // A different type is never part of the body: the Router is its own formed single block.
                    helper.assertValueEqual(connections(helper, router), 0, "the Router's connections");
                    helper.assertTrue(RouterBlock.formed(helper.getBlockState(router)), "the Router forms alone");
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
                    for (var cell : diagonal) assertFormed(helper, cell, 1, 1, 1, "diagonal single");
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
     * Building on an unformed body: right-clicking an unformed Router, Switch or Beam Converter with a block in hand
     * places the block, as on any other block. Only an empty hand asks why the body has not formed.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void deviceStructurePlacesOnUnformed(GameTestHelper helper) {
        var routerL = List.of(BASE.offset(0, 0, 6), BASE.offset(1, 0, 6), BASE.offset(0, 1, 6));
        var switchL = List.of(BASE.offset(4, 0, 6), BASE.offset(5, 0, 6), BASE.offset(4, 1, 6));
        var converter = BASE.offset(8, 0, 6);
        place(helper, routerL, RouterRegistration.ROUTER.get());
        place(helper, switchL, RouterRegistration.SWITCH.get());
        helper.setBlock(converter, space.controlnet.ae2federation.power.PowerRegistration.BEAM_CONVERTER.get());
        var player = MockServerPlayers.inLevel(helper);
        var done = new boolean[1];
        helper.succeedWhen(() -> {
            if (!done[0]) {
                assertUnformed(helper, routerL, "Router L");
                assertUnformed(helper, switchL, "Switch L");
                helper.assertTrue(!RouterBlock.formed(helper.getBlockState(converter)), "a single converter is unformed");
                use(helper, player, routerL.get(1), RouterRegistration.ROUTER_ITEM.get().getDefaultInstance());
                use(helper, player, switchL.get(1), RouterRegistration.SWITCH_ITEM.get().getDefaultInstance());
                use(helper, player, converter,
                        space.controlnet.ae2federation.power.PowerRegistration.BEAM_CONVERTER_ITEM.get().getDefaultInstance());
                done[0] = true;
            }
            helper.assertTrue(helper.getBlockState(routerL.get(1).above()).is(RouterRegistration.ROUTER.get()),
                    "a Router placed on an unformed Router");
            helper.assertTrue(helper.getBlockState(switchL.get(1).above()).is(RouterRegistration.SWITCH.get()),
                    "a Switch placed on an unformed Switch");
            helper.assertTrue(helper.getBlockState(converter.above())
                    .is(space.controlnet.ae2federation.power.PowerRegistration.BEAM_CONVERTER.get()),
                    "a converter placed on an unformed converter");
            RouterEvidence.write("devicestructureplacesonunformed", 3, Map.of(
                    "router", "placed", "switch", "placed", "converter", "placed"));
        });
    }

    /** Right-clicks the top of {@code relative} holding {@code stack}, as a player does. */
    private static void use(GameTestHelper helper, net.minecraft.server.level.ServerPlayer player, BlockPos relative,
            net.minecraft.world.item.ItemStack stack) {
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        var absolute = helper.absolutePos(relative);
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(absolute)
                .relative(Direction.UP, 0.5), Direction.UP, absolute, false);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
                net.minecraft.world.InteractionHand.MAIN_HAND, hit);
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
            helper.assertTrue(first.boundaryNode(Direction.EAST) == null && second.boundaryNode(Direction.WEST) == null,
                    "Inner faces must have no node, so no native Grid");
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

    private static void assertIsolated(RouterFixtures fixtures, GameTestHelper helper, List<BlockPos> members) {
        for (var member : members) {
            var router = fixtures.router(member);
            helper.assertTrue(!fixtures.hasInternalNativeConnection(router), member + " must not join its own faces");
            for (var side : Direction.values()) {
                var neighbor = member.relative(side);
                if (members.contains(neighbor)) {
                    helper.assertTrue(router.boundaryNode(side) == null,
                            member + " must not join a native Grid with the body member on its " + side.getName());
                }
            }
        }
    }

    private static appeng.api.networking.IGrid chestGrid(RouterFixtures fixtures, BlockPos switchPosition, Direction face) {
        return fixtures.nativeDeviceNode(switchPosition, face).getGrid();
    }

    /**
     * An invalid candidate links nothing: a 2x1x1 Switch joins the ME chests at its ends in one domain; a third Switch
     * against its side unforms all three, every face disconnects and both networks leave the domain; removing it
     * restores the body and the shared domain. At every step the two chests stay separate native Grids and no inner
     * face joins a native Grid, and using an unformed block names the reason.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 600, required = true, manualOnly = true)
    public static void deviceStructureSuspendsSwitch(GameTestHelper helper) {
        var fixtures = new RouterFixtures(helper);
        var west = BASE.offset(1, 0, 1);
        var east = west.east();
        var extra = west.south();
        fixtures.placeNativeDevice(west, Direction.WEST);
        fixtures.placeNativeDevice(east, Direction.EAST);
        var registry = FederationDomainRegistryAccess.get(helper.getLevel());
        var shareDomain = (Runnable) () -> {
            var left = FederationDomainRegistryAccess.confirmedNetworkId(chestGrid(fixtures, west, Direction.WEST));
            var right = FederationDomainRegistryAccess.confirmedNetworkId(chestGrid(fixtures, east, Direction.EAST));
            helper.assertTrue(left.isPresent() && right.isPresent(), "Waiting for native identities");
            helper.assertTrue(!registry.federationdomainsFor(left.get()).isEmpty()
                    && registry.federationdomainsFor(left.get()).equals(registry.federationdomainsFor(right.get())),
                    "Both networks share the body's domain");
        };
        var separateGrids = (Runnable) () -> helper.assertTrue(
                chestGrid(fixtures, west, Direction.WEST) != chestGrid(fixtures, east, Direction.EAST),
                "The two ME chests stay separate Grids");
        run(helper, List.of(
                new Step(() -> {
                    fixtures.placeSwitch(west);
                    fixtures.placeSwitch(east);
                }, () -> {
                    assertFormed(helper, west, 2, 1, 1, "2x1x1 Switch");
                    shareDomain.run();
                    separateGrids.run();
                    assertIsolated(fixtures, helper, List.of(west, east));
                }),
                new Step(() -> fixtures.placeSwitch(extra), () -> {
                    assertUnformed(helper, List.of(west, east, extra), "L-shaped Switch");
                    for (var member : List.of(west, east, extra)) {
                        helper.assertValueEqual(fixtures.count(fixtures.router(member), RouterPortKind.DISCONNECTED), 6L,
                                member + " of an unformed candidate must link nothing");
                    }
                    for (var chest : List.of(chestGrid(fixtures, west, Direction.WEST), chestGrid(fixtures, east, Direction.EAST))) {
                        var id = FederationDomainRegistryAccess.confirmedNetworkId(chest);
                        helper.assertTrue(id.isPresent() && registry.federationdomainsFor(id.get()).isEmpty(),
                                "A network on an unformed Switch must leave the domain");
                    }
                    separateGrids.run();
                    assertIsolated(fixtures, helper, List.of(west, east, extra));
                    var status = space.controlnet.ae2federation.router.DeviceStructureUpdater.status(helper.getLevel(),
                            helper.absolutePos(west));
                    helper.assertTrue(status.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents
                            contents && contents.getKey().equals("ae2federation.structure.invalid.touching"),
                            "Using an unformed block must name the touching block: " + status.getString());
                }),
                new Step(() -> helper.setBlock(extra, Blocks.AIR), () -> {
                    assertFormed(helper, west, 2, 1, 1, "restored Switch");
                    shareDomain.run();
                    separateGrids.run();
                    assertIsolated(fixtures, helper, List.of(west, east));
                })), () -> {
                    RouterEvidence.write("devicestructuresuspendsswitch", 8, Map.of(
                            "formedShared", "true", "unformedDisconnected", "true", "networksLeftDomain", "true",
                            "restoredShared", "true", "separateGrids", "true", "innerNativeJoin", "false",
                            "status", "touching"));
                    fixtures.close();
                });
    }

    /**
     * A Router body carries Federation only while formed: two Switches joined through a 2x1x1 Router share a domain,
     * a Router block on top of it unforms the body and splits the domain, and removing it joins them again.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 600, required = true, manualOnly = true)
    public static void deviceStructureSuspendsRouter(GameTestHelper helper) {
        var fixtures = new RouterFixtures(helper);
        var row = BASE.offset(0, 0, 6);
        var leftSwitch = row;
        var rightSwitch = row.east(5);
        var routerWest = row.east(2);
        var top = routerWest.above();
        fixtures.placeSwitch(leftSwitch);
        fixtures.placeSwitch(rightSwitch);
        fixtures.placeNativeDevice(leftSwitch, Direction.WEST);
        fixtures.placeNativeDevice(rightSwitch, Direction.EAST);
        fixtures.placeFederationCable(row.east(1));
        fixtures.placeFederationCable(row.east(4));
        var registry = FederationDomainRegistryAccess.get(helper.getLevel());
        var shared = (java.util.function.BooleanSupplier) () -> {
            var left = FederationDomainRegistryAccess.confirmedNetworkId(chestGrid(fixtures, leftSwitch, Direction.WEST));
            var right = FederationDomainRegistryAccess.confirmedNetworkId(chestGrid(fixtures, rightSwitch, Direction.EAST));
            helper.assertTrue(left.isPresent() && right.isPresent(), "Waiting for native identities");
            return registry.shareFederationDomain(left.get(), right.get());
        };
        run(helper, List.of(
                new Step(() -> {
                    fixtures.placeRouter(routerWest);
                    fixtures.placeRouter(routerWest.east());
                }, () -> {
                    assertFormed(helper, routerWest, 2, 1, 1, "2x1x1 Router");
                    helper.assertTrue(shared.getAsBoolean(), "Switches joined through a formed Router share a domain");
                }),
                new Step(() -> fixtures.placeRouter(top), () -> {
                    assertUnformed(helper, List.of(routerWest, routerWest.east(), top), "protruding Router");
                    helper.assertValueEqual(fixtures.router(routerWest).binding(Direction.WEST).kind(),
                            RouterPortKind.DISCONNECTED, "An unformed Router links nothing");
                    helper.assertTrue(!shared.getAsBoolean(), "An unformed Router carries no Federation");
                }),
                new Step(() -> helper.setBlock(top, Blocks.AIR), () -> {
                    assertFormed(helper, routerWest, 2, 1, 1, "restored Router");
                    helper.assertTrue(shared.getAsBoolean(), "The restored Router joins the Switches again");
                })), () -> {
                    RouterEvidence.write("devicestructuresuspendsrouter", 5, Map.of(
                            "formedShared", "true", "protrusionSplits", "true", "restoredShared", "true",
                            "unformedFaces", "disconnected"));
                    fixtures.close();
                });
    }

    /**
     * A body across a chunk border keeps its last validated state while part of it is unloaded, and is checked again
     * when that part loads. A 3x1x1 Switch with its last block in the next chunk forms; with that chunk unloaded a
     * check keeps the body, and removing the far loaded end leaves the middle formed toward the unloaded block but not
     * toward the removed one; loading the chunk again forms the remaining 2x1x1. No check loads a chunk.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1200, required = true, manualOnly = true)
    public static void deviceStructureAcrossChunks(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(BASE).offset(0, 0, 1024);
        int border = (origin.getX() >> 4 << 4) + 15;
        var a1 = new BlockPos(border - 1, origin.getY(), origin.getZ());
        var a2 = a1.east();
        var b = a2.east();
        var chunkA = new net.minecraft.world.level.ChunkPos(a2);
        var chunkB = new net.minecraft.world.level.ChunkPos(b);
        var block = RouterRegistration.SWITCH.get();
        int west = 1 << Direction.WEST.get3DDataValue();
        int east = 1 << Direction.EAST.get3DDataValue();
        var phase = new int[1];
        var waited = new int[1];
        // A forced chunk keeps the next two around it loaded, so the anchor two chunks west keeps chunk A loaded
        // (as a border chunk once B goes) while B itself can unload.
        var anchor = new net.minecraft.world.level.ChunkPos(chunkA.x - 2, chunkA.z);
        level.setChunkForced(anchor.x, anchor.z, true);
        level.setChunkForced(chunkB.x, chunkB.z, true);
        helper.succeedWhen(() -> {
            if (phase[0] == 0) {
                for (var cell : List.of(a1, a2, b)) level.setBlock(cell, block.defaultBlockState(), Block.UPDATE_ALL);
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for the body to form");
            }
            if (phase[0] == 1) {
                helper.assertValueEqual(RouterBlock.connections(level.getBlockState(a2)), west | east, "middle connections");
                helper.assertTrue(RouterBlock.formed(level.getBlockState(b)), "The block across the border forms");
                level.setChunkForced(chunkB.x, chunkB.z, false);
                phase[0] = 2;
                helper.assertTrue(false, "Waiting for the next chunk to unload");
            }
            if (phase[0] == 2) {
                helper.assertTrue(!level.isLoaded(b), "Waiting for the next chunk to unload");
                space.controlnet.ae2federation.router.DeviceStructureUpdater.request(level, a1);
                phase[0] = 3;
                waited[0] = 0;
                helper.assertTrue(false, "Waiting for the check");
            }
            if (phase[0] == 3) {
                helper.assertTrue(++waited[0] > 2, "Waiting for the check");
                helper.assertTrue(!level.isLoaded(b), "The check must not load the unloaded chunk");
                helper.assertValueEqual(RouterBlock.connections(level.getBlockState(a2)), west | east,
                        "A check with the far block unloaded keeps the body");
                helper.assertTrue(level.isLoaded(a1), "Chunk A stays loaded");
                helper.assertTrue(RouterBlock.formed(level.getBlockState(a1)), "The loaded end stays formed");
                level.setBlock(a1, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                phase[0] = 4;
                waited[0] = 0;
                helper.assertTrue(false, "Waiting for the removal check");
            }
            if (phase[0] == 4) {
                helper.assertTrue(++waited[0] > 2, "Waiting for the removal check");
                helper.assertTrue(!level.isLoaded(b), "The removal check must not load the unloaded chunk");
                helper.assertValueEqual(RouterBlock.connections(level.getBlockState(a2)), east,
                        "The middle keeps its unloaded neighbour and drops the removed one");
                helper.assertTrue(RouterBlock.formed(level.getBlockState(a2)), "The middle keeps its last state");
                level.setChunkForced(chunkB.x, chunkB.z, true);
                phase[0] = 5;
                helper.assertTrue(false, "Waiting for the next chunk to load");
            }
            helper.assertTrue(level.isLoaded(b), "Waiting for the next chunk to load");
            helper.assertValueEqual(RouterBlock.connections(level.getBlockState(a2)), east, "the remaining west block");
            helper.assertValueEqual(RouterBlock.connections(level.getBlockState(b)), west, "the remaining east block");
            helper.assertTrue(RouterBlock.formed(level.getBlockState(a2)) && RouterBlock.formed(level.getBlockState(b)),
                    "The loaded chunk's block re-forms the remaining 2x1x1");
            for (var cell : List.of(a2, b)) level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.setChunkForced(anchor.x, anchor.z, false);
            level.setChunkForced(chunkB.x, chunkB.z, false);
            RouterEvidence.write("devicestructureacrosschunks", 7, Map.of(
                    "formedAcrossBorder", "true", "unloadKeepsBody", "true", "forcedLoads", "0",
                    "removedSeamCleared", "true", "reloadReforms", "2x1x1"));
        });
    }
}
