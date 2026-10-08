package space.controlnet.ae2federation.test;

import java.util.Map;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.test.p2p.TunnelCarrier;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;

/**
 * Which Federation Domains Federation P2P tunnels form as their frequencies, input and channels change: a frequency's
 * tunnels are one Domain only through a working input, and no link outlives the change that ended it.
 */
@PrefixGameTestTemplate(false)
public final class P2PTopologyGameTests {
    private static final int SETTLE_TICKS = 40;

    private P2PTopologyGameTests() {
    }

    /**
     * Five tunnels on a Controller's network. An input with two outputs is one Domain; moving one output to another
     * frequency moves it to that frequency's Domain; taking the input off leaves its remaining output linked to nothing;
     * a new tunnel in its place, paired as the input, joins that output and a fresh one.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 600)
    public static void p2pFrequencyAndInputChanges(GameTestHelper helper) {
        var carrier = TunnelCarrier.withController(helper, new BlockPos(0, 4, 2), 5);
        var stage = new int[1];
        helper.succeedWhen(() -> {
            if (stage[0] == 0) {
                helper.assertTrue(carrier.online(0, 1, 2, 3, 4), "Waiting for the tunnels' channels");
                carrier.pair(0, 1, 2);
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertTrue(carrier.domainOf(0, 1, 2), "Waiting for the input and its two outputs to be one Domain");
                helper.assertTrue(carrier.alone(3) && carrier.alone(4), "Unpaired tunnels are linked to nothing");
                carrier.pair(3, 2);
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                helper.assertTrue(carrier.domainOf(0, 1), "Waiting for the moved output to leave the first Domain");
                helper.assertTrue(carrier.domainOf(3, 2), "The moved output joins its new input");
                carrier.remove(0);
                stage[0] = 3;
            }
            if (stage[0] == 3) {
                helper.assertTrue(carrier.alone(1), "Waiting for the output without its input to be linked to nothing");
                helper.assertTrue(carrier.domainOf(3, 2), "The other frequency keeps its Domain");
                carrier.replace(0);
                stage[0] = 4;
            }
            if (stage[0] == 4) {
                helper.assertTrue(carrier.online(0), "Waiting for the new tunnel's channel");
                carrier.pair(0, 1, 4);
                stage[0] = 5;
            }
            helper.assertTrue(carrier.domainOf(0, 1, 4), "Waiting for the new input to join its outputs");
            helper.assertTrue(carrier.domainOf(3, 2), "The other frequency keeps its Domain");
            PolicyEvidence.write("p2pfrequencyandinputchanges", 9, Map.of("paired", "3", "movedOutput", "2+2",
                    "withoutInput", "alone", "newInput", "3"));
        });
    }

    /**
     * An input and eight outputs on an ad-hoc network: nine devices want a channel where eight fit, so AE2 gives none
     * and no tunnel is linked. Taking one output off gives the other eight their channels, and they form one Domain.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 600)
    public static void p2pChannelShortage(GameTestHelper helper) {
        var carrier = TunnelCarrier.adHoc(helper, new BlockPos(0, 4, 2), 9);
        var stage = new int[1];
        var pairedAt = new long[1];
        helper.succeedWhen(() -> {
            long now = helper.getLevel().getGameTime();
            if (stage[0] == 0) {
                helper.assertTrue(carrier.tunnels().stream().allMatch(tunnel -> tunnel.getGridNode() != null
                        && tunnel.getGridNode().hasGridBooted()), "Waiting for the carrier to boot");
                carrier.pair(0, 1, 2, 3, 4, 5, 6, 7, 8);
                pairedAt[0] = now;
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertTrue(now - pairedAt[0] >= SETTLE_TICKS, "Letting the paired tunnels settle");
                helper.assertTrue(carrier.tunnels().stream().noneMatch(tunnel -> tunnel.isActive()),
                        "Nine devices on an ad-hoc network get no channel");
                helper.assertTrue(IntStream.range(0, 9).allMatch(carrier::alone),
                        "Tunnels without a channel are linked to nothing");
                carrier.remove(8);
                stage[0] = 2;
            }
            helper.assertTrue(carrier.online(0, 1, 2, 3, 4, 5, 6, 7), "Waiting for eight tunnels to get channels");
            helper.assertTrue(carrier.domainOf(0, 1, 2, 3, 4, 5, 6, 7), "Waiting for the eight to be one Domain");
            PolicyEvidence.write("p2pchannelshortage", 5, Map.of("withoutChannels", "9 alone", "withChannels", "8"));
        });
    }
}
