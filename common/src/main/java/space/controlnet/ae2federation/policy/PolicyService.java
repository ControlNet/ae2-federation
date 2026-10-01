package space.controlnet.ae2federation.policy;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.crafting.binding.CraftingBindingService;
import space.controlnet.ae2federation.domain.FederationDomainRegistry;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkIdentityService;
import space.controlnet.ae2federation.persistence.PolicySavedData;
import space.controlnet.ae2federation.storage.mount.StorageMountService;
import space.controlnet.ae2federation.energy.EnergySharingService;

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
        AuthorityEpoch.advance();
    }

    /** The service {@link #get} returned last, checked before the map: nearly every call is for the ticked level. */
    private static volatile PolicyService last;

    public static PolicyService get(ServerLevel level) {
        var cached = last;
        if (cached != null && cached.level == level) {
            return cached;
        }
        var service = SERVICES.computeIfAbsent(level, PolicyService::new);
        last = service;
        return service;
    }

    /** Drops the level's service when the level unloads; the service holds the level, so it cannot be weakly held. */
    public static void closeLevel(ServerLevel level) {
        last = null;
        AuthorityEpoch.advance();
        SERVICES.remove(level);
    }

    public PolicyMutationResult edit(PolicyEdit edit) {
        var result = data.edit(edit);
        if (result instanceof PolicyMutationResult.Accepted) {
            StorageMountService.reconcileIfPresent(level);
            CraftingBindingService.reconcileIfPresent(level);
            EnergySharingService.reconcileIfPresent(level);
        }
        return result;
    }

    public PolicyMutationResult delete(PolicyDelete deletion) {
        var result = data.delete(deletion);
        if (result instanceof PolicyMutationResult.Accepted) {
            StorageMountService.reconcileIfPresent(level);
            CraftingBindingService.reconcileIfPresent(level);
            EnergySharingService.reconcileIfPresent(level);
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
        return activation(key, endpoints, FederationDomainRegistryAccess.get(level));
    }

    /** As {@link #activation(PolicyKey, PolicyRuntimeEndpoints)}, with this level's registry the caller already holds. */
    public PolicyActivationState activation(PolicyKey key, PolicyRuntimeEndpoints endpoints,
            FederationDomainRegistry registry) {
        return activation(data.configured(key), key, endpoints, registry);
    }

    /**
     * Activation of a record the caller has just read with {@link #configured}, without looking it up again; the
     * registry is this level's, which the caller already holds.
     */
    public PolicyActivationState activation(PolicyRecord.Configured configured, PolicyRuntimeEndpoints endpoints,
            FederationDomainRegistry registry) {
        return activation(Optional.of(configured), configured.key(), endpoints, registry);
    }

    /**
     * As {@link #activation(PolicyRecord.Configured, PolicyRuntimeEndpoints, FederationDomainRegistry)}, from the
     * identity services of the two Grids, which a caller holding them for the Grids' lifetime passes directly.
     */
    public PolicyActivationState activation(PolicyRecord.Configured configured, NetworkIdentityService consumerIdentity,
            NetworkIdentityService providerIdentity, BackendStatus backendStatus, FederationDomainRegistry registry) {
        return PolicyActivation.classify(new PolicyActivationRequest(Optional.of(configured), configured.key(),
                consumerIdentity.settlement(), providerIdentity.settlement(), registry, backendStatus));
    }

    private PolicyActivationState activation(Optional<PolicyRecord.Configured> configured, PolicyKey key,
            PolicyRuntimeEndpoints endpoints, FederationDomainRegistry registry) {
        var consumer = endpoints.consumerGrid().getService(NetworkIdentityService.class).settlement();
        var provider = endpoints.providerGrid().getService(NetworkIdentityService.class).settlement();
        return PolicyActivation.classify(new PolicyActivationRequest(configured, key, consumer, provider, registry,
                endpoints.backendStatus()));
    }

    /**
     * Advances on every rule edit and deletion: while it and this service are unchanged, every rule reads as it did.
     */
    public long highWatermark() {
        return data.highWatermark().value();
    }

    /**
     * The identity part of {@link #activation}: whether both Grids are settled on {@code key}'s networks. A caller
     * whose rule and domains are unchanged since an {@code ACTIVE} activation needs only this part again.
     */
    public boolean identitiesMatch(PolicyKey key, NetworkIdentityService consumerIdentity,
            NetworkIdentityService providerIdentity) {
        return PolicyActivation.matches(consumerIdentity.settlement(), key.consumerNetworkId())
                && PolicyActivation.matches(providerIdentity.settlement(), key.providerNetworkId());
    }

    public int configuredCount() {
        return data.configuredCount();
    }

    public int tombstoneCount() {
        return data.tombstoneCount();
    }
}
