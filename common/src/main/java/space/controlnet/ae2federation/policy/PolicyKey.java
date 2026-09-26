package space.controlnet.ae2federation.policy;

import java.util.Objects;
import space.controlnet.ae2federation.identity.NetworkId;

public record PolicyKey(NetworkId consumerNetworkId, NetworkId providerNetworkId, PolicyCapability capability) {
    public PolicyKey {
        Objects.requireNonNull(consumerNetworkId);
        Objects.requireNonNull(providerNetworkId);
        Objects.requireNonNull(capability);
    }
}
