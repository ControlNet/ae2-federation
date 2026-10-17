package space.controlnet.ae2federation.test;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.domain.FederationDomainId;
import space.controlnet.ae2federation.domain.FederationDomainRegistry;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.power.BeamConverterBlock;
import space.controlnet.ae2federation.power.BeamConverterBlockEntity;
import space.controlnet.ae2federation.power.OperatingPowerRule;
import space.controlnet.ae2federation.power.OperatingPowerService;
import space.controlnet.ae2federation.power.PowerLedger;
import space.controlnet.ae2federation.power.PowerRegistration;
import space.controlnet.ae2federation.router.FederationCableBlockEntity;
import space.controlnet.ae2federation.router.RouterBlock;
import space.controlnet.ae2federation.test.router.RouterEvidence;
import space.controlnet.ae2federation.test.router.RouterFixtures;

/**
 * With operating power required, a domain runs on the FE in its Beam Converters. The scene: a Switch joining networks A
 * (north) and B (south), two Federation Cables east of it, and converters at the cables. The domain uses 3 FE/t: the
 * Switch 2 and each cable 0.5.
 */
@PrefixGameTestTemplate(false)
public final class OperatingPowerGameTests {
    private static final BlockPos SWITCH = new BlockPos(4, 4, 6);
    private static final double USE = 3.0;

    private OperatingPowerGameTests() {
    }

