package space.controlnet.ae2federation.observability.state;

import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.observability.id.ScopedObservationId;

public interface ObservationState {
    FederationDomainReference scope();

    ScopedObservationId id();
}
