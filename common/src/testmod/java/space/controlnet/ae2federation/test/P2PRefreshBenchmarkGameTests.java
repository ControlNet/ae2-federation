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

    /** Two independently paired groups share a carrier; moving an output invalidates both old and new groups. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 600)
    public static void p2pRefreshIndependentGroups(GameTestHelper helper) {
        var carrier = TunnelCarrier.withController(helper, new BlockPos(0, 4, 2), 6);
        int[] stage = {0};
        long[] changedAt = {0};
        helper.succeedWhen(() -> {
            long now = helper.getLevel().getGameTime();
            if (stage[0] == 0) {
                helper.assertTrue(carrier.online(0, 1, 2, 3, 4, 5), "Waiting for six real channels");
                carrier.pair(0, 1, 2);
                carrier.pair(3, 4, 5);
                stage[0] = 1;
                changedAt[0] = now;
            }
            if (stage[0] == 1) {
                helper.assertTrue(carrier.domainOf(0, 1, 2) && carrier.domainOf(3, 4, 5), "Independent domains must form");
                helper.assertTrue(now - changedAt[0] >= SETTLE_TICKS, "Waiting for initial callbacks");
                P2PRefreshDiagnostics.reset();
                carrier.tunnels().forEach(tunnel -> tunnel.onTunnelNetworkChange());
                stage[0] = 2;
                changedAt[0] = now;
                helper.fail("Refresh both groups in the same tick");
            }
            if (stage[0] == 2) {
                helper.assertTrue(now > changedAt[0] + 1, "Waiting for the server drain");
                var counts = P2PRefreshDiagnostics.snapshot();
                helper.assertValueEqual(counts.requests(), 6L, "Six native callbacks");
                helper.assertValueEqual(counts.queued(), 1L, "Both groups share one queued drain");
                helper.assertValueEqual(counts.groups(), 2L, "Each independent group is built exactly once");
                helper.assertValueEqual(counts.members(), 6L, "Each member is enumerated once");
                helper.assertValueEqual(counts.publishes(), 6L, "Every affected node is published");
                LOGGER.info("AE2F_P2P_TWO_GROUPS {}", counts);
                carrier.pair(3, 2);
                stage[0] = 3;
                helper.fail("Move output two to the other frequency");
            }
            helper.assertTrue(carrier.domainOf(0, 1), "Old frequency loses the moved output");
            helper.assertTrue(carrier.domainOf(3, 4, 5, 2), "New frequency gains exactly the moved output");
            PolicyEvidence.write("p2prefreshindependentgroups", 9,
                    Map.of("callbacks", "6", "queued", "1", "groups", "2", "members", "6", "frequencyMove", "both updated"));
        });
    }

    /** Frequency zero means unpaired, even when native callbacks coalesce on the same carrier Grid. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 600)
    public static void p2pUnpairedStaySeparate(GameTestHelper helper) {
        var carrier = TunnelCarrier.withController(helper, new BlockPos(0, 4, 2), 3);
        int[] stage = {0};
        long[] refreshed = {0};
        helper.succeedWhen(() -> {
            helper.assertTrue(carrier.online(0, 1, 2), "Waiting for three powered native channels");
            for (var tunnel : carrier.tunnels()) {
                helper.assertValueEqual(tunnel.getFrequency(), (short) 0, "No memory card has paired these tunnels");
            }
            if (stage[0] == 0) {
                carrier.tunnels().forEach(tunnel -> tunnel.onTunnelNetworkChange());
                refreshed[0] = helper.getLevel().getGameTime();
                stage[0] = 1;
                helper.fail("Wait for the coalesced refresh");
            }
            helper.assertTrue(helper.getLevel().getGameTime() > refreshed[0] + 1, "Waiting for queued native callback");
            for (int index = 0; index < 3; index++) {
                helper.assertTrue(carrier.alone(index), "Unpaired active tunnel " + index + " must not link through frequency zero");
            }
            PolicyEvidence.write("p2punpairedstayseparate", 6, Map.of("unpairedTunnels", "3", "sharedCarrier", "true", "linked", "false"));
        });
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
                + " queued=" + counts.queued() + " groups=" + counts.groups() + " members=" + counts.members()
                + " changes=" + counts.changes() + " links=" + counts.links() + " maxLinks=" + counts.maxLinks()
                + " recomputes=" + recomputed + " recomputeMicros=" + micros + " ticks=" + ticks;
        LOGGER.info("AE2F_P2P_REFRESH outputs={} phase={} {}", outputs, phase, line);
        return line;
    }
}
