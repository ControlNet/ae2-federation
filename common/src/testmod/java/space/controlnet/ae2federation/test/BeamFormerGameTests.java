package space.controlnet.ae2federation.test;

import appeng.core.definitions.AEItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.beam.BeamFormerBlock;
import space.controlnet.ae2federation.beam.BeamRegistration;
import space.controlnet.ae2federation.bundle.BundleSegment;
import space.controlnet.ae2federation.bundle.BundledCables;
import space.controlnet.ae2federation.domain.FederationDomainNodeEvidence;
import space.controlnet.ae2federation.domain.FederationDomainNodeId;
import space.controlnet.ae2federation.domain.FederationDomainNodeKind;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.router.RouterBlock;
import space.controlnet.ae2federation.test.router.RouterEvidence;
import space.controlnet.ae2federation.test.router.RouterFixtures;
import space.controlnet.ae2federation.test.world.MockServerPlayers;

/**
 * A Beam Former forms only as a square 1x1, 2x2 or 3x3 one block thick, whichever way its blocks face, and then faces
 * along its thin axis; it links Federation cable through its back alone, a bundle of its square included. A wrench
 * flips a 2x2 or 3x3 and turns a 1x1 to the clicked face.
 */
@PrefixGameTestTemplate(false)
public final class BeamFormerGameTests {
    private static final BlockPos CENTER = new BlockPos(6, 6, 6);

    private BeamFormerGameTests() {
    }

