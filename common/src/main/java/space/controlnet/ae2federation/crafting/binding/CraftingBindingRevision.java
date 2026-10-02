package space.controlnet.ae2federation.crafting.binding;

import java.util.Objects;
import java.util.Set;
import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.policy.PolicyRevision;

public record CraftingBindingRevision(PolicyRevision policyRevision, long topologyRevision,
        Set<FederationDomainReference> federationDomains, ProviderGeneration providerGeneration) {
    public CraftingBindingRevision {
        Objects.requireNonNull(policyRevision);
        Objects.requireNonNull(providerGeneration);
        federationDomains = Set.copyOf(federationDomains);
        if (policyRevision.equals(PolicyRevision.NONE) || topologyRevision < 1 || federationDomains.isEmpty()) {
            throw new IllegalArgumentException("Crafting binding revision requires current Policy and Federation Domain evidence");
        }
    }

    /**
     * Whether a binding captured with this revision still carries the same authority. The topology revision is kept for
     * evidence only: the domains are held by their references, whose generation changes with their member networks, so
     * a Federation block joining or leaving anywhere in the level neither replaces the binding nor stales a request.
     */
    public boolean sameAuthority(CraftingBindingRevision other) {
        return policyRevision.equals(other.policyRevision) && federationDomains.equals(other.federationDomains)
                && providerGeneration.equals(other.providerGeneration);
    }
}
