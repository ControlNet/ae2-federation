package space.controlnet.ae2federation.domain;

public record FederationDomainReference(FederationDomainId federationDomainId, long generation) {
    public FederationDomainReference {
        java.util.Objects.requireNonNull(federationDomainId);
        if (generation < 0) {
            throw new IllegalArgumentException("Federation Domain generation cannot be negative");
        }
    }
}