    /**
     * Each square forms, a 1x2 and a 2x2x2 do not. A body faces along its thin axis the way most of its blocks do, else
     * away from the one side with Federation cable (the back takes the cable), else toward the axis's positive end.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void beamFormerShapes(GameTestHelper helper) {
        var block = BeamRegistration.BEAM_FORMER.get();
        var one = List.of(new BlockPos(2, 2, 2));
        one.forEach(cell -> place(helper, cell, Direction.NORTH));
        var two = box(new BlockPos(2, 2, 5), 1, 2, 2);
        two.forEach(cell -> place(helper, cell, Direction.EAST));
        var three = box(new BlockPos(2, 2, 9), 1, 3, 3);
        three.forEach(cell -> place(helper, cell, Direction.EAST));
        var oneByTwo = box(new BlockPos(2, 2, 14), 1, 2, 1);
        oneByTwo.forEach(cell -> place(helper, cell, Direction.EAST));
        var thick = box(new BlockPos(2, 2, 17), 2, 2, 2);
        thick.forEach(cell -> place(helper, cell, Direction.EAST));
        var majority = box(new BlockPos(10, 2, 2), 1, 2, 2);
        for (int index = 0; index < majority.size(); index++) {
            place(helper, majority.get(index), index < 3 ? Direction.WEST : Direction.UP);
        }
        var cabled = box(new BlockPos(10, 2, 6), 1, 2, 2);
        cabled.forEach(cell -> place(helper, cell, Direction.UP));
        var fixtures = new RouterFixtures(helper);
        fixtures.placeFederationCable(new BlockPos(11, 2, 6));
        var noVote = box(new BlockPos(10, 2, 10), 1, 2, 2);
        noVote.forEach(cell -> place(helper, cell, Direction.SOUTH));
        helper.succeedWhen(() -> {
            assertFacing(helper, one, Direction.NORTH, "a 1x1 keeps its facing");
            assertFacing(helper, two, Direction.EAST, "a 2x2 forms");
            assertFacing(helper, three, Direction.EAST, "a 3x3 forms");
            for (var cell : oneByTwo) {
                helper.assertTrue(helper.getBlockState(cell).is(block) && !RouterBlock.formed(helper.getBlockState(cell)),
                        "a 1x2 is no former: " + cell);
            }
            for (var cell : thick) {
                helper.assertTrue(!RouterBlock.formed(helper.getBlockState(cell)), "a 2x2x2 is no former: " + cell);
            }
            assertFacing(helper, majority, Direction.WEST, "three of four blocks face west");
            assertFacing(helper, cabled, Direction.WEST, "no vote on its axis: away from the cable at its east");
            assertFacing(helper, noVote, Direction.EAST, "no vote, no cable: the positive end");
            RouterEvidence.write("beamformershapes", 8, Map.of("1x1", "formed", "2x2", "formed", "3x3", "formed",
                    "1x2", "unformed", "2x2x2", "unformed", "majority", "west", "cable", "away from it",
                    "noVote", "positive end"));
            fixtures.close();
        });
    }

    /**
     * A 1x1 former facing east: a cable at its back joins its domain, cables at its front and side do not; its node
     * is a Beam Former's and carries one cable's capacity.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void beamFormerLinksThroughItsBack(GameTestHelper helper) {
        BlockPos.betweenClosed(CENTER.offset(-2, -1, -1), CENTER.offset(2, 1, 1))
                .forEach(position -> helper.setBlock(position, Blocks.AIR));
        var fixtures = new RouterFixtures(helper);
        place(helper, CENTER, Direction.EAST);
        fixtures.placeFederationCable(CENTER.west());
        fixtures.placeFederationCable(CENTER.east());
        fixtures.placeFederationCable(CENTER.north());
        var level = helper.getLevel();
        var former = FederationDomainRegistryAccess.nodeId(level, helper.absolutePos(CENTER));
        helper.succeedWhen(() -> {
            helper.assertTrue(linked(helper, CENTER.west(), former), "the cable at the back joins the former");
            for (var cable : new BlockPos[] {CENTER.east(), CENTER.north()}) {
                helper.assertTrue(!linked(helper, cable, former), "a cable at the front or side links nothing: " + cable);
            }
            var evidence = evidence(helper, former);
            helper.assertTrue(evidence != null && evidence.kind() == FederationDomainNodeKind.BEAM_FORMER,
                    "the node is a Beam Former's");
            helper.assertValueEqual(evidence.capacityFactor(), 1, "a 1x1 carries one cable");
            RouterEvidence.write("beamformerlinksthroughitsback", 4, Map.of("back", "linked",
                    "frontAndSide", "unlinked", "kind", "BEAM_FORMER", "capacityFactor", "1"));
            fixtures.close();
        });
    }

    /** A 2x2 former takes a 2x2 bundle across its whole back; its nodes carry the bundle's capacity. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void beamFormerTakesItsBundle(GameTestHelper helper) {
        BlockPos.betweenClosed(CENTER.offset(-4, -1, -1), CENTER.offset(1, 2, 2))
                .forEach(position -> helper.setBlock(position, Blocks.AIR));
        var fixtures = new RouterFixtures(helper);
        var formers = box(CENTER, 1, 2, 2);
        formers.forEach(cell -> place(helper, cell, Direction.EAST));
        var run = CENTER.west(3);
        for (var cell : box(run, 3, 2, 2)) fixtures.placeFederationCable(cell);
        var level = helper.getLevel();
        var phase = new int[1];
        helper.succeedWhen(() -> {
            if (phase[0] == 0) {
                BundledCables.get(level).form(level, new BundleSegment(helper.absolutePos(run),
                        helper.absolutePos(run.offset(2, 1, 1)), Direction.Axis.X));
                phase[0] = 1;
                throw new GameTestAssertException("Waiting for the bundle to settle");
            }
            for (var cell : formers) {
                var node = FederationDomainRegistryAccess.nodeId(level, helper.absolutePos(cell));
                helper.assertTrue(linked(helper, CENTER.west(), node), "the bundle joins former block " + cell);
                helper.assertValueEqual(evidence(helper, node).capacityFactor(), 6, "a 2x2 carries six cables");
            }
            RouterEvidence.write("beamformertakesitsbundle", 2, Map.of("bundle", "linked to all four blocks",
                    "capacityFactor", "6"));
            fixtures.close();
        });
    }

    /** A wrench flips a 2x2 former and turns a 1x1 to the clicked face; the cable at the new back links. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void beamFormerTurnsWithAWrench(GameTestHelper helper) {
        BlockPos.betweenClosed(CENTER.offset(-2, -1, -1), CENTER.offset(2, 2, 5))
                .forEach(position -> helper.setBlock(position, Blocks.AIR));
        var fixtures = new RouterFixtures(helper);
        var square = box(CENTER, 1, 2, 2);
        square.forEach(cell -> place(helper, cell, Direction.EAST));
        fixtures.placeFederationCable(CENTER.east());
        var single = CENTER.south(4);
        place(helper, single, Direction.NORTH);
        var level = helper.getLevel();
        var former = FederationDomainRegistryAccess.nodeId(level, helper.absolutePos(CENTER));
        var player = MockServerPlayers.inLevel(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, AEItems.CERTUS_QUARTZ_WRENCH.stack());
        var phase = new int[1];
        helper.succeedWhen(() -> {
            if (phase[0] == 0) {
                assertFacing(helper, square, Direction.EAST, "Waiting for the square to form");
                helper.assertTrue(!linked(helper, CENTER.east(), former), "the cable at its front links nothing");
                use(helper, player, CENTER, Direction.UP);
                use(helper, player, single, Direction.UP);
                phase[0] = 1;
                throw new GameTestAssertException("Waiting for the flipped square to link");
            }
            assertFacing(helper, square, Direction.WEST, "the wrench flipped the 2x2");
            assertFacing(helper, List.of(single), Direction.UP, "the wrench turned the 1x1 to the clicked face");
            helper.assertTrue(linked(helper, CENTER.east(), former), "the cable at the new back links");
            RouterEvidence.write("beamformerturnswithawrench", 3, Map.of("square", "west", "single", "up",
                    "newBack", "linked"));
            fixtures.close();
        });
    }

    private static boolean linked(GameTestHelper helper, BlockPos cable, FederationDomainNodeId former) {
        var level = helper.getLevel();
        var domain = FederationDomainRegistryAccess.get(level).federationDomainOf(
                FederationDomainRegistryAccess.nodeId(level, helper.absolutePos(cable)));
        return domain.isPresent() && domain.get().nodes().contains(former);
    }

    private static FederationDomainNodeEvidence evidence(GameTestHelper helper, FederationDomainNodeId node) {
        var registry = FederationDomainRegistryAccess.get(helper.getLevel());
        return registry.federationDomainOf(node).flatMap(domain -> registry.nodeEvidence(domain.federationDomainId())
                .stream().filter(evidence -> evidence.nodeId().equals(node)).findFirst()).orElse(null);
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
            helper.assertValueEqual(state.getValue(BeamFormerBlock.FACING), facing, what + ": " + cell);
        }
    }

    private static List<BlockPos> box(BlockPos min, int sx, int sy, int sz) {
        var cells = new ArrayList<BlockPos>();
        for (int x = 0; x < sx; x++) for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) cells.add(min.offset(x, y, z));
        return cells;
    }

    private static void place(GameTestHelper helper, BlockPos position, Direction facing) {
        helper.setBlock(position, BeamRegistration.BEAM_FORMER.get().defaultBlockState()
                .setValue(BeamFormerBlock.FACING, facing));
    }
}
