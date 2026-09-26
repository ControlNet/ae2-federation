package space.controlnet.ae2federation.storage.mount;

import appeng.api.networking.IGrid;
import java.util.Objects;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;

public record StorageRelationship(PolicyKey key, IGrid consumerGrid, IGrid providerGrid) {
    public StorageRelationship {
        Objects.requireNonNull(key);
        Objects.requireNonNull(consumerGrid);
        Objects.requireNonNull(providerGrid);
        if (key.capability() != PolicyCapability.STORAGE) {
            throw new IllegalArgumentException("Storage relationship requires STORAGE capability");
        }
        if (consumerGrid == providerGrid) {
            throw new IllegalArgumentException("Storage relationship requires distinct native Grids");
        }
    }
}
