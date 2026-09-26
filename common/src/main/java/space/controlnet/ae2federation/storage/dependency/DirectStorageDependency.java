package space.controlnet.ae2federation.storage.dependency;

import java.util.Objects;
import java.util.Set;
import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.policy.PolicyRule;

public record DirectStorageDependency(PolicyKey key, PolicyRevision policyRevision, PolicyRule rule,
        Set<FederationDomainReference> federationDomainReferences) {
    public DirectStorageDependency {
        Objects.requireNonNull(key);
        Objects.requireNonNull(policyRevision);
        Objects.requireNonNull(rule);
        federationDomainReferences = Set.copyOf(federationDomainReferences);
        if (key.capability() != PolicyCapability.STORAGE || policyRevision.equals(PolicyRevision.NONE)
                || !rule.enabled() || federationDomainReferences.isEmpty()) {
            throw new IllegalArgumentException("Direct dependency requires an active revisioned Storage rule and Federation Domain");
        }
    }
}
