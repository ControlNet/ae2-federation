package space.controlnet.ae2federation.test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.p2p.P2PRefreshDiagnostics;
import space.controlnet.ae2federation.test.p2p.TunnelCarrier;
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
        var carrier = TunnelCarrier.withController(helper, new BlockPos(0, 4, 2), count);
        var all = IntStream.range(0, count).toArray();
        var outputIndexes = IntStream.range(1, count).toArray();
        var level = helper.getLevel();
        var registry = FederationDomainRegistryAccess.get(level);
        var stage = new int[1];
        var reachedAt = new long[1];
        var startedAt = new long[1];
        var recomputes = new long[2];
        var results = new LinkedHashMap<String, String>();
        helper.onEachTick(() -> {
            long now = level.getGameTime();
            switch (stage[0]) {
                case 0 -> {
                    if (!carrier.online(all)) return;
                    begin(registry, recomputes, startedAt, now);
                    carrier.pair(0, outputIndexes);
                    stage[0] = 1;
                }
                case 1, 3, 5 -> {
                    boolean joined = stage[0] != 3;
                    if (reachedAt[0] == 0) {
                        if (!(joined ? carrier.domainOf(all)
                                : IntStream.of(all).allMatch(carrier::alone))) return;
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
                        carrier.powerOff();
                    } else {
                        carrier.powerOn();
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
}
