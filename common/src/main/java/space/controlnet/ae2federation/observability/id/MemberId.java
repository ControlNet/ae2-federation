package space.controlnet.ae2federation.observability.id;

import space.controlnet.ae2federation.fabric.FabricId;
import space.controlnet.ae2federation.identity.NetworkId;

public record MemberId(FabricId fabricId, String value) implements ScopedObservationId {
    public MemberId {
        StableObservationId.validate(fabricId, "member", value);
    }

    public static MemberId forNetwork(FabricId fabricId, NetworkId networkId) {
        return new MemberId(fabricId, StableObservationId.create(fabricId, "member", networkId.toString()));
    }
}
