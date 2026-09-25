package space.controlnet.ae2federation.policy;

import java.util.Objects;
import java.util.Optional;
import space.controlnet.ae2federation.domain.FederationDomainRegistry;
import space.controlnet.ae2federation.identity.IdentitySettlement;

record PolicyActivationRequest(Optional<PolicyRecord.Configured> configured, PolicyKey key, IdentitySettlement consumerIdentity,
        IdentitySettlement providerIdentity, FederationDomainRegistry federationDomainRegistry, BackendStatus backendStatus) {
    public PolicyActivationRequest {
        Objects.requireNonNull(configured);
        Objects.requireNonNull(key);
        Objects.requireNonNull(consumerIdentity);
        Objects.requireNonNull(providerIdentity);
        Objects.requireNonNull(federationDomainRegistry);
        Objects.requireNonNull(backendStatus);
    }
}
