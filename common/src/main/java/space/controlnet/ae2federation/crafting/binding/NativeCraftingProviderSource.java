package space.controlnet.ae2federation.crafting.binding;

import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import java.util.Objects;
import java.util.UUID;

public record NativeCraftingProviderSource(UUID registrationNodeId, IGridNode node, ICraftingProvider provider) {
    public NativeCraftingProviderSource {
        Objects.requireNonNull(registrationNodeId);
        Objects.requireNonNull(node);
        Objects.requireNonNull(provider);
    }
}
