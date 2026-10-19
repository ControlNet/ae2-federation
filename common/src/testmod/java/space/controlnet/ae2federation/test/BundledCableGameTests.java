package space.controlnet.ae2federation.test;

import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import appeng.core.definitions.AEItems;
import space.controlnet.ae2federation.bundle.BundleContacts;
import space.controlnet.ae2federation.bundle.BundleInteraction;
import space.controlnet.ae2federation.bundle.BundleSegment;
import space.controlnet.ae2federation.bundle.BundledCables;
import space.controlnet.ae2federation.domain.FederationDomainId;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.router.CableVisualConnections;
import space.controlnet.ae2federation.router.FederationCableBlockEntity;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.test.router.RouterEvidence;
import space.controlnet.ae2federation.test.router.RouterFixtures;
import space.controlnet.ae2federation.test.world.MockServerPlayers;

/**
 * Bundled Federation Cables in the world: formed straight from the server's bundle record, as the wrench will form
 * them, then judged by the domain they join. A bundle links to a Router body only through a whole contact, never to an
 * ordinary cable, and breaking one cell reverts its layer and splits the bundle.
 */
@PrefixGameTestTemplate(false)
public final class BundledCableGameTests {
    private static final BlockPos CENTER = new BlockPos(6, 6, 6);

    private BundledCableGameTests() {
    }

