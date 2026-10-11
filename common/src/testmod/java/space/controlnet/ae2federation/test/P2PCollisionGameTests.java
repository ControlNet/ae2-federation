package space.controlnet.ae2federation.test;

import appeng.me.service.P2PService;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.p2p.FederationP2PTunnelPart;
import space.controlnet.ae2federation.test.p2p.TunnelCarrier;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;

@PrefixGameTestTemplate(false)
public final class P2PCollisionGameTests {
    /** Real tunnels; native frequency assignment deliberately reproduces a collision between input configurations. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 600)
    public static void p2pCollidingInputsStaySeparate(GameTestHelper helper) {
        var carrier = TunnelCarrier.withController(helper, new BlockPos(0, 4, 2), 3);
        int[] stage = {0};
        long[] changedAt = {0};
        helper.succeedWhen(() -> {
            var now = helper.getLevel().getGameTime();
            helper.assertTrue(carrier.online(0, 1, 2), "Waiting for real active tunnels");
            var service = P2PService.get(carrier.tunnel(0).getMainNode().getGrid());
            if (stage[0] == 0) {
                helper.assertFalse(carrier.tunnel(0).isOutput(), "First tunnel is an input");
                helper.assertFalse(carrier.tunnel(1).isOutput(), "Second tunnel is an input");
                // Configuration fault injection uses the native API, not fake tunnel classes or replacement registries.
                service.updateFreq(carrier.tunnel(0), (short) 73);
                service.updateFreq(carrier.tunnel(1), (short) 73);
                stage[0] = 1;
                changedAt[0] = now;
                helper.fail("Wait for the coalesced native frequency callbacks");
            }
            helper.assertTrue(now > changedAt[0] + 1, "Waiting for the queued refresh");
            if (stage[0] == 1) {
                helper.assertFalse(service.getOutputs((short) 73, FederationP2PTunnelPart.class).findAny().isPresent(),
                        "Native P2P has no outputs for either input to connect to");
                helper.assertTrue(carrier.alone(0), "An input must not link directly to another input");
                helper.assertTrue(carrier.alone(1), "Both colliding inputs remain separate without outputs");
                carrier.pair(1, 2);
                carrier.tunnels().forEach(tunnel -> tunnel.onTunnelNetworkChange());
                stage[0] = 2;
                changedAt[0] = now;
                helper.fail("Add a real output and refresh both input configurations");
            }
            if (stage[0] == 2) {
                helper.assertTrue(service.getInput((short) 73) == carrier.tunnel(1), "Native registry selects input one");
                helper.assertTrue(carrier.domainOf(1, 2), "The selected native input links to its output");
                helper.assertTrue(carrier.alone(0), "An unselected input has no reciprocal output link");
                service.updateFreq(carrier.tunnel(0), (short) 73);
                carrier.tunnels().forEach(tunnel -> tunnel.onTunnelNetworkChange());
                stage[0] = 3;
                changedAt[0] = now;
                helper.fail("Native registration now selects the other input");
            }
            helper.assertTrue(carrier.domainOf(0, 2), "The output follows the newly selected native input");
            helper.assertTrue(carrier.alone(1), "The old input loses its reciprocal output link");
            PolicyEvidence.write("p2pcollidinginputsstayseparate", 10,
                    Map.of("configuration", "native frequency collision injection", "inputs", "2", "outputs", "0 then 1"));
        });
    }
    /** Native frequency collision injection: sixteen inputs must not each publish sixteen output candidates. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 600)
    public static void p2pCollidingInputsLinearPublication(GameTestHelper helper) {
        var carrier = TunnelCarrier.withController(helper, new BlockPos(0, 4, 2), 32);
        var all = java.util.stream.IntStream.range(0, 32).toArray();
        var outputs = java.util.stream.IntStream.range(16, 32).toArray();
        int[] stage = {0};
        long[] changedAt = {0};
        long[] priorLinks = {0};
        helper.succeedWhen(() -> {
            var now = helper.getLevel().getGameTime();
            helper.assertTrue(carrier.online(all), "Waiting for 32 real channels");
            if (stage[0] == 0) {
                priorLinks[0] = space.controlnet.ae2federation.p2p.P2PRefreshDiagnostics.snapshot().links();
                carrier.pair(0, outputs);
                var service = P2PService.get(carrier.tunnel(0).getMainNode().getGrid());
                for (int input = 1; input < 16; input++) {
                    service.updateFreq(carrier.tunnel(input), carrier.tunnel(0).getFrequency());
                }
                stage[0] = 1;
                changedAt[0] = now;
                helper.fail("Wait for native registration and queued callbacks");
            }
            if (stage[0] == 1) {
                helper.assertTrue(now > changedAt[0] + 3, "Wait for registration callbacks");
                space.controlnet.ae2federation.p2p.P2PRefreshDiagnostics.reset();
                carrier.tunnels().forEach(tunnel -> tunnel.onTunnelNetworkChange());
                stage[0] = 2;
                changedAt[0] = now;
                helper.fail("Measure one stable refresh of the colliding group");
            }
            helper.assertTrue(now > changedAt[0] + 1, "Wait for the measured drain");
            var counts = space.controlnet.ae2federation.p2p.P2PRefreshDiagnostics.snapshot();
            helper.assertValueEqual(counts.links() - priorLinks[0], 32L,
                    "Only the canonical input and sixteen outputs publish reciprocal tunnel evidence");
            helper.assertValueEqual(counts.groups(), 1L, "Build the colliding frequency once");
            helper.assertValueEqual(counts.members(), 32L, "Keep all affected nodes in the refresh");
            helper.assertValueEqual(counts.publishes(), 32L, "Publish every node, including invalidated inputs");
            helper.assertValueEqual(counts.queued(), 1L, "Queue one task for the callback burst");
            for (int input = 0; input < 15; input++) {
                helper.assertTrue(carrier.alone(input), "Unselected native inputs remain isolated");
            }
            var linked = java.util.stream.IntStream.range(15, 32).toArray();
            helper.assertTrue(carrier.domainOf(linked), "All sixteen real outputs remain linked to the selected input");
            PolicyEvidence.write("p2pcollidinginputslinearpublication", 7,
                    Map.of("inputs", "16", "outputs", "16", "links", "32", "refresh", counts.toString()));
        });
    }

}
