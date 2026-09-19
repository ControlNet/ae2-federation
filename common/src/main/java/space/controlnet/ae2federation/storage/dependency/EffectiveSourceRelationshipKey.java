package space.controlnet.ae2federation.storage.dependency;

import java.util.Objects;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.storage.provenance.OriginNetworkId;

public record EffectiveSourceRelationshipKey(NetworkId consumerNetworkId, OriginNetworkId origin,
        PolicyCapability capability) {
    public EffectiveSourceRelationshipKey {
        Objects.requireNonNull(consumerNetworkId);
        Objects.requireNonNull(origin);
        Objects.requireNonNull(capability);
        if (capability != PolicyCapability.STORAGE) {
            throw new IllegalArgumentException("Effective source relationship requires STORAGE capability");
        }
    }

    public PolicyKey policyKey() {
        return new PolicyKey(consumerNetworkId, origin.value(), capability);
    }
}
