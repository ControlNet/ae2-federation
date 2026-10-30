package space.controlnet.ae2federation.test;

import appeng.core.definitions.AEItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.bundle.BundleSegment;
import space.controlnet.ae2federation.bundle.BundledCables;
import space.controlnet.ae2federation.domain.FederationDomainNodeEvidence;
import space.controlnet.ae2federation.domain.FederationDomainNodeId;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.power.BeamConverterBlock;
import space.controlnet.ae2federation.power.CableCrossSection;
import space.controlnet.ae2federation.power.OperatingPowerCosts;
import space.controlnet.ae2federation.power.OperatingPowerRule;
import space.controlnet.ae2federation.power.OperatingPowerService;
import space.controlnet.ae2federation.power.PowerRegistration;
import space.controlnet.ae2federation.quantum.QuantumCubeBlockEntity;
import space.controlnet.ae2federation.quantum.QuantumLinkService;
import space.controlnet.ae2federation.quantum.QuantumPairIndex;
import space.controlnet.ae2federation.quantum.QuantumRegistration;
import space.controlnet.ae2federation.router.RouterBlock;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.test.router.RouterEvidence;
import space.controlnet.ae2federation.test.router.RouterFixtures;
import space.controlnet.ae2federation.test.world.MockServerPlayers;
import space.controlnet.ae2federation.test.world.OtherDimensionSite;

/**
 * A Quantum Cube forms only as a solid cube of edge 3, 4 or 5 and links cable inside each face's centre only. Two cubes
 * bound with an AE2 Memory Card join their domains as cable would, while both are formed and the same size; binding
 * again drops the old partners. A linked pair pays AE2's bridge cost per block and counts as cable of its square.
 */
@PrefixGameTestTemplate(false)
public final class QuantumCubeGameTests {
    private QuantumCubeGameTests() {
    }

