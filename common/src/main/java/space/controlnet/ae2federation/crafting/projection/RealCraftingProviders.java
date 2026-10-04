package space.controlnet.ae2federation.crafting.projection;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.processing.provider.ProviderObservationRegistry;

/**
 * The pattern providers that really belong to a Grid: AE2 nodes with a crafting provider service (pattern providers,
 * and any add-on block that registers one), and the lanes of Federation Providers on the Grid, which register with
 * the crafting service directly. Projections of other networks' providers are never real, so projecting them again
 * cannot happen.
 */
final class RealCraftingProviders {
    private RealCraftingProviders() {
    }

    static List<ICraftingProvider> on(ServerLevel level, IGrid grid) {
        var providers = new ArrayList<ICraftingProvider>();
        for (var node : grid.getNodes()) {
            var provider = node.getService(ICraftingProvider.class);
            if (provider != null && node.isActive() && node.hasGridBooted() && node.getGrid() == grid) {
                providers.add(provider);
            }
        }
        for (var entry : ProviderObservationRegistry.entries(level)) {
            var provider = entry.provider();
            if (!provider.registered()) continue;
            for (var lane : provider.nativeLanes()) {
                if (lane.getGrid() == grid) providers.add(lane);
            }
        }
        return providers;
    }
}