    /**
     * No converter stops the domain; a 1x1x2 converter fed 200 FE runs it once more than 30 ticks have passed with
     * power, drains at 3 FE/t, stops the tick it is empty, keeps drawing while stopped, and runs again on input above
     * use. Turning the requirement off runs it on nothing.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1200, required = true, manualOnly = true)
    public static void operatingPowerRunsADomain(GameTestHelper helper) {
        var scene = new Scene(helper);
        var front = SWITCH.east(3);
        var back = SWITCH.east(4);
        var phase = new int[1];
        var waited = new int[1];
        // A domain that runs again too early would pass the waits below anyway; record it and fail at the end.
        var early = new StringBuilder();
        helper.succeedWhen(() -> {
            scene.awaitDomain();
            var domain = scene.domain();
            switch (phase[0]) {
                case 0 -> {
                    helper.assertTrue(scene.registry().unpowered(domain), "Waiting for the converterless domain to stop");
                    helper.assertTrue(!scene.running(), "a stopped domain links nothing");
                    helper.assertTrue(scene.cable().unpowered(), "Waiting for the cable's beam to learn it");
                    scene.converter(front, Direction.WEST);
                    scene.converter(back, Direction.WEST);
                    phase[0] = 1;
                    throw new GameTestAssertException("Waiting for the converter to form");
                }
                case 1 -> {
                    helper.assertTrue(RouterBlock.formed(helper.getBlockState(back)), "Waiting for the converter to form");
                    helper.assertValueEqual(scene.insert(back, Direction.EAST, 200), 200, "FE into the converter's back");
                    phase[0] = 2;
                    throw new GameTestAssertException("Waiting for the domain to run");
                }
                case 2 -> {
                    waited[0]++;
                    helper.assertTrue(!scene.registry().unpowered(domain), "Waiting for power to hold 30 ticks");
                    if (waited[0] <= PowerLedger.RESTART_TICKS) early.append(" fed run after ").append(waited[0]);
                    helper.assertTrue(scene.running(), "A and B share a running domain again");
                    helper.assertTrue(!scene.cable().unpowered(), "Waiting for the cable's beam to learn it runs");
                    var reading = scene.reading();
                    helper.assertValueEqual(reading.use().total(), USE, "the domain's use");
                    helper.assertValueEqual(reading.capacity(), 2 * BeamConverterBlockEntity.CAPACITY,
                            "two converter blocks' buffers");
                    helper.assertTrue(reading.stored() < 200 - USE * PowerLedger.RESTART_TICKS, "the buffer drains");
                    phase[0] = 3;
                    throw new GameTestAssertException("Waiting for the buffer to run dry");
                }
                case 3 -> {
                    helper.assertTrue(scene.registry().unpowered(domain), "Waiting for the empty buffer to stop the domain");
                    helper.assertValueEqual(scene.reading().stored(), 0.0, "a stopped domain keeps drawing");
                    phase[0] = 4;
                    waited[0] = 0;
                    throw new GameTestAssertException("Feeding 5 FE/t");
                }
                case 4 -> {
                    waited[0]++;
                    scene.insert(back, Direction.UP, 5);
                    helper.assertTrue(!scene.registry().unpowered(domain), "Waiting for input above use to run it");
                    if (waited[0] <= PowerLedger.RESTART_TICKS) early.append(" restart after ").append(waited[0]);
                    OperatingPowerRule.set(false, OperatingPowerRule.DEFAULT_MULTIPLIER);
                    helper.setBlock(front, Blocks.AIR);
                    phase[0] = 5;
                    throw new GameTestAssertException("Waiting with the requirement off");
                }
                default -> {
                    helper.assertTrue(!scene.registry().unpowered(domain) && scene.running(),
                            "with the requirement off, a domain without a converter runs");
                    helper.assertTrue(early.isEmpty(), "the domain ran too early:" + early);
                    RouterEvidence.write("operatingpowerrunsadomain", 8, Map.of(
                            "noConverter", "stopped", "fed", "running", "restart", "after 30 ticks", "use", "3 FE/t",
                            "drained", "stopped", "keepsDrawing", "true", "requirementOff", "running",
                            "beam", "unpowered then running"));
                    scene.close();
                }
            }
        });
    }

    /**
     * A 1x2x4 converter does not match a domain of plain cable and supplies nothing, however full; a creative 1x1x2
     * converter added on the other cable runs the domain without FE and never empties.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 600, required = true, manualOnly = true)
    public static void operatingPowerNeedsAMatchingConverter(GameTestHelper helper) {
        var scene = new Scene(helper);
        var phase = new int[1];
        var waited = new int[1];
        helper.succeedWhen(() -> {
            scene.awaitDomain();
            var domain = scene.domain();
            switch (phase[0]) {
                case 0 -> {
                    for (var x = 3; x <= 6; x++) {
                        for (var y = 0; y <= 1; y++) scene.converter(SWITCH.offset(x, y, 0), Direction.WEST);
                    }
                    phase[0] = 1;
                    throw new GameTestAssertException("Waiting for the 1x2x4 converter to form");
                }
                case 1 -> {
                    helper.assertTrue(RouterBlock.formed(helper.getBlockState(SWITCH.east(6))), "Waiting for it to form");
                    helper.assertValueEqual(scene.insert(SWITCH.east(6), Direction.EAST, 16000), 16000, "FE in");
                    phase[0] = 2;
                    throw new GameTestAssertException("Waiting 40 ticks");
                }
                case 2 -> {
                    // Longer than a matching converter would take to run the domain.
                    if (++waited[0] < 40) throw new GameTestAssertException("Waiting 40 ticks");
                    helper.assertTrue(scene.registry().unpowered(domain), "a 1x2x4 converter does not power plain cable");
                    var reading = scene.reading();
                    helper.assertTrue(!reading.converters().isEmpty()
                            && reading.converters().stream().noneMatch(converter -> converter.matches()),
                            "the converter is listed as not matching");
                    var stored = ((BeamConverterBlockEntity) helper.getBlockEntity(SWITCH.east(6))).stored();
                    helper.assertValueEqual(stored, 16000.0, "a converter that does not match is not drawn on");
                    scene.creative(SWITCH.east(1).above(), Direction.DOWN);
                    scene.creative(SWITCH.east(1).above(2), Direction.DOWN);
                    phase[0] = 3;
                    throw new GameTestAssertException("Waiting for the creative converter to run the domain");
                }
                default -> {
                    helper.assertTrue(!scene.registry().unpowered(domain) && scene.running(),
                            "Waiting for the creative converter to run the domain");
                    var creative = (BeamConverterBlockEntity) helper.getBlockEntity(SWITCH.east(1).above());
                    helper.assertValueEqual(creative.stored(), BeamConverterBlockEntity.CAPACITY, "it never empties");
                    RouterEvidence.write("operatingpowerneedsamatchingconverter", 4, Map.of(
                            "wrongSize", "stopped", "wrongSizeListed", "not matching", "wrongSizeDrawn", "false",
                            "creative", "running"));
                    scene.close();
                }
            }
        });
    }

    /**
     * Two 1x1x2 converters, fed 1000 and 3000 FE, pool their buffers and are drawn in proportion; a converter block's
     * FE survives a save and load of its block entity.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 600, required = true, manualOnly = true)
    public static void operatingPowerConvertersAddUp(GameTestHelper helper) {
        var scene = new Scene(helper);
        var west = SWITCH.east(3);
        var below = SWITCH.east(2).below();
        var phase = new int[1];
        helper.succeedWhen(() -> {
            scene.awaitDomain();
            var domain = scene.domain();
            switch (phase[0]) {
                case 0 -> {
                    scene.converter(west, Direction.WEST);
                    scene.converter(west.east(), Direction.WEST);
                    scene.converter(below, Direction.UP);
                    scene.converter(below.below(), Direction.UP);
                    phase[0] = 1;
                    throw new GameTestAssertException("Waiting for both converters to form");
                }
                case 1 -> {
                    helper.assertTrue(RouterBlock.formed(helper.getBlockState(west.east()))
                            && RouterBlock.formed(helper.getBlockState(below.below())), "Waiting for both to form");
                    // Fed only once both are in the domain, so neither is drawn on alone.
                    helper.assertValueEqual(scene.reading().capacity(), 4 * BeamConverterBlockEntity.CAPACITY,
                            "Waiting for both converters to join the domain");
                    helper.assertValueEqual(scene.insert(west.east(), Direction.EAST, 1000), 1000, "FE into the first");
                    helper.assertValueEqual(scene.insert(below.below(), Direction.DOWN, 3000), 3000, "FE into the second");
                    phase[0] = 2;
                    throw new GameTestAssertException("Waiting for the domain to run");
                }
                default -> {
                    helper.assertTrue(!scene.registry().unpowered(domain), "Waiting for the domain to run");
                    var reading = scene.reading();
                    helper.assertValueEqual(reading.capacity(), 4 * BeamConverterBlockEntity.CAPACITY,
                            "four converter blocks' buffers");
                    helper.assertTrue(reading.stored() < 4000 && reading.stored() > 3000, "both are drawn on: "
                            + reading.stored());
                    var first = (BeamConverterBlockEntity) helper.getBlockEntity(west.east());
                    var second = (BeamConverterBlockEntity) helper.getBlockEntity(below.below());
                    helper.assertTrue(Math.abs(second.stored() - 3 * first.stored()) < 1e-6,
                            "drawn in proportion: " + first.stored() + " and " + second.stored());
                    var registries = helper.getLevel().registryAccess();
                    var saved = second.saveWithFullMetadata(registries);
                    var loaded = BlockEntity.loadStatic(second.getBlockPos(), second.getBlockState(), saved, registries);
                    helper.assertTrue(loaded instanceof BeamConverterBlockEntity copy
                            && Math.abs(copy.stored() - second.stored()) < 1e-9, "FE survives a save");
                    RouterEvidence.write("operatingpowerconvertersaddup", 4, Map.of(
                            "running", "true", "capacity", "64000", "proportional", "true", "saved", "true"));
                    scene.close();
                }
            }
        });
    }

    /**
     * The statistics page's figures for a running domain: each second the book gets the domain's use by group, its
     * converters' input averaged over the second, their buffer as a percentage and its size, and that it ran; the
     * converter body is listed with its size, input and match.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1200, required = true, manualOnly = true)
    public static void operatingPowerFeedsStatistics(GameTestHelper helper) {
        var scene = new Scene(helper);
        var front = SWITCH.east(3);
        var back = SWITCH.east(4);
        var phase = new int[1];
        var lastSecond = new long[1];
        var statistics = space.controlnet.ae2federation.observability.LevelObservabilityService.get(helper.getLevel())
                .statistics();
        // Fed 5 FE every tick, above the domain's use of 3.
        helper.onEachTick(() -> {
            if (phase[0] >= 2) scene.insert(back, Direction.EAST, 5);
        });
        helper.succeedWhen(() -> {
            scene.awaitDomain();
            var domain = scene.domain();
            switch (phase[0]) {
                case 0 -> {
                    scene.converter(front, Direction.WEST);
                    scene.converter(back, Direction.WEST);
                    phase[0] = 1;
                    throw new GameTestAssertException("Waiting for the converter to form");
                }
                case 1 -> {
                    helper.assertTrue(RouterBlock.formed(helper.getBlockState(back)), "Waiting for the converter to form");
                    helper.assertValueEqual(scene.insert(back, Direction.EAST, 8000), 8000, "FE into the converter");
                    phase[0] = 2;
                    throw new GameTestAssertException("Waiting for the domain to run");
                }
                case 2 -> {
                    helper.assertTrue(!scene.registry().unpowered(domain), "Waiting for the domain to run");
                    // The service writes a second once the wall clock passes it, and a GameTest server runs its ticks
                    // faster than that: the test writes each second itself, one tick apart. This one holds the 8000.
                    lastSecond[0] = statistics.nowSecond();
                    statistics.finishSecond(lastSecond[0]);
                    phase[0] = 3;
                    throw new GameTestAssertException("Waiting for a second fed every tick");
                }
                default -> {
                    statistics.finishSecond(++lastSecond[0]);
                    var book = statistics.book();
                    var now = lastSecond[0];
                    var prefix = space.controlnet.ae2federation.observability.statistics.StatisticsNames.fedpower(domain.value());
                    var range = space.controlnet.ae2federation.observability.statistics.StatisticsRange.SECONDS;
                    helper.assertValueEqual(book.latest(prefix + "stopped", range, now), 0.0, "the domain ran");
                    var input = book.latest(prefix + "input", range, now);
                    helper.assertTrue(Math.abs(input - 5) < 1e-6, "fed 5 FE every tick: " + input);
                    helper.assertValueEqual(book.latest(prefix + "use/cables", range, now), 1.0, "two cables");
                    helper.assertValueEqual(book.latest(prefix + "use/switches", range, now), 2.0, "one Switch");
                    helper.assertValueEqual(book.latest(prefix + "capacity", range, now),
                            2 * BeamConverterBlockEntity.CAPACITY, "two converter blocks");
                    var stored = book.latest(prefix + "stored", range, now);
                    helper.assertTrue(stored > 0 && stored < 100, "the buffer as a percentage: " + stored);
                    var bodies = statistics.converters(domain.value());
                    helper.assertTrue(bodies.size() == 1 && bodies.get(0).size().equals("1x1x2") && bodies.get(0).matches()
                            && Math.abs(bodies.get(0).input() - 5) < 1e-6, "one 1x1x2 body listed: " + bodies);
                    RouterEvidence.write("operatingpowerfeedsstatistics", 6, Map.of(
                            "stopped", "0", "input", "5 FE/t", "use", "cables 1, switches 2", "capacity", "32000",
                            "stored", "percent", "bodies", "1x1x2 matching"));
                    scene.close();
                }
            }
        });
    }

    /** The Switch with A and B, its two cables, and the rule turned on until {@link #close()}. */
    private static final class Scene implements AutoCloseable {
        private final GameTestHelper helper;
        private final RouterFixtures routers;
        private boolean placed;

