package space.controlnet.ae2federation.crafting.binding;

import appeng.api.networking.IGrid;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;

final class CraftingFabricObserver {
    private final ServerLevel level;
    private final Map<NetworkId, IGrid> loadedGrids = new HashMap<>();

    CraftingFabricObserver(ServerLevel level) {
        this.level = level;
    }

    void register(IGrid grid) {
        FabricRegistryAccess.confirmedNetworkId(grid).ifPresent(networkId -> loadedGrids.put(networkId, grid));
    }

    void register(Iterable<IGrid> grids) {
        grids.forEach(this::register);
    }

    Map<PolicyKey, CraftingRelationship> relationships() {
        discardStaleGrids();
        var result = new HashMap<PolicyKey, CraftingRelationship>();
        for (var fabric : FabricRegistryAccess.get(level).snapshot().fabrics().values()) {
            var members = fabric.memberships().keySet().stream().map(loadedGrids::get)
                    .filter(java.util.Objects::nonNull).toList();
            for (var consumer : members) {
                for (var provider : members) {
                    if (consumer != provider) {
                        var key = new PolicyKey(FabricRegistryAccess.confirmedNetworkId(consumer).orElseThrow(),
                                FabricRegistryAccess.confirmedNetworkId(provider).orElseThrow(),
                                PolicyCapability.CRAFTING);
                        result.put(key, new CraftingRelationship(key, consumer, provider));
                    }
                }
            }
        }
        return Map.copyOf(result);
    }

    Set<FabricReference> references(CraftingRelationship relationship) {
        var registry = FabricRegistryAccess.get(level);
        var consumer = registry.fabricsFor(relationship.key().consumerNetworkId());
        var provider = registry.fabricsFor(relationship.key().providerNetworkId());
        return consumer.stream().filter(provider::contains).map(registry::fabric)
                .flatMap(java.util.Optional::stream).map(snapshot -> snapshot.reference())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    long topologyRevision() {
        return FabricRegistryAccess.get(level).snapshot().topologyRevision();
    }

    void clear() {
        loadedGrids.clear();
    }

    private void discardStaleGrids() {
        loadedGrids.entrySet().removeIf(entry -> FabricRegistryAccess.confirmedNetworkId(entry.getValue())
                .filter(entry.getKey()::equals).isEmpty());
    }
}
