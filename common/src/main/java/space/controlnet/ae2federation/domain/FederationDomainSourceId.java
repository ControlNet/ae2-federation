package space.controlnet.ae2federation.domain;

import java.util.Objects;

public record FederationDomainSourceId(String value) implements Comparable<FederationDomainSourceId> {
    public FederationDomainSourceId {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Federation Domain source identity must not be blank");
        }
    }

    public static FederationDomainSourceId nativePort(FederationDomainPortId port) {
        return new FederationDomainSourceId("native:" + port.node() + ":" + port.port());
    }

    public FederationDomainSourceId child(String role) {
        return new FederationDomainSourceId(value + ":" + role);
    }

    @Override
    public int compareTo(FederationDomainSourceId other) {
        return value.compareTo(other.value);
    }
}
