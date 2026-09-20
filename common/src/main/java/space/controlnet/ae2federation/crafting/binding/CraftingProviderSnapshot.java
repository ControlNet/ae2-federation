package space.controlnet.ae2federation.crafting.binding;

import java.util.List;
import java.util.Objects;
import space.controlnet.ae2federation.identity.NetworkId;

record CraftingProviderSnapshot<S, P>(NetworkId origin, ProviderGeneration generation, S service, List<P> providers) {
    CraftingProviderSnapshot {
        Objects.requireNonNull(origin);
        Objects.requireNonNull(generation);
        Objects.requireNonNull(service);
        providers = List.copyOf(providers);
    }
}
