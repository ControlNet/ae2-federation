package space.controlnet.ae2federation.energy;

import java.util.Set;
import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.policy.PolicyRevision;

public record EnergyBindingRevision(PolicyRevision policyRevision, long topologyRevision,
        Set<FederationDomainReference> federationDomains, EnergyProviderGeneration providerGeneration) {
    public EnergyBindingRevision {
        federationDomains = Set.copyOf(federationDomains);
    }
}