    /** 3³, 4³ and 5³ form and take 1x1, 2x2 and 3x3; 2³, 6³ and a 3x3x4 do not form. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void quantumCubeShapes(GameTestHelper helper) {
        var three = cube(helper, new BlockPos(1, 1, 1), 3);
        var four = cube(helper, new BlockPos(6, 1, 1), 4);
        var five = cube(helper, new BlockPos(12, 1, 1), 5);
        var two = cube(helper, new BlockPos(19, 1, 1), 2);
        var six = cube(helper, new BlockPos(23, 1, 1), 6);
        var cuboid = box(new BlockPos(1, 1, 10), 3, 3, 4);
        cuboid.forEach(cell -> place(helper, cell));
        helper.succeedWhen(() -> {
            assertCapacity(helper, three, 1, "a 3x3x3 forms and takes 1x1");
            assertCapacity(helper, four, 6, "a 4x4x4 forms and takes 2x2");
            assertCapacity(helper, five, FederationDomainNodeEvidence.UNLIMITED, "a 5x5x5 forms and takes 3x3");
            for (var cells : List.of(two, six, cuboid)) {
                for (var cell : cells) {
                    helper.assertTrue(!RouterBlock.formed(helper.getBlockState(cell)), "no cube: " + cell);
                }
            }
            var status = space.controlnet.ae2federation.router.DeviceStructureUpdater.status(helper.getLevel(),
                    helper.absolutePos(cuboid.getFirst()));
            helper.assertTrue(status.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents
                    contents && contents.getKey().equals("ae2federation.structure.invalid.wrong_shape.quantum_cube"),
                    "a 3x3x4 must name the cube's sizes: " + status.getString());
            RouterEvidence.write("quantumcubeshapes", 6, Map.of("3x3x3", "1x1", "4x4x4", "2x2", "5x5x5", "3x3",
                    "2x2x2", "unformed", "6x6x6", "unformed", "3x3x4", "unformed"));
        });
    }

    /**
     * A cable on a 3³ face's centre joins it, one on its border does not; a 2x2 bundle over a 4³ face's centre joins,
     * one a row lower, pressed on the border, does not; a 2x2 bundle anywhere inside a 5³ face's 3x3 centre joins.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void quantumCubeLinksInsideItsFaceCentres(GameTestHelper helper) {
        var fixtures = new RouterFixtures(helper);
        cube(helper, new BlockPos(2, 2, 2), 3);
        cube(helper, new BlockPos(10, 2, 2), 4);
        cube(helper, new BlockPos(21, 2, 2), 5);
        var centre = new BlockPos(1, 3, 3);
        var border = new BlockPos(2, 4, 1);
        fixtures.placeFederationCable(centre);
        fixtures.placeFederationCable(border);
        var inside = box(new BlockPos(7, 3, 3), 3, 2, 2);
        var offCentre = box(new BlockPos(14, 2, 3), 3, 2, 2);
        var large = box(new BlockPos(18, 4, 3), 3, 2, 2);
        for (var run : List.of(inside, offCentre, large)) run.forEach(fixtures::placeFederationCable);
        var level = helper.getLevel();
        var small = node(helper, new BlockPos(3, 3, 2));
        var middle = node(helper, new BlockPos(11, 3, 3));
        var big = node(helper, new BlockPos(22, 3, 3));
        var phase = new int[1];
        helper.succeedWhen(() -> {
            if (phase[0] == 0) {
                for (var run : List.of(inside, offCentre, large)) {
                    var last = run.get(run.size() - 1);
                    BundledCables.get(level).form(level, new BundleSegment(helper.absolutePos(run.get(0)),
                            helper.absolutePos(last), Direction.Axis.X));
                }
                phase[0] = 1;
                throw new GameTestAssertException("Waiting for the bundles to settle");
            }
            helper.assertTrue(linked(helper, centre, small), "a cable on the face's centre joins the 3x3x3");
            helper.assertTrue(linked(helper, inside.get(0), middle), "a 2x2 bundle on the 4x4x4's centre joins it");
            helper.assertTrue(linked(helper, large.get(0), big), "a 2x2 bundle inside the 5x5x5's centre joins it");
            helper.assertTrue(!linked(helper, border, small), "a cable on the border links nothing");
            helper.assertTrue(!linked(helper, offCentre.get(0), middle), "a bundle pressed on the border links nothing");
            RouterEvidence.write("quantumcubelinksinsideitsfacecentres", 5, Map.of("centre", "linked",
                    "border", "unlinked", "2x2On4", "linked", "2x2OffCentre", "unlinked", "2x2On5", "linked"));
            fixtures.close();
        });
    }

    /**
     * Sneak-use copies a cube to a Memory Card and use binds it to another: the cables on both join one domain. A cube
     * bound to one of another size does not link. A broken block unlinks the pair and the block put back relinks it.
     * Binding a cube again drops both old partners.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
            timeoutTicks = 600, required = true, manualOnly = true)
    public static void quantumCubePairBindsWithAMemoryCard(GameTestHelper helper) {
        var fixtures = new RouterFixtures(helper);
        var a = new BlockPos(2, 2, 2);
        var b = new BlockPos(10, 2, 2);
        var c = new BlockPos(2, 2, 10);
        var d = new BlockPos(10, 2, 10);
        cube(helper, a, 3);
        cube(helper, b, 3);
        cube(helper, c, 3);
        cube(helper, d, 4);
        var cableA = new BlockPos(1, 3, 3);
        var cableB = new BlockPos(13, 3, 3);
        var cableC = new BlockPos(1, 3, 11);
        for (var cable : List.of(cableA, cableB, cableC)) fixtures.placeFederationCable(cable);
        var level = helper.getLevel();
        var nodeB = node(helper, cableB);
        var nodeC = node(helper, cableC);
        var player = MockServerPlayers.inLevel(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, AEItems.MEMORY_CARD.stack());
        var broken = new BlockPos(12, 4, 4);
        var phase = new int[1];
        helper.succeedWhen(() -> {
            if (phase[0] == 0) {
                for (var cube : List.of(a, b, c, d)) {
                    helper.assertTrue(RouterBlock.formed(helper.getBlockState(cube)), "Waiting for " + cube + " to form");
                }
                bind(helper, player, a, b);
                bind(helper, player, c, d);
                phase[0] = 1;
                throw new GameTestAssertException("Waiting for the pair to link");
            }
            if (phase[0] == 1) {
                helper.assertTrue(linked(helper, cableA, nodeB), "Waiting for the pair to link");
                helper.assertValueEqual(status(helper, a), QuantumPairIndex.Status.Kind.LINKED, "Jade reads the link");
                helper.assertValueEqual(status(helper, c), QuantumPairIndex.Status.Kind.WRONG_SIZE,
                        "a 3x3x3 bound to a 4x4x4");
                helper.setBlock(broken, Blocks.AIR);
                phase[0] = 2;
                throw new GameTestAssertException("Waiting for the broken cube to unlink");
            }
            if (phase[0] == 2) {
                helper.assertTrue(!linked(helper, cableA, nodeB), "Waiting for the broken cube to unlink");
                place(helper, broken);
                phase[0] = 3;
                throw new GameTestAssertException("Waiting for the mended cube to link again");
            }
            if (phase[0] == 3) {
                helper.assertTrue(linked(helper, cableA, nodeB), "Waiting for the mended cube to link again");
                bind(helper, player, a, c);
                phase[0] = 4;
                throw new GameTestAssertException("Waiting for the new pair to link");
            }
            helper.assertTrue(linked(helper, cableA, nodeC), "Waiting for the new pair to link");
            helper.assertTrue(!linked(helper, cableA, nodeB), "the old partner is dropped");
            helper.assertValueEqual(status(helper, b), QuantumPairIndex.Status.Kind.NOT_BOUND, "the old partner is cleared");
            helper.assertValueEqual(status(helper, d), QuantumPairIndex.Status.Kind.NOT_BOUND,
                    "the new partner's old partner is cleared");
            RouterEvidence.write("quantumcubepairbindswithamemorycard", 8, Map.of("bound", "linked",
                    "jade", "linked", "otherSize", "wrong size", "broken", "unlinked", "mended", "linked",
                    "rebound", "linked", "oldPartner", "dropped", "oldPartners", "not bound"));
            fixtures.close();
        });
    }

    /**
     * A Memory Card binds an overworld cube to a nether one and the cables on both join one domain. As AE2's bridge,
     * the pair unlinks while the nether chunks really unload (their tickets released) and links again when they load;
     * when the nether level closes, the overworld cube reads its partner as not loaded.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
            timeoutTicks = 1400, required = true, manualOnly = true)
    public static void quantumCubePairLinksAcrossDimensions(GameTestHelper helper) {
        var fixtures = new RouterFixtures(helper);
        var site = OtherDimensionSite.nether(helper, new BlockPos(5, 3, 5));
        var here = new BlockPos(2, 2, 2);
        var there = new BlockPos(1, 0, 1);
        var farCable = new BlockPos(0, 1, 2);
        cube(helper, here, 3);
        var cable = new BlockPos(1, 3, 3);
        fixtures.placeFederationCable(cable);
        var nether = site.level();
        var farNode = FederationDomainRegistryAccess.nodeId(nether, site.absolute(farCable));
        var player = MockServerPlayers.inLevel(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, AEItems.MEMORY_CARD.stack());
        var phase = new int[1];
        helper.succeedWhen(() -> {
            if (phase[0] == 0) {
                helper.assertTrue(site.ready(), "Waiting for the nether site to tick: " + site.tickDiagnostics());
                for (var cell : box(there, 3, 3, 3)) {
                    site.setBlock(cell, QuantumRegistration.QUANTUM_CUBE.get().defaultBlockState());
                }
                site.setBlock(farCable, RouterRegistration.FEDERATION_CABLE.get().defaultBlockState());
                phase[0] = 1;
                throw new GameTestAssertException("Waiting for both cubes to form");
            }
            if (phase[0] == 1) {
                helper.assertTrue(RouterBlock.formed(helper.getBlockState(here)), "Waiting for the overworld cube");
                helper.assertTrue(RouterBlock.formed(site.getBlockState(there)), "Waiting for the nether cube");
                player.setShiftKeyDown(true);
                use(player, nether, site.absolute(there));
                player.setShiftKeyDown(false);
                use(player, helper.getLevel(), helper.absolutePos(here));
                phase[0] = 2;
                throw new GameTestAssertException("Waiting for the pair to link");
            }
            if (phase[0] == 2) {
                helper.assertTrue(linked(helper, cable, farNode), "Waiting for the pair to link across dimensions");
                helper.assertValueEqual(status(helper, here), QuantumPairIndex.Status.Kind.LINKED, "Jade reads the link");
                site.releaseTickets();
                phase[0] = 3;
                throw new GameTestAssertException("Waiting for the nether chunks to unload");
            }
            if (phase[0] == 3) {
                helper.assertFalse(site.loaded(), "Waiting for the nether chunks to unload");
                helper.assertTrue(!linked(helper, cable, farNode), "Waiting for the unloaded pair to unlink");
                helper.assertValueEqual(status(helper, here), QuantumPairIndex.Status.Kind.PARTNER_NOT_LOADED,
                        "Jade reads the partner as not loaded");
                site.forceAgain();
                phase[0] = 4;
                throw new GameTestAssertException("Waiting for the nether chunks to load again");
            }
            if (phase[0] == 4) {
                helper.assertTrue(site.ticking(), "Waiting for the nether chunks to tick: " + site.tickDiagnostics());
                helper.assertTrue(linked(helper, cable, farNode), "Waiting for the reloaded pair to link again");
                helper.assertValueEqual(status(helper, here), QuantumPairIndex.Status.Kind.LINKED, "linked again");
                QuantumLinkService.closeLevel(nether);
                phase[0] = 5;
                throw new GameTestAssertException("Waiting for the closed nether to unlink");
            }
            helper.assertTrue(!linked(helper, cable, farNode), "Waiting for the closed nether to unlink");
            helper.assertValueEqual(status(helper, here), QuantumPairIndex.Status.Kind.PARTNER_NOT_LOADED,
                    "the nether's level closed");
            RouterEvidence.write("quantumcubepairlinksacrossdimensions", 7, Map.of("bound", "linked",
                    "jade", "linked", "unloaded", "unlinked", "unloadedJade", "partner not loaded",
                    "reloaded", "linked", "reloadedJade", "linked", "levelClosed", "partner not loaded"));
            fixtures.close();
            site.close();
        });
    }

    /**
     * With operating power required, a 5³ cube pays AE2's bridge cost, 22 AE/t for each of its 125 blocks, linked or
     * not; once linked, the pair counts as 3x3 cable, so a 1x1x2 converter no longer matches its domain.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void quantumCubePairNeedsItsSizeOfConverter(GameTestHelper helper) {
        var fixtures = new RouterFixtures(helper);
        OperatingPowerRule.set(true, OperatingPowerRule.DEFAULT_MULTIPLIER);
        var here = new BlockPos(2, 1, 2);
        var there = new BlockPos(12, 1, 2);
        cube(helper, here, 5);
        cube(helper, there, 5);
        var cable = new BlockPos(1, 3, 4);
        var farCable = new BlockPos(17, 3, 4);
        fixtures.placeFederationCable(cable);
        fixtures.placeFederationCable(farCable);
        for (var z = 2; z <= 3; z++) {
            helper.setBlock(new BlockPos(1, 3, z), PowerRegistration.BEAM_CONVERTER.get().defaultBlockState()
                    .setValue(BeamConverterBlock.FACING, Direction.SOUTH));
        }
        var level = helper.getLevel();
        var cableNode = node(helper, cable);
        var farNode = node(helper, farCable);
        var phase = new int[1];
        helper.succeedWhen(() -> {
            var registry = FederationDomainRegistryAccess.get(level);
            var domain = registry.federationDomainOf(cableNode)
                    .orElseThrow(() -> new GameTestAssertException("Waiting for the cable's domain"));
            var reading = OperatingPowerService.reading(level.getServer(), domain.federationDomainId())
                    .orElseThrow(() -> new GameTestAssertException("Waiting for a power reading"));
            var cubes = reading.use().byGroup().getOrDefault(OperatingPowerCosts.Group.QUANTUM_CUBES, 0.0);
            if (phase[0] == 0) {
                helper.assertTrue(!reading.converters().isEmpty(), "Waiting for the converter to join");
                helper.assertTrue(cubes == 125 * 44.0, "Waiting for one unlinked cube's cost, not " + cubes);
                helper.assertValueEqual(reading.section(), CableCrossSection.ONE_BY_ONE, "unlinked, plain cable");
                QuantumLinkService.get(level.getServer()).bind(GlobalPos.of(level.dimension(), helper.absolutePos(here)),
                        GlobalPos.of(level.dimension(), helper.absolutePos(there)));
                phase[0] = 1;
                throw new GameTestAssertException("Waiting for the pair to link");
            }
            helper.assertTrue(linked(helper, cable, farNode), "Waiting for the pair to link");
            helper.assertTrue(cubes == 250 * 44.0, "both cubes pay 2750 AE/t each, not " + cubes);
            helper.assertValueEqual(reading.section(), CableCrossSection.THREE_BY_THREE, "the pair counts as 3x3");
            helper.assertTrue(reading.converters().stream().noneMatch(OperatingPowerService.ConverterReading::matches),
                    "the 1x1x2 converter no longer matches");
            RouterEvidence.write("quantumcubepairneedsitssizeofconverter", 5, Map.of("unlinked", "5500 FE/t",
                    "linked", "11000 FE/t", "section", "3x3", "converter", "no longer matches", "plain", "1x1"));
            OperatingPowerRule.set(false, OperatingPowerRule.DEFAULT_MULTIPLIER);
            fixtures.close();
        });
    }

    /** Copies the cube at {@code from} to the player's Memory Card, sneaking, and uses the card on {@code to}. */
    private static void bind(GameTestHelper helper, ServerPlayer player, BlockPos from, BlockPos to) {
        player.setShiftKeyDown(true);
        use(helper, player, from);
        player.setShiftKeyDown(false);
        use(helper, player, to);
    }

