package space.controlnet.ae2federation.test;

import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.api.parts.PartHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.FederationDomainSnapshot;
import space.controlnet.ae2federation.domain.port.FederationPort;
import space.controlnet.ae2federation.p2p.FederationP2PTunnelPart;
import space.controlnet.ae2federation.p2p.P2PRefreshDiagnostics;
import space.controlnet.ae2federation.test.p2p.P2PTunnels;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;

/**
 * How the work of keeping Federation P2P links published grows with the outputs of one frequency. A carrier network
 * with an ME Controller (real channels, no channel mode changed) feeds a dense cable; glass cable buses above and below
 * it carry one input and {@code outputs} outputs. Three changes are measured: pairing with a memory card, the carrier
 * losing power, and power coming back. For each, the tunnels' refresh requests, the refresh tasks that ran, node
 * publications, registry changes, the links published, and the Domain recomputations with their time are logged as
 * {@code AE2F_P2P_REFRESH}. Each change must also end in the right Domains: one Domain of every tunnel while the input
 * works, and every tunnel on its own without power.
 */
@PrefixGameTestTemplate(false)
public final class P2PRefreshBenchmarkGameTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(P2PRefreshBenchmarkGameTests.class);
    private static final BlockPos CELL = new BlockPos(0, 4, 2);
    private static final BlockPos CONTROLLER = new BlockPos(1, 4, 2);
    /** The tunnel faces of a bus above the dense cable and of one below it. */
    private static final List<Direction> ABOVE = List.of(Direction.NORTH, Direction.SOUTH, Direction.UP);
    private static final List<Direction> BELOW = List.of(Direction.NORTH, Direction.SOUTH, Direction.DOWN);
    /** Ticks to keep counting after a change reached its Domains, for refreshes that come late. */
    private static final int SETTLE_TICKS = 10;

    private P2PRefreshBenchmarkGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            timeoutTicks = 600)
    public static void p2pRefreshScale2(GameTestHelper helper) {
        run(helper, 2);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            timeoutTicks = 600)
    public static void p2pRefreshScale4(GameTestHelper helper) {
        run(helper, 4);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            timeoutTicks = 600)
    public static void p2pRefreshScale8(GameTestHelper helper) {
        run(helper, 8);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            timeoutTicks = 600)
    public static void p2pRefreshScale16(GameTestHelper helper) {
        run(helper, 16);
    }

    /** The most one dense cable from one Controller face carries: 32 channels, one input and 31 outputs. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            timeoutTicks = 600)
    public static void p2pRefreshScale31(GameTestHelper helper) {
        run(helper, 31);
    }

    private static void run(GameTestHelper helper, int outputs) {
        int count = outputs + 1;
        int trunk = (count + 5) / 6;
        var tunnels = new FederationP2PTunnelPart[count];
        var level = helper.getLevel();
        var registry = FederationDomainRegistryAccess.get(level);
        var cards = new P2PTunnels(helper.makeMockPlayer(GameType.CREATIVE));
        var stage = new int[1];
        var reachedAt = new long[1];
        var startedAt = new long[1];
        var recomputes = new long[2];
        var results = new LinkedHashMap<String, String>();
        helper.setBlock(CELL, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(CONTROLLER, AEBlocks.CONTROLLER.block());
        int placed = 0;
        for (int x = 0; x < trunk; x++) {
            var dense = new BlockPos(2 + x, 4, 2);
            helper.assertTrue(PartHelper.setPart(level, helper.absolutePos(dense), null, null,
                    AEParts.SMART_DENSE_CABLE.item(AEColor.TRANSPARENT)) != null, "A dense cable must be placed");
            for (var bus : List.of(dense.above(), dense.below())) {
                helper.assertTrue(P2PTunnels.placeCable(level, helper.absolutePos(bus)), "A bus must be placed");
                for (var side : bus.getY() > dense.getY() ? ABOVE : BELOW) {
                    if (placed < count) {
                        tunnels[placed++] = Objects.requireNonNull(cards.attune(level, helper.absolutePos(bus), side),
                                "A Federation cable must attune the tunnel");
                    }
                }
            }
        }
        helper.onEachTick(() -> {
            long now = level.getGameTime();
            switch (stage[0]) {
                case 0 -> {
                    if (!P2PTunnels.online(tunnels)) return;
                    begin(registry, recomputes, startedAt, now);
                    helper.assertTrue(cards.pair(tunnels[0], Arrays.asList(tunnels).subList(1, count)),
                            "The memory card must pair every output with the input");
                    stage[0] = 1;
                }
                case 1, 3, 5 -> {
                    boolean joined = stage[0] != 3;
                    if (reachedAt[0] == 0) {
                        if (!(joined ? oneDomain(helper, tunnels) : apart(helper, tunnels))) return;
                        reachedAt[0] = now;
                    }
                    if (now - reachedAt[0] < SETTLE_TICKS) return;
                    var phase = stage[0] == 1 ? "pair" : stage[0] == 3 ? "power-off" : "power-on";
                    results.put(phase, record(outputs, phase, registry, recomputes, reachedAt[0] - startedAt[0]));
                    reachedAt[0] = 0;
                    if (stage[0] == 5) {
                        PolicyEvidence.write("p2prefreshscale" + outputs, 3, Map.copyOf(results));
                        helper.succeed();
                        stage[0] = 7;
                        return;
                    }
                    begin(registry, recomputes, startedAt, now);
                    if (stage[0] == 1) {
                        helper.setBlock(CELL, Blocks.AIR);
                    } else {
                        helper.setBlock(CELL, AEBlocks.CREATIVE_ENERGY_CELL.block());
                    }
                    stage[0]++;
                }
                case 2, 4 -> stage[0]++;
                default -> {
                }
            }
        });
    }

    private static void begin(space.controlnet.ae2federation.domain.FederationDomainRegistry registry,
            long[] recomputes, long[] startedAt, long now) {
        P2PRefreshDiagnostics.reset();
        recomputes[0] = registry.recomputes();
        recomputes[1] = registry.recomputeNanos();
        startedAt[0] = now;
    }

    private static String record(int outputs, String phase,
            space.controlnet.ae2federation.domain.FederationDomainRegistry registry, long[] recomputes, long ticks) {
        var counts = P2PRefreshDiagnostics.snapshot();
        long recomputed = registry.recomputes() - recomputes[0];
        long micros = (registry.recomputeNanos() - recomputes[1]) / 1000;
        var line = "requests=" + counts.requests() + " tasks=" + counts.tasks() + " taskMicros="
                + counts.taskNanos() / 1000 + " publishes=" + counts.publishes()
                + " changes=" + counts.changes() + " links=" + counts.links() + " maxLinks=" + counts.maxLinks()
                + " recomputes=" + recomputed + " recomputeMicros=" + micros + " ticks=" + ticks;
        LOGGER.info("AE2F_P2P_REFRESH outputs={} phase={} {}", outputs, phase, line);
        return line;
    }

    /** Whether every tunnel is in one Domain, and that Domain holds nothing else. */
    private static boolean oneDomain(GameTestHelper helper, FederationP2PTunnelPart[] tunnels) {
        var domains = domains(helper, tunnels);
        var first = domains.get(0);
        return first != null && first.nodes().size() == tunnels.length
                && domains.stream().allMatch(domain -> domain != null
                        && domain.federationDomainId().equals(first.federationDomainId()));
    }

    /** Whether every tunnel is a Domain of its own. */
    private static boolean apart(GameTestHelper helper, FederationP2PTunnelPart[] tunnels) {
        return domains(helper, tunnels).stream().allMatch(domain -> domain == null || domain.nodes().size() == 1);
    }

    private static List<FederationDomainSnapshot> domains(GameTestHelper helper, FederationP2PTunnelPart[] tunnels) {
        var level = helper.getLevel();
        var registry = FederationDomainRegistryAccess.get(level);
        var result = new ArrayList<FederationDomainSnapshot>();
        for (var tunnel : tunnels) {
            var port = new FederationPort(tunnel.getBlockEntity().getBlockPos(), tunnel.getSide(),
                    tunnel.getSide().getSerializedName());
            result.add(registry.federationDomainOf(FederationDomainRegistryAccess.nodeId(level, port)).orElse(null));
        }
        return result;
    }
}
