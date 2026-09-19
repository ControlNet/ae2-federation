package space.controlnet.ae2federation.storage.mount;

import appeng.api.networking.IGrid;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;

final class StorageFabricObserver {
    private final ServerLevel level;
    private final Map<NetworkId, IGrid> loadedGrids = new HashMap<>();

    StorageFabricObserver(ServerLevel level) {
        this.level = level;
    }

    void register(Iterable<IGrid> grids) {
        grids.forEach(this::register);
    }

    void register(IGrid grid) {
        FabricRegistryAccess.confirmedNetworkId(grid).ifPresent(networkId -> loadedGrids.put(networkId, grid));
    }

    Map<PolicyKey, StorageRelationship> relationships() {
        loadedGrids.entrySet().removeIf(entry -> FabricRegistryAccess.confirmedNetworkId(entry.getValue())
                .filter(entry.getKey()::equals).isEmpty());
        var relationships = new HashMap<PolicyKey, StorageRelationship>();
        for (var fabric : FabricRegistryAccess.get(level).snapshot().fabrics().values()) {
            var members = fabric.memberships().keySet().stream().map(loadedGrids::get)
                    .filter(java.util.Objects::nonNull).toList();
            for (var consumer : members) {
                for (var provider : members) {
                    if (consumer != provider) {
                        var key = new PolicyKey(FabricRegistryAccess.confirmedNetworkId(consumer).orElseThrow(),
                                FabricRegistryAccess.confirmedNetworkId(provider).orElseThrow(),
                                PolicyCapability.STORAGE);
                        relationships.put(key, new StorageRelationship(key, consumer, provider));
                    }
                }
            }
        }
        return relationships;
    }

    boolean contains(StorageRelationship relationship) {
        var consumerId = FabricRegistryAccess.confirmedNetworkId(relationship.consumerGrid());
        var providerId = FabricRegistryAccess.confirmedNetworkId(relationship.providerGrid());
        if (consumerId.isEmpty() || providerId.isEmpty()) {
            return false;
        }
        var registry = FabricRegistryAccess.get(level);
        var providerFabrics = registry.fabricsFor(providerId.orElseThrow());
        return registry.fabricsFor(consumerId.orElseThrow()).stream().anyMatch(providerFabrics::contains);
    }

    void clear() {
        loadedGrids.clear();
    }
}