    private static void use(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
        use(player, helper.getLevel(), helper.absolutePos(relative));
    }

    private static void use(ServerPlayer player, ServerLevel level, BlockPos absolute) {
        var hit = new BlockHitResult(Vec3.atCenterOf(absolute).relative(Direction.UP, 0.5), Direction.UP, absolute,
                false);
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    private static QuantumPairIndex.Status.Kind status(GameTestHelper helper, BlockPos min) {
        var level = helper.getLevel();
        var status = QuantumLinkService.get(level.getServer())
                .status(FederationDomainRegistryAccess.dimension(level), helper.absolutePos(min));
        if (status == null) throw new GameTestAssertException("Waiting for " + min + " to be reported");
        return status.kind();
    }

    private static void assertCapacity(GameTestHelper helper, List<BlockPos> cells, int factor, String what) {
        for (var cell : cells) {
            helper.assertTrue(RouterBlock.formed(helper.getBlockState(cell)), what + ": " + cell + " forms");
            var cube = (QuantumCubeBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(cell));
            helper.assertValueEqual(cube.capacityFactor(), factor, what + ": " + cell);
        }
    }

    private static FederationDomainNodeId node(GameTestHelper helper, BlockPos relative) {
        return FederationDomainRegistryAccess.nodeId(helper.getLevel(), helper.absolutePos(relative));
    }

    private static boolean linked(GameTestHelper helper, BlockPos cable, FederationDomainNodeId other) {
        var level = helper.getLevel();
        var domain = FederationDomainRegistryAccess.get(level).federationDomainOf(
                FederationDomainRegistryAccess.nodeId(level, helper.absolutePos(cable)));
        return domain.isPresent() && domain.get().nodes().contains(other);
    }

    private static List<BlockPos> cube(GameTestHelper helper, BlockPos min, int edge) {
        var cells = box(min, edge, edge, edge);
        cells.forEach(cell -> place(helper, cell));
        return cells;
    }

    private static List<BlockPos> box(BlockPos min, int sx, int sy, int sz) {
        var cells = new ArrayList<BlockPos>();
        for (int x = 0; x < sx; x++) for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) cells.add(min.offset(x, y, z));
        return cells;
    }

    private static void place(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, QuantumRegistration.QUANTUM_CUBE.get().defaultBlockState());
    }
}
