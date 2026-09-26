package space.controlnet.ae2federation.domain;

import java.util.Objects;

public record FederationDomainId(String value) implements Comparable<FederationDomainId> {
    public FederationDomainId {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Federation Domain identity must not be blank");
        }
    }

    public static FederationDomainId direct(FederationDomainSourceId source) {
        return new FederationDomainId("direct:" + source.value());
    }

    public static FederationDomainId physical(FederationDomainNodeId firstNode) {
        return new FederationDomainId("physical:" + firstNode);
    }

    @Override
    public int compareTo(FederationDomainId other) {
        return value.compareTo(other.value);
    }
}
