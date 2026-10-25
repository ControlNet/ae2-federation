package space.controlnet.ae2federation.compat;

import appeng.api.networking.crafting.ICraftingPlan;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Federation with Applied Enhancements' Aelis planner. */
@PrefixGameTestTemplate(false)
public final class AppliedEnhancementsCompatGameTests {
    private AppliedEnhancementsCompatGameTests() {
    }

    /**
     * With its automatic planner switched on, as its config file does, Aelis chains a seed pattern that only the
     * provider network has; see {@link CyclePlanningScene}. The switch is shared by the whole server, so the test runs
     * alone and puts it back afterwards.
     */
    @RunsAlone
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void aelisPlannerCycle(GameTestHelper helper) {
        var automatic = automaticPlanner();
        boolean before = automatic.get();
        automatic.set(false);
        helper.succeedWhen(new CyclePlanningScene(helper, "Aelis", plain -> {
            automatic.set(true);
            return plain;
        }, AppliedEnhancementsCompatGameTests::aelisPlan).finishing(() -> automatic.set(before))::tick);
    }

    /** Applied Enhancements' {@code crafting.aelis.enable_automatic_planner} switch. */
    private static ModConfigSpec.BooleanValue automaticPlanner() {
        try {
            return (ModConfigSpec.BooleanValue) Class.forName("com.appliedenhancements.Config")
                    .getField("ENABLE_AUTOMATIC_AELIS_PLANNER").get(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Applied Enhancements has no automatic Aelis planner switch", exception);
        }
    }

    /** Whether Aelis made {@code plan}, as the plan's calculation path records it; a fallback to AE2 is not. */
    private static boolean aelisPlan(ICraftingPlan plan) {
        try {
            var metadata = Class.forName("com.appliedenhancements.api.AelisExactCraftingPlanApi")
                    .getMethod("read", ICraftingPlan.class).invoke(null, plan);
            return metadata.getClass().getMethod("path").invoke(metadata).toString().equals("AELIS");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Applied Enhancements has no plan metadata", exception);
        }
    }
}
