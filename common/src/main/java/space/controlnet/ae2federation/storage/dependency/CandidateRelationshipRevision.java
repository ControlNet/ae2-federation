package space.controlnet.ae2federation.storage.dependency;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.storage.provenance.SourceGeneration;

public record CandidateRelationshipRevision(long compilationRevision, long fabricTopologyRevision,
        SourceGeneration sourceGeneration, Map<PolicyKey, PolicyRevision> policyRevisions,
        Set<FabricReference> fabricReferences) {
    public CandidateRelationshipRevision {
        if (compilationRevision < 1 || fabricTopologyRevision < 0) {
            throw new IllegalArgumentException("Candidate relationship revisions are invalid");
        }
        Objects.requireNonNull(sourceGeneration);
        policyRevisions = Map.copyOf(policyRevisions);
        fabricReferences = Set.copyOf(fabricReferences);
        if (policyRevisions.isEmpty() || fabricReferences.isEmpty()) {
            throw new IllegalArgumentException("Candidate relationship requires policy and Fabric dependencies");
        }
    }

    public boolean isCurrent(long topologyRevision, SourceGeneration currentSourceGeneration,
            Function<PolicyKey, PolicyRevision> policyLookup, Predicate<FabricReference> fabricLookup) {
        return fabricTopologyRevision == topologyRevision && sourceGeneration.equals(currentSourceGeneration)
                && policyRevisions.entrySet().stream().allMatch(entry -> entry.getValue().equals(policyLookup.apply(entry.getKey())))
                && fabricReferences.stream().allMatch(fabricLookup);
    }
}
