package space.controlnet.ae2federation.compat;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Federation with Neo ECO AE Extension and AdvancedAE loaded together: what each does with the other's blocks. */
@PrefixGameTestTemplate(false)
public final class NeoEcoAdvancedAECompatGameTests {
    private NeoEcoAdvancedAECompatGameTests() {
    }

    /**
     * Neo ECO's smart doubling through a projected Advanced Pattern Provider: Neo ECO recognises AdvancedAE's provider
     * logic and scales to it on its own network, so it must through the projection too.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void advPatternProviderSmartDoubling(GameTestHelper helper) {
        helper.succeedWhen(NeoEcoCompatGameTests.smartDoubling(helper, "advanced_ae:adv_pattern_provider")::tick);
    }
}
