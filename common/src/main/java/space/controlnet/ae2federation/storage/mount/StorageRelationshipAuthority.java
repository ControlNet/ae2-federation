package space.controlnet.ae2federation.storage.mount;

import appeng.api.stacks.AEKey;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyResource;
import space.controlnet.ae2federation.storage.dependency.EffectiveSourceRelationship;
import space.controlnet.ae2federation.storage.dependency.EffectiveStorageAuthority;

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
        var authority = readyAuthority();
        return authority != null && permits(authority, operation, key);
    }

    @Override
    public boolean permitsView(AEKey key) {
        return permits(PolicyOperation.VIEW, key);
    }

    @Override
    public boolean ready() {
        return readyAuthority() != null;
    }

    @Override
    public @Nullable ResourceAuthorization readyAuthorization() {
        var authority = readyAuthority();
        if (authority == null) {
            return null;
        }
        return new ResourceAuthorization() {
            @Override
            public boolean permits(PolicyOperation operation, AEKey key) {
                return StorageRelationshipAuthority.permits(authority, operation, key);
            }

            @Override
            public boolean permitsAll(PolicyOperation operation) {
                return authority.permitsAll(operation);
            }
        };
    }

    /** The current relationship's authority while its source is ready and it is current, else null. */
    private @Nullable EffectiveStorageAuthority readyAuthority() {
        if (!sourceReady.getAsBoolean()) {
            return null;
        }
        var candidate = relationship.get();
        if (candidate == null || !relationshipCurrent.test(candidate)) {
            return null;
        }
        return candidate.authority();
    }

    private static boolean permits(EffectiveStorageAuthority authority, PolicyOperation operation, AEKey key) {
        return authority.permitsAll(operation)
                || authority.permits(operation, new PolicyResource(key.getType().getId(), key.getId()));
    }

    @Override
    public java.util.Set<space.controlnet.ae2federation.domain.FederationDomainReference> scopes() {
        var candidate = relationship.get();
        return candidate == null ? java.util.Set.of() : candidate.revision().federationDomainReferences();
    }
}
