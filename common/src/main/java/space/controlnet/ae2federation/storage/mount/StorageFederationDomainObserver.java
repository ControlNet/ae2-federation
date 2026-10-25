package space.controlnet.ae2federation.storage.mount;

import appeng.api.networking.IGrid;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.ObservedGrids;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyService;

final class StorageFederationDomainObserver {
    private final ServerLevel level;
    private final ObservedGrids observed = new ObservedGrids();

    StorageFederationDomainObserver(ServerLevel level) {
        this.level = level;
    }

    void register(Iterable<IGrid> grids) {
        grids.forEach(this::register);
    }

    void register(IGrid grid) {
        observed.add(grid);
    }

    Map<PolicyKey, StorageRelationship> relationships() {
        var loadedGrids = observed.confirmed();
        var relationships = new HashMap<PolicyKey, StorageRelationship>();
        var registry = FederationDomainRegistryAccess.get(level);
        // By rule: every two networks of a domain would be N(N-1) pairs, and only pairs with a rule link anything.
        for (var rule : PolicyService.get(level).configuredRules(PolicyCapability.STORAGE)) {
            var key = rule.key();
            var consumer = loadedGrids.get(key.consumerNetworkId());
            var provider = loadedGrids.get(key.providerNetworkId());
            if (consumer != null && provider != null && consumer != provider
                    && registry.shareFederationDomain(key.consumerNetworkId(), key.providerNetworkId())) {
                relationships.put(key, new StorageRelationship(key, consumer, provider));
            }
        }
        return relationships;
    }

    Map<NetworkId, IGrid> loadedGrids() {
        return Map.copyOf(observed.confirmed());
    }

    Set<space.controlnet.ae2federation.domain.FederationDomainReference> references(StorageRelationship relationship) {
        return FederationDomainRegistryAccess.get(level).sharedFederationDomainReferences(
                relationship.key().consumerNetworkId(), relationship.key().providerNetworkId());
    }

    long topologyRevision() {
        return FederationDomainRegistryAccess.get(level).topologyRevision();
    }

    boolean contains(StorageRelationship relationship) {
        var consumerId = FederationDomainRegistryAccess.confirmedNetworkId(relationship.consumerGrid());
        var providerId = FederationDomainRegistryAccess.confirmedNetworkId(relationship.providerGrid());
        if (consumerId.isEmpty() || providerId.isEmpty()) {
            return false;
        }
        return FederationDomainRegistryAccess.get(level).shareFederationDomain(consumerId.orElseThrow(),
                providerId.orElseThrow());
    }

    void clear() {
        observed.clear();
    }
}
