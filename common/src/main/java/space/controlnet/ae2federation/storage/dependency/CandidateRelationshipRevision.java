package space.controlnet.ae2federation.storage.dependency;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.storage.provenance.SourceGeneration;

public record CandidateRelationshipRevision(long compilationRevision, long federationDomainTopologyRevision,
        SourceGeneration sourceGeneration, Map<PolicyKey, PolicyRevision> policyRevisions,
        Set<FederationDomainReference> federationDomainReferences) {
    public CandidateRelationshipRevision {
        if (compilationRevision < 1 || federationDomainTopologyRevision < 0) {
            throw new IllegalArgumentException("Candidate relationship revisions are invalid");
        }
        Objects.requireNonNull(sourceGeneration);
        policyRevisions = Map.copyOf(policyRevisions);
        federationDomainReferences = Set.copyOf(federationDomainReferences);
        if (policyRevisions.isEmpty() || federationDomainReferences.isEmpty()) {
            throw new IllegalArgumentException("Candidate relationship requires policy and Federation Domain dependencies");
        }
    }

    public boolean isCurrent(long topologyRevision, SourceGeneration currentSourceGeneration,
            Function<PolicyKey, PolicyRevision> policyLookup, Predicate<FederationDomainReference> federationDomainLookup) {
        return federationDomainTopologyRevision == topologyRevision && sourceGeneration.equals(currentSourceGeneration)
                && policyRevisions.entrySet().stream().allMatch(entry -> entry.getValue().equals(policyLookup.apply(entry.getKey())))
                && federationDomainReferences.stream().allMatch(federationDomainLookup);
    }
}
