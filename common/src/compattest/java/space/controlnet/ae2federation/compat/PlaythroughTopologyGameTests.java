package space.controlnet.ae2federation.compat;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class PlaythroughTopologyGameTests {
    @RunsAlone
    @GameTest(templateNamespace = "ae2federation_test", template = "scale_36_empty", timeoutTicks = PlaythroughCraftingGameTests.MAX_TICKS)
    public static void playthroughTopology(GameTestHelper helper) throws Exception {
        if ("recover".equals(System.getProperty("ae2federation.compat.transitRestartPhase"))) PlaythroughFactory.resumeRestart(helper);
        else PlaythroughCraftingGameTests.runTopology(helper);
    }
    private PlaythroughTopologyGameTests() { }
}
