package space.controlnet.ae2federation.ae2.processing;

import appeng.api.networking.IGridNodeService;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.IGridNode;

final class NativeProviderLaneServices {
    private IGridTickable ticker;
    private ICraftingProvider provider;
    private long tickerInvocations;
    private long providerRefreshInvocations;

    <T extends IGridNodeService> void capture(Class<T> serviceClass, T service) {
        // PatternProviderLogic's constructor registers AE2's own ticker and crafting provider before any addon code
        // runs. A Lane keeps those two. AE2 would let a later registration replace them; here it is left out, like
        // services of other kinds that addons add to every PatternProviderLogic, such as Applied Flux's energy
        // distributor, rather than being added to the physical node once per Lane. The owner logic's node adds those
        // to the physical node once (CapturedManagedGridNode.owner).
        if (serviceClass == IGridTickable.class && ticker == null) {
            ticker = (IGridTickable) service;
        } else if (serviceClass == ICraftingProvider.class && provider == null) {
            provider = (ICraftingProvider) service;
        }
    }

    IGridTickable ticker() {
        if (ticker == null) {
            throw new IllegalStateException("PatternProviderLogic did not install a native ticker");
        }
        return ticker;
    }

    ICraftingProvider provider() {
        if (provider == null) {
            throw new IllegalStateException("PatternProviderLogic did not install a crafting provider");
        }
        return provider;
    }

    TickRateModulation tick(IGridNode node, int ticksSinceLastCall) {
        tickerInvocations++;
        return ticker().tickingRequest(node, ticksSinceLastCall);
    }

    long tickerInvocations() {
        return tickerInvocations;
    }

    void recordProviderRefresh() {
        providerRefreshInvocations++;
    }

    long providerRefreshInvocations() {
        return providerRefreshInvocations;
    }
}
