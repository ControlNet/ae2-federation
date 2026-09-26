package space.controlnet.ae2federation.crafting.binding;

import appeng.api.networking.IGrid;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;

final class CraftingFederationDomainObserver {
    private final ServerLevel level;
    private final Map<NetworkId, IGrid> loadedGrids = new HashMap<>();

    CraftingFederationDomainObserver(ServerLevel level) {
        this.level = level;
    }

    void register(IGrid grid) {
        FederationDomainRegistryAccess.confirmedNetworkId(grid).ifPresent(networkId -> loadedGrids.put(networkId, grid));
    }

    void register(Iterable<IGrid> grids) {
        grids.forEach(this::register);
    }

    Map<PolicyKey, CraftingRelationship> relationships() {
        discardStaleGrids();
        var result = new HashMap<PolicyKey, CraftingRelationship>();
        for (var federationDomain : FederationDomainRegistryAccess.get(level).snapshot().federationDomains().values()) {
            var members = federationDomain.memberships().keySet().stream().map(loadedGrids::get)
                    .filter(java.util.Objects::nonNull).toList();
            for (var consumer : members) {
                for (var provider : members) {
                    if (consumer != provider) {
                        var key = new PolicyKey(FederationDomainRegistryAccess.confirmedNetworkId(consumer).orElseThrow(),
                                FederationDomainRegistryAccess.confirmedNetworkId(provider).orElseThrow(),
                                PolicyCapability.CRAFTING);
                        result.put(key, new CraftingRelationship(key, consumer, provider));
                    }
                }
            }
        }
        return Map.copyOf(result);
    }

    Set<FederationDomainReference> references(CraftingRelationship relationship) {
        var registry = FederationDomainRegistryAccess.get(level);
        var consumer = registry.federationdomainsFor(relationship.key().consumerNetworkId());
        var provider = registry.federationdomainsFor(relationship.key().providerNetworkId());
        return consumer.stream().filter(provider::contains).map(registry::federationDomain)
                .flatMap(java.util.Optional::stream).map(snapshot -> snapshot.reference())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    long topologyRevision() {
        return FederationDomainRegistryAccess.get(level).snapshot().topologyRevision();
    }

    void clear() {
        loadedGrids.clear();
    }

    private void discardStaleGrids() {
        loadedGrids.entrySet().removeIf(entry -> FederationDomainRegistryAccess.confirmedNetworkId(entry.getValue())
                .filter(entry.getKey()::equals).isEmpty());
    }
}
