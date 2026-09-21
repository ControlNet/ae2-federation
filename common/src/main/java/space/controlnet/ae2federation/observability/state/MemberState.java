package space.controlnet.ae2federation.observability.state;

import java.util.Objects;
import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.observability.id.MemberId;

public record MemberState(FabricReference scope, MemberId id, NetworkId networkId, String status)
        implements ObservationState {
    public MemberState {
        ObservationStateSupport.validate(scope, id, status);
        Objects.requireNonNull(networkId);
    }
}
