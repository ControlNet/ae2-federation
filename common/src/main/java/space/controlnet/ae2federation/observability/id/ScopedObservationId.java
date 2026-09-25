package space.controlnet.ae2federation.observability.id;

import space.controlnet.ae2federation.domain.FederationDomainId;

public interface ScopedObservationId extends Comparable<ScopedObservationId> {
    FederationDomainId federationDomainId();

    String value();

    @Override
    default int compareTo(ScopedObservationId other) {
        var kind = getClass().getName().compareTo(other.getClass().getName());
        if (kind != 0) {
            return kind;
        }
        var federationDomain = federationDomainId().toString().compareTo(other.federationDomainId().toString());
        return federationDomain != 0 ? federationDomain : value().compareTo(other.value());
    }
}