    /**
     * Router A (a 1x2x2 body) - a 2x2 bundle four long - Router B. B starts as half a body, covering two of the end
     * face's four cells, so it stays apart; completing B afterwards links it with no neighbour update. An ordinary
     * cable on the bundle's side never joins. Breaking a middle cell splits the bundle and A from B.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void bundleLinksThroughWholeEndFaces(GameTestHelper helper) {
        clear(helper);
        var fixtures = new RouterFixtures(helper);
        var west = CENTER.west(3);
        var east = CENTER.east(2);
        for (var pos : BlockPos.betweenClosed(west, west.offset(0, 1, 1))) fixtures.placeRouter(pos.immutable());
        for (var pos : BlockPos.betweenClosed(CENTER.west(2), CENTER.offset(1, 1, 1))) {
            fixtures.placeFederationCable(pos.immutable());
        }
        fixtures.placeRouter(east);
        fixtures.placeRouter(east.south());
        var stray = CENTER.west().above(2);
        fixtures.placeFederationCable(stray);
        var phase = new int[1];
        helper.succeedWhen(() -> {
            var level = helper.getLevel();
            if (phase[0] == 0) {
                var segment = new BundleSegment(helper.absolutePos(CENTER.west(2)),
                        helper.absolutePos(CENTER.offset(1, 1, 1)), Direction.Axis.X);
                var bundles = BundledCables.get(level);
                helper.assertValueEqual(bundles.formable(level, segment), Optional.empty(), "the cables must be free");
                bundles.form(level, segment);
                phase[0] = 1;
                throw new GameTestAssertException("Waiting for the bundle to link");
            }
            if (phase[0] == 1) {
                helper.assertTrue(sameDomain(helper, west, CENTER), "Waiting for Router A to join the bundle");
                helper.assertTrue(sameDomain(helper, west, CENTER.offset(1, 1, 1)), "Waiting for the whole bundle");
                helper.assertFalse(sameDomain(helper, CENTER, east), "half a Router body is no contact");
                helper.assertFalse(sameDomain(helper, CENTER, stray), "an ordinary cable never joins a bundle");
                helper.assertValueEqual(capacity(helper, CENTER), 48, "a 2x2 bundle carries six cables' capacity");
                fixtures.placeRouter(east.above());
                fixtures.placeRouter(east.above().south());
                phase[0] = 2;
                throw new GameTestAssertException("Waiting for Router B's body to form");
            }
            if (phase[0] == 2) {
                helper.assertTrue(sameDomain(helper, west, east), "Waiting for the completed body to link");
                helper.setBlock(CENTER.west().above().south(), Blocks.AIR);
                phase[0] = 3;
                throw new GameTestAssertException("Waiting for the broken layer to split the bundle");
            }
            var bundles = BundledCables.get(level);
            helper.assertTrue(bundles.at(helper.absolutePos(CENTER.west())).isEmpty(),
                    "the rest of the broken layer is ordinary cable");
            helper.assertTrue(bundles.at(helper.absolutePos(CENTER.west(2))).isPresent(), "the west part stays");
            helper.assertTrue(bundles.at(helper.absolutePos(CENTER)).isPresent(), "the east part stays");
            helper.assertTrue(sameDomain(helper, west, CENTER.west(2)), "Waiting for A to keep the west part");
            helper.assertTrue(sameDomain(helper, east, CENTER), "Waiting for B to keep the east part");
            helper.assertFalse(sameDomain(helper, west, east), "Waiting for the split to part A from B");
            RouterEvidence.write("bundlelinksthroughwholeendfaces", 6, Map.of("partialEndApart", "true",
                    "completedBodyLinks", "true", "ordinaryCableApart", "true", "capacity", "48",
                    "breakRevertsLayer", "true", "breakSplits", "true"));
            clear(helper);
            fixtures.close();
        });
    }

    /**
     * A 2x2 bundle's side links to a device covering that side's full width: one Router covers one of two cells and
     * stays apart, a 1x2 body links. A 1x2 bundle's narrow side is one cell wide, so one Router links there.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void bundleSidesNeedTheirFullWidth(GameTestHelper helper) {
        clear(helper);
        var fixtures = new RouterFixtures(helper);
        // 2x2 along x at z 4..5; 1x2 along x at z 7..8, one high.
        var square = new BlockPos(CENTER.getX() - 1, CENTER.getY(), CENTER.getZ() - 2);
        var flat = new BlockPos(CENTER.getX() - 1, CENTER.getY(), CENTER.getZ() + 1);
        for (var pos : BlockPos.betweenClosed(square, square.offset(2, 1, 1))) fixtures.placeFederationCable(pos.immutable());
        for (var pos : BlockPos.betweenClosed(flat, flat.offset(2, 0, 1))) fixtures.placeFederationCable(pos.immutable());
        var squareSide = square.offset(1, 0, -1);
        var flatSide = flat.offset(1, 0, 2);
        fixtures.placeRouter(squareSide);
        fixtures.placeRouter(flatSide);
        var phase = new int[1];
        helper.succeedWhen(() -> {
            var level = helper.getLevel();
            if (phase[0] == 0) {
                var bundles = BundledCables.get(level);
                bundles.form(level, new BundleSegment(helper.absolutePos(square), helper.absolutePos(square.offset(2, 1, 1)),
                        Direction.Axis.X));
                bundles.form(level, new BundleSegment(helper.absolutePos(flat), helper.absolutePos(flat.offset(2, 0, 1)),
                        Direction.Axis.X));
                phase[0] = 1;
                throw new GameTestAssertException("Waiting for the bundles to settle");
            }
            if (phase[0] == 1) {
                helper.assertTrue(sameDomain(helper, flatSide, flat), "Waiting for one Router on a one-wide side");
                helper.assertFalse(sameDomain(helper, squareSide, square), "one Router covers half a two-wide side");
                var summary = summary(helper, square);
                helper.assertValueEqual(summary.partial().size(), 1, "the half contact is reported");
                helper.assertValueEqual(summary.partial().get(0).covered(), 1, "it covers one cell");
                fixtures.placeRouter(squareSide.above());
                phase[0] = 2;
                throw new GameTestAssertException("Waiting for the 1x2 body to form");
            }
            helper.assertTrue(sameDomain(helper, squareSide, square), "Waiting for the full-width body to link");
            var summary = summary(helper, square);
            helper.assertValueEqual(summary.sides(), 1, "one side contact");
            helper.assertValueEqual(summary.partial().size(), 0, "no partial contact left");
            RouterEvidence.write("bundlesidesneedtheirfullwidth", 5, Map.of("narrowSideOneRouter", "true",
                    "halfWidthApart", "true", "halfWidthReported", "true", "fullWidthLinks", "true",
                    "summarySides", "1"));
            clear(helper);
            fixtures.close();
        });
    }

    /**
     * A survival player: nine Federation Cables right-clicked onto a 2x2 bundle's end face add a layer of four, onto its
     * side a branch of four, and the last cable is too few for another branch. Shift+right-click with a wrench
     * dismantles one cell and reverts its layer. Three wrench clicks sent to the server refuse a 1x2 bundle, which
     * players cannot build today, and form a 2x2 bundle once every position holds a cable.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void bundlePlayerExtendsBranchesAndDismantles(GameTestHelper helper) {
        clear(helper);
        var fixtures = new RouterFixtures(helper);
        var run = CENTER.west(2);
        for (var pos : BlockPos.betweenClosed(run, CENTER.offset(0, 1, 1))) fixtures.placeFederationCable(pos.immutable());
        var level = helper.getLevel();
        var bundles = BundledCables.get(level);
        bundles.form(level, new BundleSegment(helper.absolutePos(run), helper.absolutePos(CENTER.offset(0, 1, 1)),
                Direction.Axis.X));
        var player = MockServerPlayers.inLevel(helper);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(CENTER.above(3))));
        player.setGameMode(GameType.SURVIVAL);
        var cables = new ItemStack(RouterRegistration.FEDERATION_CABLE_ITEM.get(), 9);
        player.setItemInHand(InteractionHand.MAIN_HAND, cables);

        use(helper, player, CENTER, Direction.EAST);
        helper.assertTrue(bundled(helper, CENTER.east().above().south()), "the end face grew a layer");
        helper.assertValueEqual(bundles.at(helper.absolutePos(CENTER)).orElseThrow().segment().length(), 4,
                "the layer joined the run");
        helper.assertValueEqual(player.getMainHandItem().getCount(), 5, "a 2x2 layer uses four cables");

        use(helper, player, CENTER.west().south(), Direction.SOUTH);
        var branch = CENTER.west().south(2);
        helper.assertTrue(bundled(helper, branch) && bundled(helper, branch.east().above()), "the side grew a branch");
        helper.assertValueEqual(bundles.at(helper.absolutePos(branch)).orElseThrow().bundle().id(),
                bundles.at(helper.absolutePos(run)).orElseThrow().bundle().id(), "the branch joined the bundle");
        helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "the branch used four cables");

        use(helper, player, CENTER.west(), Direction.NORTH);
        helper.assertTrue(helper.getBlockState(CENTER.west().north()).isAir(), "one cable is too few for a branch");
        helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "nothing was used");

        player.setItemInHand(InteractionHand.MAIN_HAND, AEItems.CERTUS_QUARTZ_WRENCH.stack());
        player.setShiftKeyDown(true);
        use(helper, player, run.above().south(), Direction.UP);
        player.setShiftKeyDown(false);
        helper.assertTrue(helper.getBlockState(run.above().south()).isAir(), "the wrench dismantled the cell");
        helper.assertFalse(bundled(helper, run), "the rest of its layer reverted");
        helper.assertTrue(bundled(helper, run.east()), "the rest of the bundle stays");

        var lineStart = CENTER.offset(-2, 3, -3);
        for (var pos : BlockPos.betweenClosed(lineStart, lineStart.offset(1, 1, 1))) {
            fixtures.placeFederationCable(pos.immutable());
        }
        BundleInteraction.form(player, helper.absolutePos(lineStart), helper.absolutePos(lineStart.south()),
                helper.absolutePos(lineStart.offset(1, 0, 1)), Direction.UP);
        helper.assertFalse(bundled(helper, lineStart), "a 1x2 cross-section is not built today");
        var corner = lineStart.offset(0, 1, 1);
        BundleInteraction.form(player, helper.absolutePos(lineStart), helper.absolutePos(corner),
                helper.absolutePos(lineStart.east(2)), Direction.UP);
        helper.assertFalse(bundled(helper, lineStart), "a position without a cable refuses the whole box");
        BundleInteraction.form(player, helper.absolutePos(lineStart), helper.absolutePos(corner),
                helper.absolutePos(lineStart.east()), Direction.UP);
        var square = bundles.at(helper.absolutePos(lineStart));
        helper.assertTrue(square.isPresent(), "three valid clicks form the bundle");
        helper.assertValueEqual(square.get().bundle().shape().toString(), "2x2", "a 2x2 bundle along x");
        helper.assertValueEqual(square.get().segment().axis(), Direction.Axis.X, "the third click set the axis");
        RouterEvidence.write("bundleplayerextendsbranchesanddismantles", 8, Map.of("extendUsesFour", "true",
                "branchUsesFour", "true", "tooFewRefused", "true", "wrenchDismantles", "true",
                "dismantleRevertsLayer", "true", "lineRefused", "true", "missingCableRefuses", "true",
                "threeClicksForm", "true"));
        clear(helper);
        fixtures.close();
        helper.succeed();
    }

    /**
     * What a 2x2 bundle three long shows, read on the server as a client reads it: one shell, so a cell's outline
     * reaches the faces it shares with other cells and stops 2 voxels short of the outer ones; beams that run along the
     * bundle, end at a closed end, and reach a Router body that covers the whole end face; and no join to the
     * ordinary cable beyond the other end, which joined the cables before they were bundled.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void bundleShowsOneShellClosedToOrdinaryCables(GameTestHelper helper) {
        clear(helper);
        var fixtures = new RouterFixtures(helper);
        var west = CENTER.west();
        var east = CENTER.east();
        for (var pos : BlockPos.betweenClosed(west, east.offset(0, 1, 1))) fixtures.placeFederationCable(pos.immutable());
        for (var pos : BlockPos.betweenClosed(west.west(), west.west().offset(0, 1, 1))) {
            fixtures.placeRouter(pos.immutable());
        }
        var stray = east.east();
        fixtures.placeFederationCable(stray);
        var level = helper.getLevel();
        helper.assertValueEqual(kind(helper, stray, Direction.WEST), CableVisualConnections.CABLE,
                "two ordinary cables join");
        BundledCables.get(level).form(level, new BundleSegment(helper.absolutePos(west),
                helper.absolutePos(east.offset(0, 1, 1)), Direction.Axis.X));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(kind(helper, stray, Direction.WEST), CableVisualConnections.NONE,
                    "an ordinary cable stays closed toward a bundle");
            helper.assertValueEqual(CableVisualConnections.mask(level, helper.absolutePos(east)),
                    bits(Direction.WEST), "the beam ends at the closed end");
            helper.assertValueEqual(CableVisualConnections.mask(level, helper.absolutePos(CENTER)),
                    bits(Direction.EAST, Direction.WEST), "the beam runs along the bundle");
            helper.assertValueEqual(CableVisualConnections.mask(level, helper.absolutePos(west)),
                    bits(Direction.EAST, Direction.WEST), "Waiting for the beam to reach the Router body");
            helper.assertValueEqual(outline(helper, CENTER), new AABB(0, 2 / 16d, 2 / 16d, 1, 1, 1),
                    "a cell's outline is its part of one shell");
            helper.assertValueEqual(outline(helper, east.above().south()), new AABB(0, 0, 0, 14 / 16d, 14 / 16d,
                    14 / 16d), "the closed end stops 2 voxels short");
            var cable = (FederationCableBlockEntity) helper.getBlockEntity(CENTER);
            helper.assertTrue(cable.bundleSegment() != null, "the server's cable knows its segment");
            RouterEvidence.write("bundleshowsoneshellclosedtoordinarycables", 8, Map.of("ordinaryJoinsBefore", "true",
                    "ordinaryClosedAfter", "true", "closedEndBeam", "true", "runningBeam", "true",
                    "deviceBeam", "true", "sharedFacesOpen", "true", "closedEndInset", "true",
                    "serverSegment", "true"));
            clear(helper);
            fixtures.close();
        });
    }

    private static int kind(GameTestHelper helper, BlockPos relative, Direction side) {
        return CableVisualConnections.kind(CableVisualConnections.connections(helper.getLevel(),
                helper.absolutePos(relative)), side);
    }

    private static int bits(Direction... sides) {
        int mask = 0;
        for (var side : sides) mask |= 1 << java.util.List.of(CableVisualConnections.DIRECTIONS).indexOf(side);
        return mask;
    }

    private static AABB outline(GameTestHelper helper, BlockPos relative) {
        var absolute = helper.absolutePos(relative);
        return helper.getLevel().getBlockState(absolute).getShape(helper.getLevel(), absolute).bounds();
    }

    /** Right-clicks {@code face} of {@code relative} with the main hand, as the server handles a player's click. */
    private static void use(GameTestHelper helper, ServerPlayer player, BlockPos relative, Direction face) {
        var absolute = helper.absolutePos(relative);
        var hit = new BlockHitResult(Vec3.atCenterOf(absolute).relative(face, 0.5), face, absolute, false);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    private static boolean bundled(GameTestHelper helper, BlockPos relative) {
        return BundledCables.get(helper.getLevel()).at(helper.absolutePos(relative)).isPresent();
    }

    /** Clears the area; removing the cables also removes their bundles, which the level keeps between tests. */
    private static void clear(GameTestHelper helper) {
        BlockPos.betweenClosed(CENTER.offset(-4, -1, -3), CENTER.offset(4, 3, 3))
                .forEach(position -> helper.setBlock(position, Blocks.AIR));
    }

    private static boolean sameDomain(GameTestHelper helper, BlockPos first, BlockPos second) {
        var a = domain(helper, first);
        return a.isPresent() && a.equals(domain(helper, second));
    }

    private static Optional<FederationDomainId> domain(GameTestHelper helper, BlockPos relative) {
        var level = helper.getLevel();
        return FederationDomainRegistryAccess.get(level)
                .federationDomainOf(FederationDomainRegistryAccess.nodeId(level, helper.absolutePos(relative)))
                .map(domain -> domain.federationDomainId());
    }

    /** The capacity the cable sends its clients' flow renderer. */
    private static int capacity(GameTestHelper helper, BlockPos relative) {
        var cable = (FederationCableBlockEntity) helper.getBlockEntity(relative);
        return cable.getUpdateTag(helper.getLevel().registryAccess()).getInt("Capacity");
    }

    private static BundleContacts.Summary summary(GameTestHelper helper, BlockPos relative) {
        var level = helper.getLevel();
        var membership = BundledCables.get(level).at(helper.absolutePos(relative));
        helper.assertTrue(membership.isPresent(), "the cell at " + relative + " must be bundled");
        return BundleContacts.summary(level, membership.get().bundle());
    }
}
