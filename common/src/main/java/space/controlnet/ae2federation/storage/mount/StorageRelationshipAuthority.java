package space.controlnet.ae2federation.storage.mount;

import appeng.api.stacks.AEKey;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.policy.BackendStatus;
import space.controlnet.ae2federation.policy.PolicyActivationState;
import space.controlnet.ae2federation.policy.PolicyFilterMode;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyResource;
import space.controlnet.ae2federation.policy.PolicyRuntimeEndpoints;
import space.controlnet.ae2federation.policy.PolicyService;

final class StorageRelationshipAuthority implements StorageProjectionAuthorization {
    private final ServerLevel level;
    private final StorageRelationship relationship;
    private final BooleanSupplier sourceReady;

    StorageRelationshipAuthority(ServerLevel level, StorageRelationship relationship, BooleanSupplier sourceReady) {
        this.level = Objects.requireNonNull(level);
        this.relationship = Objects.requireNonNull(relationship);
        this.sourceReady = Objects.requireNonNull(sourceReady);
    }

    @Override
    public boolean permits(PolicyOperation operation, AEKey key) {
        if (!ready()) {
            return false;
        }
        var configured = PolicyService.get(level).configured(relationship.key()).orElse(null);
        return configured != null && configured.rule().operations().contains(operation)
                && permits(configured.rule().filter().mode(), configured.rule().filter().entries(), key);
    }

    @Override
    public boolean permitsView(AEKey key) {
        return permits(PolicyOperation.VIEW, key);
    }

    @Override
    public boolean ready() {
        if (!sourceReady.getAsBoolean()) {
            return false;
        }
        var endpoints = new PolicyRuntimeEndpoints(relationship.consumerGrid(), relationship.providerGrid(),
                BackendStatus.READY);
        return PolicyService.get(level).activation(relationship.key(), endpoints) == PolicyActivationState.ACTIVE;
    }

    private static boolean permits(PolicyFilterMode mode, java.util.Set<PolicyResource> entries, AEKey key) {
        var resource = new PolicyResource(key.getType().getId(), key.getId());
        if (mode == PolicyFilterMode.ALL) {
            return true;
        }
        if (mode == PolicyFilterMode.ALLOW_LIST) {
            return entries.contains(resource);
        }
        return !entries.contains(resource);
    }
}
