package space.controlnet.ae2federation.observability.state;

import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.observability.id.ProviderId;

public record ProviderState(FederationDomainReference scope, ProviderId id, String status) implements ObservationState {
    public ProviderState {
        ObservationStateSupport.validate(scope, id, status);
    }
}
