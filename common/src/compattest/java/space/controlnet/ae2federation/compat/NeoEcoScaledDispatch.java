package space.controlnet.ae2federation.compat;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.Level;

/**
 * Neo ECO's smart doubling, as its CPU decides it: a processing pattern's runs go out as one scaled batch only to a
 * provider {@code ECOProcessingPatternDispatcher.supportsScaledDispatch} accepts. That check reads only the request's
 * pattern and the provider, so a request with nothing else in it asks Neo ECO's own answer.
 */
final class NeoEcoScaledDispatch {
    private static final String EXECUTION = "cn.dancingsnow.neoecoae.crafting.execution.";
    private static final String PROVIDER = "cn.dancingsnow.neoecoae.api.me.provider.";

    private NeoEcoScaledDispatch() {
    }

    /** Whether a Neo ECO CPU would push {@code pattern}'s runs to {@code provider} as one scaled batch. */
    static boolean wouldScale(IPatternDetails pattern, ICraftingProvider provider, Level level) {
        try {
            var requestType = Class.forName(EXECUTION + "ECOCraftingDispatchRequest");
            var constructor = requestType.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            var request = constructor.newInstance(null, null, pattern, new KeyCounter[0], new KeyCounter(),
                    new KeyCounter(), 16L, null, level);
            var check = Class.forName(EXECUTION + "ECOProcessingPatternDispatcher")
                    .getDeclaredMethod("supportsScaledDispatch", requestType, ICraftingProvider.class);
            check.setAccessible(true);
            return (boolean) check.invoke(null, request, provider);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Neo ECO's scaled dispatch check is not available", exception);
        }
    }

    /**
     * Which of Neo ECO's batch paths take {@code provider}: its crafting fast path ({@code ECOFastPathFacade.supports},
     * which the crafting system's pattern bus takes) and its parallel providers ({@code ECOParallelCraftingProviders},
     * or before Neo ECO had that registry, the {@code ECOParallelCraftingProvider} interface).
     */
    static String batchPaths(ICraftingProvider provider) {
        try {
            var fastPath = (boolean) Class.forName("cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade")
                    .getMethod("supports", ICraftingProvider.class).invoke(null, provider);
            boolean parallel;
            try {
                parallel = Class.forName(PROVIDER + "ECOParallelCraftingProviders")
                        .getMethod("find", Object.class).invoke(null, provider) != null;
            } catch (ClassNotFoundException olderNeoEco) {
                parallel = Class.forName(PROVIDER + "ECOParallelCraftingProvider").isInstance(provider);
            }
            return "fast path " + fastPath + ", parallel " + parallel;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Neo ECO's batch paths are not available", exception);
        }
    }

    /** The providers {@code grid}'s crafting service holds for {@code pattern}. */
    static List<ICraftingProvider> providers(IGrid grid, IPatternDetails pattern) {
        var out = new ArrayList<ICraftingProvider>();
        ((appeng.me.service.CraftingService) grid.getCraftingService()).getProviders(pattern).forEach(out::add);
        return out;
    }
}