        Scene(GameTestHelper helper) {
            this.helper = helper;
            BlockPos.betweenClosed(SWITCH.offset(-1, -3, -1), SWITCH.offset(7, 3, 1))
                    .forEach(position -> helper.setBlock(position, Blocks.AIR));
            routers = new RouterFixtures(helper);
            routers.placeNativeDevice(SWITCH, Direction.NORTH);
            routers.placeNativeDevice(SWITCH, Direction.SOUTH);
            OperatingPowerRule.set(true, OperatingPowerRule.DEFAULT_MULTIPLIER);
        }

        void awaitDomain() {
            if (FederationDomainRegistryAccess.confirmedNetworkId(grid(Direction.NORTH)).isEmpty()
                    || FederationDomainRegistryAccess.confirmedNetworkId(grid(Direction.SOUTH)).isEmpty()) {
                throw new GameTestAssertException("Waiting for A and B to settle");
            }
            if (!placed) {
                placed = true;
                routers.placeSwitch(SWITCH);
                routers.placeFederationCable(SWITCH.east());
                routers.placeFederationCable(SWITCH.east(2));
            }
            if (!registry().shareFederationDomain(network(Direction.NORTH), network(Direction.SOUTH))) {
                throw new GameTestAssertException("Waiting for A and B to share a domain");
            }
        }

