package space.controlnet.ae2federation.storage.mount;

import appeng.api.networking.IGrid;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;

final class StorageFederationDomainObserver {
    private final ServerLevel level;
    private final Map<NetworkId, IGrid> loadedGrids = new HashMap<>();

    StorageFederationDomainObserver(ServerLevel level) {
        this.level = level;
    }

    void register(Iterable<IGrid> grids) {
        grids.forEach(this::register);
    }

    void register(IGrid grid) {
        FederationDomainRegistryAccess.confirmedNetworkId(grid).ifPresent(networkId -> loadedGrids.put(networkId, grid));
    }

    Map<PolicyKey, StorageRelationship> relationships() {
        discardStaleGrids();
        var relationships = new HashMap<PolicyKey, StorageRelationship>();
        for (var federationDomain : FederationDomainRegistryAccess.get(level).snapshot().federationDomains().values()) {
            var members = federationDomain.memberships().keySet().stream().map(loadedGrids::get)
                    .filter(java.util.Objects::nonNull).toList();
            for (var consumer : members) {
                for (var provider : members) {
                    if (consumer != provider) {
                        var key = new PolicyKey(FederationDomainRegistryAccess.confirmedNetworkId(consumer).orElseThrow(),
                                FederationDomainRegistryAccess.confirmedNetworkId(provider).orElseThrow(),
                                PolicyCapability.STORAGE);
                        relationships.put(key, new StorageRelationship(key, consumer, provider));
                    }
                }
            }
        }
        return relationships;
    }

    Map<NetworkId, IGrid> loadedGrids() {
        discardStaleGrids();
        return Map.copyOf(loadedGrids);
    }

    Set<space.controlnet.ae2federation.domain.FederationDomainReference> references(StorageRelationship relationship) {
        var registry = FederationDomainRegistryAccess.get(level);
        var consumerFederationDomains = registry.federationdomainsFor(relationship.key().consumerNetworkId());
        var providerFederationDomains = registry.federationdomainsFor(relationship.key().providerNetworkId());
        return consumerFederationDomains.stream().filter(providerFederationDomains::contains).map(registry::federationDomain)
                .flatMap(java.util.Optional::stream).map(space.controlnet.ae2federation.domain.FederationDomainSnapshot::reference)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    long topologyRevision() {
        return FederationDomainRegistryAccess.get(level).snapshot().topologyRevision();
    }

    boolean contains(StorageRelationship relationship) {
        var consumerId = FederationDomainRegistryAccess.confirmedNetworkId(relationship.consumerGrid());
        var providerId = FederationDomainRegistryAccess.confirmedNetworkId(relationship.providerGrid());
        if (consumerId.isEmpty() || providerId.isEmpty()) {
            return false;
        }
        var registry = FederationDomainRegistryAccess.get(level);
        var providerFederationDomains = registry.federationdomainsFor(providerId.orElseThrow());
        return registry.federationdomainsFor(consumerId.orElseThrow()).stream().anyMatch(providerFederationDomains::contains);
    }

    void clear() {
        loadedGrids.clear();
    }

    private void discardStaleGrids() {
        loadedGrids.entrySet().removeIf(entry -> FederationDomainRegistryAccess.confirmedNetworkId(entry.getValue())
                .filter(entry.getKey()::equals).isEmpty());
    }
}
