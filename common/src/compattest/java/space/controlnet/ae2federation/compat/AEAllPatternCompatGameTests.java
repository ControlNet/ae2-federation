package space.controlnet.ae2federation.compat;

import appeng.api.networking.crafting.ICraftingPlan;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Federation with AE All Pattern's own route planner. */
@PrefixGameTestTemplate(false)
public final class AEAllPatternCompatGameTests {
    private static final String ROUTING = "io.github.langqi99.aeallpattern.internal.routing.ae2.crafting.";

    private AEAllPatternCompatGameTests() {
    }

    /**
     * Inside the default routing policy, as its crafting screen plans when a Tianshu Pattern Router is on the network,
     * AE All Pattern's planner chains a seed pattern that only the provider network has; see
     * {@link CyclePlanningScene}. Its plans carry no mark of their own, so the scene itself tells them apart: AE2's
     * planner cannot chain the pattern, so a complete plan can only be AE All Pattern's. The plan is not run: AE2's own
     * CPU waits forever on such a cycle plan even with the pattern on its own network, as AE All Pattern's own tests
     * only plan too.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void routePlannerCycle(GameTestHelper helper) {
        helper.succeedWhen(new CyclePlanningScene(helper, "AE All Pattern's planner", plain -> plain,
                plan -> !plan.simulation())
                .planningWithin(AEAllPatternCompatGameTests::withDefaultPolicy).planningOnly()::tick);
    }

    /** Runs {@code start} with {@code CraftingRoutePolicy.DEFAULT} as the calling thread's routing policy. */
    @SuppressWarnings("unchecked")
    private static Future<ICraftingPlan> withDefaultPolicy(Supplier<Future<ICraftingPlan>> start) {
        try {
            var policy = Class.forName(ROUTING + "CraftingRoutePolicy").getField("DEFAULT").get(null);
            return (Future<ICraftingPlan>) Class.forName(ROUTING + "CraftingRoutePolicyContext")
                    .getMethod("withPolicy", policy.getClass(), Supplier.class).invoke(null, policy, start);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("AE All Pattern's routing policy is not available", exception);
        }
    }
}
