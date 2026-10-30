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
import space.controlnet.ae2federation.beam.BeamLinkService;
import space.controlnet.ae2federation.beam.BeamRegistration;
import space.controlnet.ae2federation.router.CableVisualConnections;
import space.controlnet.ae2federation.bundle.BundleSegment;
import space.controlnet.ae2federation.bundle.BundledCables;
import space.controlnet.ae2federation.domain.FederationDomainNodeEvidence;
import space.controlnet.ae2federation.domain.FederationDomainNodeId;
import space.controlnet.ae2federation.domain.FederationDomainNodeKind;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.power.BeamConverterBlock;
import space.controlnet.ae2federation.power.CableCrossSection;
import space.controlnet.ae2federation.power.OperatingPowerCosts;
import space.controlnet.ae2federation.power.OperatingPowerRule;
import space.controlnet.ae2federation.power.OperatingPowerService;
import space.controlnet.ae2federation.power.PowerRegistration;
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
        helper.startSequence().thenWaitUntil(() -> {
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
            var status = space.controlnet.ae2federation.router.DeviceStructureUpdater.status(helper.getLevel(),
                    helper.absolutePos(oneByTwo.getFirst()));
            helper.assertTrue(status.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents
                    contents && contents.getKey().equals("ae2federation.structure.invalid.wrong_shape.beam_former"),
                    "a 1x2 must name the former's sizes: " + status.getString());
            assertFacing(helper, majority, Direction.WEST, "three of four blocks face west");
            assertFacing(helper, cabled, Direction.WEST, "no vote on its axis: away from the cable at its east");
            assertFacing(helper, noVote, Direction.EAST, "no vote, no cable: the positive end");
        }).thenExecute(() -> {
            helper.setBlock(two.getFirst(), Blocks.AIR);
            helper.setBlock(three.getFirst(), Blocks.AIR);
        }).thenWaitUntil(() -> {
            for (var cells : List.of(two, three)) {
                for (var cell : cells.subList(1, cells.size())) {
                    var state = helper.getBlockState(cell);
                    helper.assertTrue(!RouterBlock.formed(state), "broken body unforms");
                    helper.assertValueEqual(state.getValue(BeamFormerBlock.SIZE), BeamFormerBlock.Size.SMALL,
                            "broken body resets its size");
                }
            }
        }).thenExecute(fixtures::close).thenExecute(() -> {
            RouterEvidence.write("beamformershapes", 10, Map.of("1x1", "formed", "2x2", "formed", "3x3", "formed",
                    "1x2", "unformed", "2x2x2", "unformed", "majority", "west", "cable", "away from it",
                    "noVote", "positive end", "2x2Reset", "small", "3x3Reset", "small"));
        }).thenSucceed();
    }

    /**
     * A 1x1 former facing east: a cable at its back joins its domain and reaches it with a dense arm, cables at its
     * front and side do neither; its node is a Beam Former's and carries one cable's capacity.
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
            helper.assertValueEqual(arm(helper, CENTER.west(), Direction.EAST), CableVisualConnections.DENSE,
                    "the cable at the back reaches the former with a dense arm");
            helper.assertValueEqual(arm(helper, CENTER.east(), Direction.WEST), CableVisualConnections.NONE,
                    "the cable at the front stays closed toward it");
            helper.assertValueEqual(arm(helper, CENTER.north(), Direction.SOUTH), CableVisualConnections.NONE,
                    "the cable at the side stays closed toward it");
            var evidence = evidence(helper, former);
            helper.assertTrue(evidence != null && evidence.kind() == FederationDomainNodeKind.BEAM_FORMER,
                    "the node is a Beam Former's");
            helper.assertValueEqual(evidence.capacityFactor(), 1, "a 1x1 carries one cable");
            RouterEvidence.write("beamformerlinksthroughitsback", 5, Map.of("back", "linked",
                    "frontAndSide", "unlinked", "kind", "BEAM_FORMER", "capacityFactor", "1", "arm", "back-only"));
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

    /**
     * Two 1x1 formers facing each other 17 blocks apart link: the cables at their backs join one domain, and Jade reads
     * the beam's length. A stone in the beam unlinks it only after the debounce; taken away, the beam links again,
     * again only after the debounce.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void beamFormerPairLinksAndDebounces(GameTestHelper helper) {
        var fixtures = new RouterFixtures(helper);
        var west = new BlockPos(2, 2, 10);
        var east = new BlockPos(20, 2, 10);
        place(helper, west, Direction.EAST);
        place(helper, east, Direction.WEST);
        fixtures.placeFederationCable(west.west());
        fixtures.placeFederationCable(east.east());
        var stone = new BlockPos(11, 2, 10);
        var level = helper.getLevel();
        var farCable = FederationDomainRegistryAccess.nodeId(level, helper.absolutePos(east.east()));
        var phase = new int[1];
        var since = new long[1];
        helper.succeedWhen(() -> {
            long now = level.getGameTime();
            if (phase[0] == 0) {
                helper.assertTrue(linked(helper, west.west(), farCable), "Waiting for the beam to link");
                var status = BeamLinkService.get(level).status(helper.absolutePos(west));
                helper.assertValueEqual(status.kind(), BeamLinkService.Status.Kind.LINKED, "Jade reads the beam");
                helper.assertValueEqual(status.value(), 17, "17 blocks between the fronts");
                helper.setBlock(stone, Blocks.STONE);
                since[0] = now;
                phase[0] = 1;
                throw new GameTestAssertException("Waiting for the stone to unlink the beam");
            }
            if (phase[0] == 1) {
                if (now - since[0] < 10) {
                    helper.assertTrue(linked(helper, west.west(), farCable), "the beam holds through the debounce");
                    throw new GameTestAssertException("Waiting out the debounce");
                }
                helper.assertTrue(!linked(helper, west.west(), farCable), "Waiting for the stone to unlink the beam");
                helper.assertTrue(now - since[0] >= 20, "unlinked only after 20 ticks blocked, not " + (now - since[0]));
                var status = BeamLinkService.get(level).status(helper.absolutePos(west));
                helper.assertValueEqual(status.kind(), BeamLinkService.Status.Kind.BLOCKED, "Jade reads the blocker");
                helper.assertValueEqual(status.blocker(), helper.absolutePos(stone), "at the stone");
                helper.setBlock(stone, Blocks.AIR);
                since[0] = now;
                phase[0] = 2;
                throw new GameTestAssertException("Waiting for the beam to link again");
            }
            helper.assertTrue(linked(helper, west.west(), farCable), "Waiting for the beam to link again");
            helper.assertTrue(now - since[0] >= 20, "linked again only after 20 ticks clear, not " + (now - since[0]));
            RouterEvidence.write("beamformerpairlinksanddebounces", 6, Map.of("linked", "17 blocks", "held", "10 ticks",
                    "unlinked", "after 20 ticks", "blocker", "reported", "relinked", "after 20 ticks", "jade", "linked"));
            fixtures.close();
        });
    }

    /** Glass, a glass pane, water and AE2 quartz glass in a 2x2 beam do not block it. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void beamFormerPassesGlassAndWater(GameTestHelper helper) {
        var fixtures = new RouterFixtures(helper);
        var west = box(new BlockPos(2, 2, 10), 1, 2, 2);
        var east = box(new BlockPos(14, 2, 10), 1, 2, 2);
        west.forEach(cell -> place(helper, cell, Direction.EAST));
        east.forEach(cell -> place(helper, cell, Direction.WEST));
        fixtures.placeFederationCable(new BlockPos(1, 2, 10));
        fixtures.placeFederationCable(new BlockPos(15, 2, 10));
        helper.setBlock(new BlockPos(5, 2, 10), Blocks.GLASS);
        helper.setBlock(new BlockPos(6, 3, 11), Blocks.GLASS_PANE);
        helper.setBlock(new BlockPos(8, 2, 11), Blocks.WATER);
        helper.setBlock(new BlockPos(10, 3, 10), appeng.core.definitions.AEBlocks.QUARTZ_GLASS.block());
        var level = helper.getLevel();
        var farCable = FederationDomainRegistryAccess.nodeId(level, helper.absolutePos(new BlockPos(15, 2, 10)));
        helper.succeedWhen(() -> {
            helper.assertTrue(linked(helper, new BlockPos(1, 2, 10), farCable), "the beam links through glass and water");
            for (var body : List.of(west, east)) {
                var anchor = (space.controlnet.ae2federation.beam.BeamFormerBlockEntity)
                        level.getBlockEntity(helper.absolutePos(body.getFirst()));
                helper.assertTrue(anchor.look() != null && anchor.look().side() == 2,
                        "both anchors sync the large portal look");
                var other = body == west ? east : west;
                helper.assertValueEqual(anchor.look().partner(), helper.absolutePos(other.getFirst()),
                        "each look names the opposite anchor");
                var copy = new space.controlnet.ae2federation.beam.BeamFormerBlockEntity(anchor.getBlockPos(),
                        anchor.getBlockState());
                copy.handleUpdateTag(anchor.getUpdateTag(level.registryAccess()), level.registryAccess());
                helper.assertValueEqual(copy.look(), anchor.look(), "the full look survives the client update tag");
                for (var member : body.subList(1, body.size())) {
                    var entity = (space.controlnet.ae2federation.beam.BeamFormerBlockEntity)
                            level.getBlockEntity(helper.absolutePos(member));
                    helper.assertTrue(entity.look() == null, "non-anchor members never draw portals");
                }
            }
            RouterEvidence.write("beamformerpassesglassandwater", 1, Map.of("glassPaneWaterQuartzGlass", "pass"));
            fixtures.close();
        });
    }

    /**
     * Only the nearest former counts: of two facing back on one line, the nearer one links and the farther one has no
     * partner. A 1x1 facing a 2x2 does not link and reads "another size".
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void beamFormerPairsTheNearestOfItsSize(GameTestHelper helper) {
        var fixtures = new RouterFixtures(helper);
        var a = new BlockPos(2, 2, 4);
        var b = new BlockPos(10, 2, 4);
        var c = new BlockPos(20, 2, 4);
        place(helper, a, Direction.EAST);
        place(helper, b, Direction.WEST);
        place(helper, c, Direction.WEST);
        fixtures.placeFederationCable(a.west());
        fixtures.placeFederationCable(b.east());
        fixtures.placeFederationCable(c.east());
        var single = new BlockPos(2, 2, 14);
        place(helper, single, Direction.EAST);
        box(new BlockPos(12, 2, 14), 1, 2, 2).forEach(cell -> place(helper, cell, Direction.WEST));
        var level = helper.getLevel();
        var nodeB = FederationDomainRegistryAccess.nodeId(level, helper.absolutePos(b.east()));
        var nodeC = FederationDomainRegistryAccess.nodeId(level, helper.absolutePos(c.east()));
        helper.succeedWhen(() -> {
            helper.assertTrue(linked(helper, a.west(), nodeB), "the nearer former links");
            helper.assertTrue(!linked(helper, a.west(), nodeC), "the farther one does not");
            var service = BeamLinkService.get(level);
            helper.assertValueEqual(service.status(helper.absolutePos(c)).kind(), BeamLinkService.Status.Kind.NO_PARTNER,
                    "the farther one has no partner");
            helper.assertValueEqual(service.status(helper.absolutePos(single)).kind(),
                    BeamLinkService.Status.Kind.WRONG_SIZE, "a 1x1 facing a 2x2");
            RouterEvidence.write("beamformerpairsthenearestofitssize", 4, Map.of("nearer", "linked",
                    "farther", "unlinked", "fartherStatus", "no partner", "otherSize", "wrong size"));
            fixtures.close();
        });
    }

    /**
     * With operating power required, a linked 3x3 pair counts as 3x3 cable (D6): a 1x1x2 converter at a plain cable
     * matches its domain until the beam links, and then no longer does. The domain pays the beam once:
     * 9 x 5^1.05 AE for five blocks between the fronts.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void beamFormerPairNeedsItsSizeOfConverter(GameTestHelper helper) {
        var fixtures = new RouterFixtures(helper);
        OperatingPowerRule.set(true, OperatingPowerRule.DEFAULT_MULTIPLIER);
        box(new BlockPos(2, 2, 8), 1, 3, 3).forEach(cell -> place(helper, cell, Direction.EAST));
        box(new BlockPos(8, 2, 8), 1, 3, 3).forEach(cell -> place(helper, cell, Direction.WEST));
        var cable = new BlockPos(1, 2, 8);
        fixtures.placeFederationCable(cable);
        fixtures.placeFederationCable(new BlockPos(9, 2, 8));
        for (var z = 6; z <= 7; z++) {
            helper.setBlock(new BlockPos(1, 2, z), PowerRegistration.BEAM_CONVERTER.get().defaultBlockState()
                    .setValue(BeamConverterBlock.FACING, Direction.SOUTH));
        }
        var stone = new BlockPos(5, 3, 9);
        helper.setBlock(stone, Blocks.STONE);
        var level = helper.getLevel();
        var cableNode = FederationDomainRegistryAccess.nodeId(level, helper.absolutePos(cable));
        var farCable = FederationDomainRegistryAccess.nodeId(level, helper.absolutePos(new BlockPos(9, 2, 8)));
        var phase = new int[1];
        helper.succeedWhen(() -> {
            var registry = FederationDomainRegistryAccess.get(level);
            var domain = registry.federationDomainOf(cableNode)
                    .orElseThrow(() -> new GameTestAssertException("Waiting for the cable's domain"));
            var reading = OperatingPowerService.reading(level.getServer(), domain.federationDomainId())
                    .orElseThrow(() -> new GameTestAssertException("Waiting for a power reading"));
            if (phase[0] == 0) {
                helper.assertTrue(!reading.converters().isEmpty(), "Waiting for the converter to join");
                helper.assertValueEqual(reading.section(), CableCrossSection.ONE_BY_ONE, "unlinked, plain cable");
                helper.assertTrue(reading.converters().stream().allMatch(OperatingPowerService.ConverterReading::matches),
                        "a 1x1x2 converter matches plain cable");
                helper.assertTrue(!reading.use().byGroup().containsKey(OperatingPowerCosts.Group.BEAMS),
                        "no beam, no beam cost");
                helper.setBlock(stone, Blocks.AIR);
                phase[0] = 1;
                throw new GameTestAssertException("Waiting for the beam to link");
            }
            helper.assertTrue(linked(helper, cable, farCable), "Waiting for the beam to link");
            helper.assertValueEqual(reading.section(), CableCrossSection.THREE_BY_THREE, "the pair counts as 3x3");
            helper.assertTrue(reading.converters().stream().noneMatch(OperatingPowerService.ConverterReading::matches),
                    "the 1x1x2 converter no longer matches");
            var beam = reading.use().byGroup().getOrDefault(OperatingPowerCosts.Group.BEAMS, 0.0);
            helper.assertTrue(Math.abs(beam - 9 * Math.pow(5, 1.05) * 2) < 1e-9, "one 3x3 beam of 5 blocks, not " + beam);
            RouterEvidence.write("beamformerpairneedsitssizeofconverter", 4, Map.of("unlinked", "1x1 matches",
                    "linked", "3x3", "converter", "no longer matches", "beamCost", String.valueOf(beam)));
            OperatingPowerRule.set(false, OperatingPowerRule.DEFAULT_MULTIPLIER);
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
            var size = switch (cells.size()) {
                case 1 -> BeamFormerBlock.Size.SMALL;
                case 4 -> BeamFormerBlock.Size.LARGE;
                case 9 -> BeamFormerBlock.Size.HUGE;
                default -> throw new IllegalArgumentException("Not a former square");
            };
            helper.assertValueEqual(state.getValue(BeamFormerBlock.SIZE), size, what + ": body size");
            helper.assertTrue(RouterBlock.formed(state), what + ": " + cell + " forms");
            helper.assertValueEqual(state.getValue(BeamFormerBlock.FACING), facing, what + ": " + cell);
        }
    }

    private static List<BlockPos> box(BlockPos min, int sx, int sy, int sz) {
        var cells = new ArrayList<BlockPos>();
        for (int x = 0; x < sx; x++) for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) cells.add(min.offset(x, y, z));
        return cells;
    }

    /** How the cable at {@code cable} draws its side toward {@code side}. */
    private static int arm(GameTestHelper helper, BlockPos cable, Direction side) {
        return CableVisualConnections.kind(CableVisualConnections.connections(helper.getLevel(),
                helper.absolutePos(cable)), side);
    }

    private static void place(GameTestHelper helper, BlockPos position, Direction facing) {
        helper.setBlock(position, BeamRegistration.BEAM_FORMER.get().defaultBlockState()
                .setValue(BeamFormerBlock.FACING, facing));
    }
}
