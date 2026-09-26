package space.controlnet.ae2federation.observability.state;

import java.util.Objects;
import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.observability.id.MemberId;

public record MemberState(FederationDomainReference scope, MemberId id, NetworkId networkId, String status)
        implements ObservationState {
    public MemberState {
        ObservationStateSupport.validate(scope, id, status);
        Objects.requireNonNull(networkId);
    }
}
