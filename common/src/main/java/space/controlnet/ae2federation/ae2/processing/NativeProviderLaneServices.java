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
        if (serviceClass == IGridTickable.class) {
            if (ticker != null) {
                throw new IllegalStateException("PatternProviderLogic installed duplicate native tickers");
            }
            ticker = (IGridTickable) service;
        } else if (serviceClass == ICraftingProvider.class) {
            if (provider != null) {
                throw new IllegalStateException("PatternProviderLogic installed duplicate crafting providers");
            }
            provider = (ICraftingProvider) service;
        } else {
            throw new IllegalArgumentException("Unexpected PatternProviderLogic service: " + serviceClass.getName());
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
