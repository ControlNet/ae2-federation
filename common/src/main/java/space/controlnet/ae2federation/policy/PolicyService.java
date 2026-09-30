package space.controlnet.ae2federation.policy;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.crafting.binding.CraftingBindingService;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkIdentityService;
import space.controlnet.ae2federation.persistence.PolicySavedData;
import space.controlnet.ae2federation.storage.mount.StorageMountService;
import space.controlnet.ae2federation.energy.EnergyBindingService;

public final class PolicyService {
    /**
     * One service per level, dropped by {@link #closeLevel}: {@link #get} is on every storage, crafting and energy
     * operation's validation path, and the policy store is the overworld's saved data, which lives as long as the
     * server.
     */
    private static final java.util.Map<ServerLevel, PolicyService> SERVICES = new java.util.WeakHashMap<>();

    private final ServerLevel level;
    private final PolicySavedData data;

    private PolicyService(ServerLevel level) {
        this.level = level;
        data = PolicySavedData.get(level);
    }

    public static PolicyService get(ServerLevel level) {
        return SERVICES.computeIfAbsent(level, PolicyService::new);
    }

    /** Drops the level's service when the level unloads; the service holds the level, so it cannot be weakly held. */
    public static void closeLevel(ServerLevel level) {
        SERVICES.remove(level);
    }

    public PolicyMutationResult edit(PolicyEdit edit) {
        var result = data.edit(edit);
        if (result instanceof PolicyMutationResult.Accepted) {
            StorageMountService.reconcileIfPresent(level);
            CraftingBindingService.reconcileIfPresent(level);
            EnergyBindingService.reconcileIfPresent(level);
        }
        return result;
    }

    public PolicyMutationResult delete(PolicyDelete deletion) {
        var result = data.delete(deletion);
        if (result instanceof PolicyMutationResult.Accepted) {
            StorageMountService.reconcileIfPresent(level);
            CraftingBindingService.reconcileIfPresent(level);
            EnergyBindingService.reconcileIfPresent(level);
        }
        return result;
    }

    public Optional<PolicyRecord.Configured> configured(PolicyKey key) {
        return data.configured(key);
    }

    public PolicyRevision revision(PolicyKey key) {
        return data.revision(key);
    }

    public PolicyActivationState activation(PolicyKey key, PolicyRuntimeEndpoints endpoints) {
        var consumer = endpoints.consumerGrid().getService(NetworkIdentityService.class).settlement();
        var provider = endpoints.providerGrid().getService(NetworkIdentityService.class).settlement();
        return PolicyActivation.classify(new PolicyActivationRequest(data.configured(key), key, consumer, provider,
                FederationDomainRegistryAccess.get(level), endpoints.backendStatus()));
    }

    public int configuredCount() {
        return data.configuredCount();
    }

    public int tombstoneCount() {
        return data.tombstoneCount();
    }
}
