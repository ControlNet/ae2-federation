package space.controlnet.ae2federation.observability.id;

import space.controlnet.ae2federation.fabric.FabricId;
import space.controlnet.ae2federation.policy.PolicyKey;

public record PolicyId(FabricId fabricId, String value) implements ScopedObservationId {
    public PolicyId {
        StableObservationId.validate(fabricId, "policy", value);
    }

    public static PolicyId forKey(FabricId fabricId, PolicyKey key) {
        var nativeKey = key.consumerNetworkId() + ">" + key.providerNetworkId() + ":" + key.capability();
        return new PolicyId(fabricId, StableObservationId.create(fabricId, "policy", nativeKey));
    }
}
