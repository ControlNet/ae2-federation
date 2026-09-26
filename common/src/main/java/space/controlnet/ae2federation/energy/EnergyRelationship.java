package space.controlnet.ae2federation.energy;

import appeng.api.networking.IGrid;
import java.util.Objects;
import space.controlnet.ae2federation.policy.PolicyKey;

record EnergyRelationship(PolicyKey key, IGrid consumerGrid, IGrid providerGrid) {
    EnergyRelationship {
        Objects.requireNonNull(key);
        Objects.requireNonNull(consumerGrid);
        Objects.requireNonNull(providerGrid);
        if (consumerGrid == providerGrid) {
            throw new IllegalArgumentException("Directional energy requires distinct native Grids");
        }
    }
}
