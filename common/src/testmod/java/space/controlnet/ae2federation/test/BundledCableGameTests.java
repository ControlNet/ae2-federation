package space.controlnet.ae2federation.test;

import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.bundle.BundleContacts;
import space.controlnet.ae2federation.bundle.BundleSegment;
import space.controlnet.ae2federation.bundle.BundledCables;
import space.controlnet.ae2federation.domain.FederationDomainId;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.router.FederationCableBlockEntity;
import space.controlnet.ae2federation.test.router.RouterEvidence;
import space.controlnet.ae2federation.test.router.RouterFixtures;

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
