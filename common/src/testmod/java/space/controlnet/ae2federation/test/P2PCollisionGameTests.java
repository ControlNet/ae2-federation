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
}
