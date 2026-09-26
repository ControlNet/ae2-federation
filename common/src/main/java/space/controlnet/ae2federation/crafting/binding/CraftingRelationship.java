package space.controlnet.ae2federation.crafting.binding;

import appeng.api.networking.IGrid;
import java.util.Objects;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;

public record CraftingRelationship(PolicyKey key, IGrid consumerGrid, IGrid providerGrid) {
    public CraftingRelationship {
        Objects.requireNonNull(key);
        Objects.requireNonNull(consumerGrid);
        Objects.requireNonNull(providerGrid);
        if (key.capability() != PolicyCapability.CRAFTING || consumerGrid == providerGrid) {
            throw new IllegalArgumentException("Crafting relationship requires distinct directional Grids");
        }
    }
}
