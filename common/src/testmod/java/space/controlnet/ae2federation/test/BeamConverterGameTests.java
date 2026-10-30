package space.controlnet.ae2federation.test;

import appeng.core.definitions.AEItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.power.BeamConverterBlock;
import space.controlnet.ae2federation.power.BeamConverterBlockEntity;
import space.controlnet.ae2federation.power.CableCrossSection;
import space.controlnet.ae2federation.power.PowerRegistration;
import space.controlnet.ae2federation.router.RouterBlock;
import space.controlnet.ae2federation.test.router.RouterEvidence;
import space.controlnet.ae2federation.test.router.RouterFixtures;
import space.controlnet.ae2federation.test.world.MockServerPlayers;

/**
 * A Beam Converter forms only at a converter's size, whichever way its blocks face, and then faces one way along its
 * length; a wrench turns a formed body end for end. Formed, it links Federation cable through its front alone and takes
 * FE only through its rear exterior face.
 */
@PrefixGameTestTemplate(false)
public final class BeamConverterGameTests {
    private static final BlockPos CENTER = new BlockPos(6, 6, 6);

    private BeamConverterGameTests() {
    }

    /**
     * Each of the three buildable sizes forms, facing east in a row of its own; the three sizes of the bundles players
     * cannot build today (1x2x4, 1x3x6, 2x3x6) and a 2x2x3 body (a block short) do not. Blocks need not
     * face alike: a body faces along its length the way most of its blocks do, else toward the one end with Federation
     * cable, else toward the axis's positive end.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void beamConverterShapes(GameTestHelper helper) {
        var block = PowerRegistration.BEAM_CONVERTER.get();
        var formed = new ArrayList<List<BlockPos>>();
        var unbuildable = new ArrayList<List<BlockPos>>();
        int z = 2;
        for (var section : CableCrossSection.values()) {
            var cells = box(new BlockPos(2, 2, z), section.converterLength(), section.wide(), section.narrow());
            cells.forEach(cell -> place(helper, cell, Direction.EAST));
            (section.buildable() ? formed : unbuildable).add(cells);
            z += section.narrow() + 1;
        }
        var short2x2 = box(new BlockPos(20, 2, 2), 3, 2, 2);
        short2x2.forEach(cell -> place(helper, cell, Direction.EAST));
        var mixed = List.of(new BlockPos(20, 2, 8), new BlockPos(21, 2, 8));
        place(helper, mixed.get(0), Direction.EAST);
        place(helper, mixed.get(1), Direction.WEST);
        var across = List.of(new BlockPos(20, 2, 11), new BlockPos(21, 2, 11));
        place(helper, across.get(0), Direction.UP);
        place(helper, across.get(1), Direction.NORTH);
        var majority = box(new BlockPos(20, 2, 14), 4, 2, 2);
        for (int index = 0; index < majority.size(); index++) {
            place(helper, majority.get(index), index < 9 ? Direction.WEST : Direction.EAST);
        }
        var cabled = List.of(new BlockPos(20, 2, 17), new BlockPos(21, 2, 17));
        cabled.forEach(cell -> place(helper, cell, Direction.UP));
        new RouterFixtures(helper).placeFederationCable(new BlockPos(19, 2, 17));
        helper.succeedWhen(() -> {
            for (var cells : formed) {
                for (var cell : cells) {
                    helper.assertTrue(RouterBlock.formed(helper.getBlockState(cell)), "converter member " + cell + " forms");
                    for (var face : Direction.values()) {
                        var input = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,
                                helper.absolutePos(cell), face);
                        boolean rear = face == Direction.WEST && cell.getX() == cells.getFirst().getX();
                        helper.assertTrue((input != null) == rear, "rear-only FE on " + cell + " / " + face);
                    }
                }
            }
            for (var cells : unbuildable) {
                for (var cell : cells) {
                    helper.assertTrue(!RouterBlock.formed(helper.getBlockState(cell)), "not built today: " + cell);
                }
            }
            for (var cell : short2x2) {
                helper.assertTrue(helper.getBlockState(cell).is(block) && !RouterBlock.formed(helper.getBlockState(cell)),
                        "a 2x2x3 body is not a converter: " + cell);
            }
            assertFacing(helper, mixed, Direction.EAST, "blocks facing opposite ways, a tie: the positive end");
            assertFacing(helper, across, Direction.EAST, "blocks facing across the length: the positive end");
            assertFacing(helper, majority, Direction.WEST, "nine of sixteen blocks face west");
            assertFacing(helper, cabled, Direction.WEST, "no vote: toward the Federation cable");
            RouterEvidence.write("beamconvertershapes", 11, Map.ofEntries(
                    Map.entry("1x1x2", "formed"), Map.entry("1x2x4", "unformed"), Map.entry("1x3x6", "unformed"),
                    Map.entry("2x2x4", "formed"), Map.entry("2x3x6", "unformed"), Map.entry("3x3x6", "formed"),
                    Map.entry("2x2x3", "unformed"), Map.entry("tie", "positive end"),
                    Map.entry("across", "positive end"), Map.entry("majority", "west"),
                    Map.entry("noVote", "toward the cable")));
        });
    }

    /**
     * A 1x1x2 converter facing east, its back to the west: a cable at its front joins its domain, cables at its side
     * and back do not. FE enters only through the back.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void beamConverterLinksThroughItsFront(GameTestHelper helper) {
        BlockPos.betweenClosed(CENTER.offset(-3, -1, -1), CENTER.offset(2, 1, 1))
                .forEach(position -> helper.setBlock(position, Blocks.AIR));
        var fixtures = new RouterFixtures(helper);
        var front = CENTER;
        var back = CENTER.west();
        place(helper, front, Direction.EAST);
        place(helper, back, Direction.EAST);
        fixtures.placeFederationCable(front.east());
        fixtures.placeFederationCable(front.north());
        fixtures.placeFederationCable(back.west());
        var level = helper.getLevel();
        var registry = FederationDomainRegistryAccess.get(level);
        var converterNode = FederationDomainRegistryAccess.nodeId(level, helper.absolutePos(front));
        var inserted = new int[] {-1};
        helper.succeedWhen(() -> {
            helper.assertTrue(RouterBlock.formed(helper.getBlockState(front)), "the 1x1x2 converter forms");
            var domain = registry.federationDomainOf(FederationDomainRegistryAccess.nodeId(level,
                    helper.absolutePos(front.east())));
            helper.assertTrue(domain.isPresent() && domain.get().nodes().contains(converterNode)
                    && domain.get().nodes().contains(FederationDomainRegistryAccess.nodeId(level, helper.absolutePos(back))),
                    "the cable at the front joins the whole converter");
            for (var cable : new BlockPos[] {front.north(), back.west()}) {
                var other = registry.federationDomainOf(FederationDomainRegistryAccess.nodeId(level, helper.absolutePos(cable)));
                helper.assertTrue(other.isEmpty() || !other.get().nodes().contains(converterNode),
                        "a cable at the side or back links nothing: " + cable);
            }
            helper.assertTrue(level.getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(front),
                    Direction.EAST) == null, "the front takes no FE");
            helper.assertTrue(level.getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(front), Direction.UP)
                    == null, "a side takes no FE");
            helper.assertTrue(level.getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(front), Direction.WEST)
                    == null, "an interior rear-facing member takes no FE");
            helper.assertTrue(level.getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(back), null)
                    == null, "unsided access cannot bypass the rear port");
            var side = level.getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(back), Direction.WEST);
            helper.assertTrue(side != null, "the rear takes FE");
            if (inserted[0] < 0) inserted[0] = side.receiveEnergy(1000, false);
            helper.assertValueEqual(inserted[0], 1000, "FE accepted by the rear");
            var body = level.getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(back), Direction.WEST);
            helper.assertValueEqual(body.getEnergyStored(), 1000, "the body holds what it took in");
            RouterEvidence.write("beamconverterlinksthroughitsfront", 6, Map.of(
                    "formed", "1x1x2", "front", "linked", "sideAndBack", "unlinked", "frontFe", "none",
                    "rearFe", "accepted", "stored", "1000"));
            fixtures.close();
        });
    }

    /**
     * A wrench turns a formed 1x1x2 converter end for end: its front, and so its link and the face that takes no FE, move
     * to the other end. An unformed block keeps its facing.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void beamConverterTurnsWithAWrench(GameTestHelper helper) {
        BlockPos.betweenClosed(CENTER.offset(-3, -1, -1), CENTER.offset(2, 1, 3))
                .forEach(position -> helper.setBlock(position, Blocks.AIR));
        var fixtures = new RouterFixtures(helper);
        var east = CENTER;
        var west = CENTER.west();
        place(helper, east, Direction.EAST);
        place(helper, west, Direction.EAST);
        fixtures.placeFederationCable(east.east());
        fixtures.placeFederationCable(west.west());
        var single = CENTER.south(3);
        place(helper, single, Direction.NORTH);
        var level = helper.getLevel();
        var registry = FederationDomainRegistryAccess.get(level);
        var converterNode = FederationDomainRegistryAccess.nodeId(level, helper.absolutePos(east));
        var player = MockServerPlayers.inLevel(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, AEItems.CERTUS_QUARTZ_WRENCH.stack());
        var phase = new int[1];
        var oldInput = new net.neoforged.neoforge.energy.IEnergyStorage[1];
        helper.succeedWhen(() -> {
            if (phase[0] == 0) {
                helper.assertTrue(linked(helper, east.east(), converterNode), "Waiting for the east cable to link");
                oldInput[0] = level.getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(west), Direction.WEST);
                helper.assertTrue(oldInput[0] != null, "the original rear accepts FE");
                use(helper, player, west, Direction.UP);
                use(helper, player, single, Direction.UP);
                phase[0] = 1;
                throw new net.minecraft.gametest.framework.GameTestAssertException("Waiting for the turned body to link");
            }
            assertFacing(helper, List.of(east, west), Direction.WEST, "the wrench turned the body");
            helper.assertValueEqual(oldInput[0].receiveEnergy(100, false), 0, "a cached old rear interface rejects FE after rotation");
            helper.assertValueEqual(helper.getBlockState(single).getValue(BeamConverterBlock.FACING), Direction.NORTH,
                    "an unformed block keeps its facing");
            helper.assertTrue(linked(helper, west.west(), converterNode), "the west cable links the turned body");
            helper.assertTrue(!linked(helper, east.east(), converterNode), "the east cable no longer links it");
            helper.assertTrue(level.getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(west),
                    Direction.WEST) == null, "the new front takes no FE");
            helper.assertTrue(level.getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(east),
                    Direction.EAST) != null, "the old front takes FE");
            RouterEvidence.write("beamconverterturnswithawrench", 5, Map.of(
                    "turned", "west", "unformed", "kept", "westCable", "linked", "eastCable", "unlinked",
                    "frontFe", "moved"));
            fixtures.close();
        });
    }

    private static boolean linked(GameTestHelper helper, BlockPos cable,
            space.controlnet.ae2federation.domain.FederationDomainNodeId converter) {
        var level = helper.getLevel();
        var domain = FederationDomainRegistryAccess.get(level).federationDomainOf(
                FederationDomainRegistryAccess.nodeId(level, helper.absolutePos(cable)));
        return domain.isPresent() && domain.get().nodes().contains(converter);
    }

    private static void use(GameTestHelper helper, ServerPlayer player, BlockPos relative, Direction face) {
        var absolute = helper.absolutePos(relative);
        var hit = new BlockHitResult(Vec3.atCenterOf(absolute).relative(face, 0.5), face, absolute, false);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    private static void assertFacing(GameTestHelper helper, List<BlockPos> cells, Direction facing, String what) {
        for (var cell : cells) {
            var state = helper.getBlockState(cell);
            helper.assertTrue(RouterBlock.formed(state), what + ": " + cell + " forms");
            helper.assertValueEqual(state.getValue(BeamConverterBlock.FACING), facing, what + ": " + cell);
        }
    }

    /**
     * A 2x2x4 converter is one buffer of sixteen blocks: FE through one rear block fills the whole body, every block reads
     * the body's total, and each block keeps an equal share, which a block broken out of the body takes with it.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void beamConverterBodySharesOneBuffer(GameTestHelper helper) {
        var section = CableCrossSection.TWO_BY_TWO;
        var cells = box(new BlockPos(2, 2, 2), section.converterLength(), section.wide(), section.narrow());
        cells.forEach(cell -> place(helper, cell, Direction.EAST));
        var level = helper.getLevel();
        var fed = cells.get(0);
        var other = cells.get(cells.size() - 1);
        var accepted = new int[] {-1, -1};
        var capacity = (int) BeamConverterBlockEntity.CAPACITY;
        helper.succeedWhen(() -> {
            if (accepted[1] < 0) {
                for (var cell : cells) {
                    helper.assertTrue(RouterBlock.formed(helper.getBlockState(cell)), "Waiting for the converter to form");
                }
            }
            var input = level.getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(fed), Direction.WEST);
            var reader = level.getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(cells.get(1)), Direction.WEST);
            if (accepted[0] < 0) accepted[0] = input.receiveEnergy(100_000, false);
            helper.assertValueEqual(accepted[0], 100_000, "one block takes in more than its own 16000 FE");
            if (accepted[1] < 0) {
                helper.assertValueEqual(reader.getEnergyStored(), 100_000, "another block reads the body's total");
                helper.assertValueEqual(reader.getMaxEnergyStored(), 16 * capacity, "the body holds 16000 FE per block");
                for (var cell : cells) {
                    helper.assertValueEqual(((BeamConverterBlockEntity) helper.getBlockEntity(cell)).stored(), 6_250.0,
                            "each block keeps an equal share: " + cell);
                }
                accepted[1] = input.receiveEnergy(200_000, false);
                GameTestChecks.once(helper, () -> helper.assertValueEqual(accepted[1], 16 * capacity - 100_000,
                        "the body takes in up to its room"));
                helper.setBlock(other, Blocks.AIR);
            }
            for (var cell : cells.subList(0, cells.size() - 1)) {
                helper.assertValueEqual(((BeamConverterBlockEntity) helper.getBlockEntity(cell)).stored(), (double) capacity,
                        "a block left from the body keeps its share: " + cell);
            }
            RouterEvidence.write("beamconverterbodysharesonebuffer", 6, Map.of(
                    "accepted", "100000 through one block", "read", "100000 from another", "capacity", "256000",
                    "share", "6250 each", "topUp", "156000", "broken", "16000 kept per block"));
        });
    }

    /** All three sizes expose their complete rear plane in all six orientations. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void beamConverterRearPortsEveryOrientation(GameTestHelper helper) {
        record Body(List<BlockPos> cells, Direction facing) { }
        var bodies = new ArrayList<Body>();
        for (int n = 1; n <= 3; n++) {
            for (var facing : Direction.values()) {
                int i = facing.get3DDataValue();
                var min = new BlockPos(2 + (i % 3) * 10, 2 + (n - 1) * 8, 2 + (i / 3) * 10);
                var axis = facing.getAxis();
                var cells = box(min, axis == Direction.Axis.X ? 2*n : n,
                        axis == Direction.Axis.Y ? 2*n : n, axis == Direction.Axis.Z ? 2*n : n);
                cells.forEach(cell -> place(helper, cell, facing));
                bodies.add(new Body(cells, facing));
            }
        }
        helper.succeedWhen(() -> {
            for (var body : bodies) {
                assertFacing(helper, body.cells(), body.facing(), "all orientations form");
                var rear = body.facing().getOpposite();
                var axis = rear.getAxis();
                int boundary = rear.getAxisDirection() == Direction.AxisDirection.NEGATIVE
                        ? body.cells().getFirst().get(axis) : body.cells().getLast().get(axis);
                for (var cell : body.cells()) {
                    for (var face : Direction.values()) {
                        var input = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,
                                helper.absolutePos(cell), face);
                        boolean allowed = face == rear && cell.get(axis) == boundary;
                        helper.assertTrue((input != null) == allowed, "rear plane at " + cell + " / " + face);
                        if (input != null) {
                            helper.assertValueEqual(input.receiveEnergy(100, true), 100, "rear receives in simulation");
                            helper.assertValueEqual(input.getEnergyStored(), 0, "simulation does not fill the buffer");
                        }
                    }
                }
            }
            RouterEvidence.write("beamconverterrearportseveryorientation", 18,
                    Map.of("sizes", "1,2,3", "orientations", "all six", "FE", "rear exterior only"));
        });
    }

    private static List<BlockPos> box(BlockPos min, int sx, int sy, int sz) {
        var cells = new ArrayList<BlockPos>();
        for (int x = 0; x < sx; x++) for (int y = 0; y < sy; y++) for (int zz = 0; zz < sz; zz++) cells.add(min.offset(x, y, zz));
        return cells;
    }

    private static void place(GameTestHelper helper, BlockPos position, Direction facing) {
        helper.setBlock(position, PowerRegistration.BEAM_CONVERTER.get().defaultBlockState()
                .setValue(BeamConverterBlock.FACING, facing));
    }
}
