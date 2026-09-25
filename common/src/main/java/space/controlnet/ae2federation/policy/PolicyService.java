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
    private final ServerLevel level;
    private final PolicySavedData data;

    private PolicyService(ServerLevel level) {
        this.level = level;
        data = PolicySavedData.get(level);
    }

    public static PolicyService get(ServerLevel level) {
        return new PolicyService(level);
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
