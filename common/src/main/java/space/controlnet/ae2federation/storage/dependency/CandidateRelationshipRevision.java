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

    /**
     * The compiled topology revision is kept for evidence only: a change to a domain the relationship depends on
     * reaches it through that domain's reference, while a change anywhere else in the level leaves it current.
     */
    public boolean isCurrent(SourceGeneration currentSourceGeneration,
            Function<PolicyKey, PolicyRevision> policyLookup, Predicate<FederationDomainReference> federationDomainLookup) {
        if (!sourceGeneration.equals(currentSourceGeneration)) {
            return false;
        }
        for (var entry : policyRevisions.entrySet()) {
            if (!entry.getValue().equals(policyLookup.apply(entry.getKey()))) {
                return false;
            }
        }
        for (var reference : federationDomainReferences) {
            if (!federationDomainLookup.test(reference)) {
                return false;
            }
        }
        return true;
    }
}
