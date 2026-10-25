package space.controlnet.ae2federation.crafting.projection;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import java.util.Collections;
import java.util.IdentityHashMap;
import space.controlnet.ae2federation.mixin.compat.CraftingServiceProvidersAccess;
import space.controlnet.ae2federation.mixin.compat.NetworkCraftingProvidersAccess;
import space.controlnet.ae2federation.mixin.compat.CraftingProviderStateAccess;

/**
 * The pattern providers that really belong to a Grid: AE2 nodes with a crafting provider service (pattern providers,
 * and any add-on block that registers one), plus native global registrations without node services. Projections of other networks' providers are never real, so projecting them again
 * cannot happen.
 */
final class RealCraftingProviders {
    private RealCraftingProviders() {
    }

    static List<ICraftingProvider> on(ServerLevel level, IGrid grid) {
        long started = System.nanoTime();
        long nodes = 0;
        long globals = 0;
        var providers = Collections.newSetFromMap(new IdentityHashMap<ICraftingProvider, Boolean>());
        for (var node : grid.getNodes()) {
            nodes++;
            var provider = node.getService(ICraftingProvider.class);
            if (provider != null && node.isActive() && node.hasGridBooted() && node.getGrid() == grid) {
                providers.add(provider);
            }
        }
        if (grid.getCraftingService() instanceof CraftingServiceProvidersAccess service
                && service.ae2federation$providers() instanceof NetworkCraftingProvidersAccess access) {
            for (var state : access.ae2federation$globals()) {
                globals++;
                var provider = ((CraftingProviderStateAccess) state).ae2federation$provider();
                if (!PatternProjection.isProjection(provider)) providers.add(provider);
            }
        }
        ProjectionDiagnostics.discovered(nodes, globals, System.nanoTime() - started);
        return List.copyOf(providers);
    }
}
