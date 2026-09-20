package space.controlnet.ae2federation.crafting.binding;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingService;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import space.controlnet.ae2federation.identity.NetworkId;

public record NativeCraftingBackend(NetworkId sourceNetworkId, ProviderGeneration generation, IGrid sourceGrid,
        ICraftingService service, List<NativeCraftingProviderSource> providers, Set<ICraftingCPU> cpus) {
    public NativeCraftingBackend {
        Objects.requireNonNull(sourceNetworkId);
        Objects.requireNonNull(generation);
        Objects.requireNonNull(sourceGrid);
        Objects.requireNonNull(service);
        providers = List.copyOf(providers);
        cpus = Set.copyOf(cpus);
        if (providers.isEmpty() || cpus.isEmpty()) {
            throw new IllegalArgumentException("Native crafting backend requires a provider and CPU");
        }
    }
}
