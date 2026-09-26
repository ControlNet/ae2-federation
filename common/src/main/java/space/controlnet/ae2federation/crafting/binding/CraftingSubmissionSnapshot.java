package space.controlnet.ae2federation.crafting.binding;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingCPU;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record CraftingSubmissionSnapshot(CraftingCapabilityBinding binding, ICraftingService service,
        IGrid sourceGrid, List<NativeCraftingProviderSource> providerSources, Set<ICraftingCPU> nativeCpus) {
    public CraftingSubmissionSnapshot {
        Objects.requireNonNull(binding);
        Objects.requireNonNull(service);
        Objects.requireNonNull(sourceGrid);
        providerSources = List.copyOf(providerSources);
        nativeCpus = Set.copyOf(nativeCpus);
        if (providerSources.isEmpty()) {
            throw new IllegalArgumentException("Crafting submission requires a native provider");
        }
    }
}
