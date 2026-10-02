package space.controlnet.ae2federation.policy;

import java.util.Objects;
import space.controlnet.ae2federation.identity.NetworkId;

public record PolicyKey(NetworkId consumerNetworkId, NetworkId providerNetworkId, PolicyCapability capability) {
    public PolicyKey {
        Objects.requireNonNull(consumerNetworkId);
        Objects.requireNonNull(providerNetworkId);
        Objects.requireNonNull(capability);
    }

    /**
     * The record's hash with the capability's ordinal in place of its identity hash, which reads the enum constant's
     * object header on every map lookup of a key.
     */
    @Override
    public int hashCode() {
        return 31 * (31 * consumerNetworkId.hashCode() + providerNetworkId.hashCode()) + capability.ordinal();
    }
}
