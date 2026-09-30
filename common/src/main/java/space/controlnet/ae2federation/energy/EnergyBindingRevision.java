package space.controlnet.ae2federation.energy;

import java.util.Set;
import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.policy.PolicyRevision;

public record EnergyBindingRevision(PolicyRevision policyRevision, long topologyRevision,
        Set<FederationDomainReference> federationDomains, EnergyProviderGeneration providerGeneration) {
    public EnergyBindingRevision {
        federationDomains = Set.copyOf(federationDomains);
    }

    /**
     * Whether a binding captured with this revision still carries the same authority. The topology revision is kept for
     * evidence only: the domains are held by their references, whose generation changes with their member networks, so
     * a Federation block joining or leaving anywhere in the level does not replace the binding.
     */
    public boolean sameAuthority(EnergyBindingRevision other) {
        return policyRevision.equals(other.policyRevision) && federationDomains.equals(other.federationDomains)
                && providerGeneration.equals(other.providerGeneration);
    }
}
