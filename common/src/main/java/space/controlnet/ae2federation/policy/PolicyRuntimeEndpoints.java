package space.controlnet.ae2federation.policy;

import appeng.api.networking.IGrid;
import java.util.Objects;

public record PolicyRuntimeEndpoints(IGrid consumerGrid, IGrid providerGrid, BackendStatus backendStatus) {
    public PolicyRuntimeEndpoints {
        Objects.requireNonNull(consumerGrid);
        Objects.requireNonNull(providerGrid);
        Objects.requireNonNull(backendStatus);
    }
}
