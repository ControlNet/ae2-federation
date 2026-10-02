package space.controlnet.ae2federation.storage.mount;

import appeng.api.stacks.AEKey;
import java.util.Objects;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyResource;
import space.controlnet.ae2federation.storage.dependency.EffectiveSourceRelationship;
import space.controlnet.ae2federation.storage.dependency.EffectiveStorageAuthority;

final class StorageRelationshipAuthority implements StorageProjectionAuthorization {
    private final Supplier<EffectiveSourceRelationship> relationship;
    private final Supplier<@Nullable EffectiveStorageAuthority> readyAuthority;

    /**
     * @param readyAuthority the current relationship's authority while its source is ready and it is current, else
     *     null; evaluated on every operation
     */
    StorageRelationshipAuthority(Supplier<EffectiveSourceRelationship> relationship,
            Supplier<@Nullable EffectiveStorageAuthority> readyAuthority) {
        this.relationship = Objects.requireNonNull(relationship);
        this.readyAuthority = Objects.requireNonNull(readyAuthority);
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

    private @Nullable EffectiveStorageAuthority readyAuthority() {
        return readyAuthority.get();
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
