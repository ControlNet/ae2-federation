package space.controlnet.ae2federation.storage.dependency;

import java.util.Map;
import java.util.Optional;

public record DependencyCompilation(Map<EffectiveSourceRelationshipKey, EffectiveSourceRelationship> relationships,
        int frontierRelaxations, int originCycleRejections) {
    public DependencyCompilation {
        relationships = Map.copyOf(relationships);
    }

    public Optional<EffectiveSourceRelationship> relationship(EffectiveSourceRelationshipKey key) {
        return Optional.ofNullable(relationships.get(key));
    }
}
