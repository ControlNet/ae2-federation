package space.controlnet.ae2federation.storage.mount;

import appeng.api.stacks.AEKey;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyResource;
import space.controlnet.ae2federation.storage.dependency.EffectiveSourceRelationship;

final class StorageRelationshipAuthority implements StorageProjectionAuthorization {
    private final Supplier<EffectiveSourceRelationship> relationship;
    private final Predicate<EffectiveSourceRelationship> relationshipCurrent;
    private final BooleanSupplier sourceReady;

    StorageRelationshipAuthority(Supplier<EffectiveSourceRelationship> relationship,
            Predicate<EffectiveSourceRelationship> relationshipCurrent, BooleanSupplier sourceReady) {
        this.relationship = Objects.requireNonNull(relationship);
        this.relationshipCurrent = Objects.requireNonNull(relationshipCurrent);
        this.sourceReady = Objects.requireNonNull(sourceReady);
    }

    @Override
    public boolean permits(PolicyOperation operation, AEKey key) {
        if (!ready()) {
            return false;
        }
        var candidate = relationship.get();
        return candidate != null && candidate.authority().permits(operation,
                new PolicyResource(key.getType().getId(), key.getId()));
    }

    @Override
    public boolean permitsView(AEKey key) {
        return permits(PolicyOperation.VIEW, key);
    }

    @Override
    public boolean ready() {
        var candidate = relationship.get();
        return sourceReady.getAsBoolean() && candidate != null && relationshipCurrent.test(candidate);
    }

    @Override
    public java.util.Set<space.controlnet.ae2federation.fabric.FabricReference> scopes() {
        var candidate = relationship.get();
        return candidate == null ? java.util.Set.of() : candidate.revision().fabricReferences();
    }
}
