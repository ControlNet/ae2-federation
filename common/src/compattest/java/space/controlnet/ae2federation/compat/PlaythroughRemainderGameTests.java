package space.controlnet.ae2federation.compat;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Focused remainder regression, separate from chapter report selection. */
@PrefixGameTestTemplate(false)
public final class PlaythroughRemainderGameTests {
    private PlaythroughRemainderGameTests() { }

    /** Exercises real bucket remainders through the same two-layout accounting driver. */
    @RunsAlone
    @GameTest(templateNamespace = "ae2federation_test", template = "scale_36_empty", timeoutTicks = PlaythroughCraftingGameTests.MAX_TICKS)
    public static void playthroughRemainders(GameTestHelper helper) throws Exception {
        PlaythroughCraftingGameTests.runRemainderRegression(helper);
    }
}
