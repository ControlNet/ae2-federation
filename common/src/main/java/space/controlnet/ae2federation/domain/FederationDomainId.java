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

    /** A physical domain keeps its id while it grows, shrinks or absorbs others; a registry never reuses a sequence. */
    public static FederationDomainId physical(long sequence) {
        if (sequence < 1) {
            throw new IllegalArgumentException("Physical Federation Domain sequence must be positive");
        }
        return new FederationDomainId("physical:" + sequence);
    }

    @Override
    public int compareTo(FederationDomainId other) {
        return value.compareTo(other.value);
    }
}
