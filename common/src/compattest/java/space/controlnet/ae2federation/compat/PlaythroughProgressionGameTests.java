package space.controlnet.ae2federation.compat;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class PlaythroughProgressionGameTests {
    /** One server process of a quest progression; tools/compat_playthrough_progression.py chains the processes. */
    @RunsAlone
    @GameTest(templateNamespace = "ae2federation_test", template = "scale_36_empty", timeoutTicks = PlaythroughCraftingGameTests.MAX_TICKS)
    public static void playthroughProgression(GameTestHelper helper) throws Exception {
        new PlaythroughProgression(helper).start();
    }
    private PlaythroughProgressionGameTests() { }
}