        FederationDomainId domain() {
            return registry().federationdomainsFor(network(Direction.NORTH)).iterator().next();
        }

        boolean running() {
            return registry().shareRunningFederationDomain(network(Direction.NORTH), network(Direction.SOUTH));
        }

        OperatingPowerService.Reading reading() {
            return OperatingPowerService.reading(helper.getLevel().getServer(), domain())
                    .orElseThrow(() -> new GameTestAssertException("No power reading yet"));
        }

        void converter(BlockPos position, Direction facing) {
            helper.setBlock(position, PowerRegistration.BEAM_CONVERTER.get().defaultBlockState()
                    .setValue(BeamConverterBlock.FACING, facing));
        }

        void creative(BlockPos position, Direction facing) {
            helper.setBlock(position, PowerRegistration.CREATIVE_BEAM_CONVERTER.get().defaultBlockState()
                    .setValue(BeamConverterBlock.FACING, facing));
        }

        int insert(BlockPos position, Direction side, int amount) {
            var storage = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(position),
                    side);
            return storage == null ? 0 : storage.receiveEnergy(amount, false);
        }

        FederationCableBlockEntity cable() {
            return (FederationCableBlockEntity) helper.getBlockEntity(SWITCH.east());
        }

        FederationDomainRegistry registry() {
            return FederationDomainRegistryAccess.get(helper.getLevel());
        }

        private appeng.api.networking.IGrid grid(Direction face) {
            return routers.nativeDeviceNode(SWITCH, face).getGrid();
        }

        private NetworkId network(Direction face) {
            return FederationDomainRegistryAccess.confirmedNetworkId(grid(face))
                    .orElseThrow(() -> new GameTestAssertException("Native network identity is not settled"));
        }

        @Override
        public void close() {
            OperatingPowerRule.set(false, OperatingPowerRule.DEFAULT_MULTIPLIER);
            routers.close();
        }
    }
}
