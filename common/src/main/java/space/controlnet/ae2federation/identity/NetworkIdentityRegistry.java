package space.controlnet.ae2federation.identity;

import appeng.api.networking.IGrid;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class NetworkIdentityRegistry extends SavedData {
    private static final String DATA_NAME = "ae2federation_network_identities";
    private static final Factory<NetworkIdentityRegistry> FACTORY =
            new Factory<>(NetworkIdentityRegistry::new, NetworkIdentityRegistry::load);

    private final Map<NetworkId, IdentityStatus> settlements = new HashMap<>();
    private final Map<IGrid, Set<NodeLineage>> liveClaims = new IdentityHashMap<>();

    public static NetworkIdentityRegistry get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void observe(IGrid grid, Set<NodeLineage> lineages) {
        liveClaims.put(grid, Set.copyOf(lineages));
    }

    public void release(IGrid grid) {
        liveClaims.remove(grid);
    }

    public IdentitySettlement settle(IGrid grid, Set<NodeLineage> lineages) {
        var copied = liveClaims.entrySet().stream()
                .filter(entry -> entry.getKey() != grid)
                .flatMap(entry -> entry.getValue().stream())
                .anyMatch(other -> lineages.stream().anyMatch(current -> current.nodeId().equals(other.nodeId())));
        var split = liveClaims.entrySet().stream()
                .filter(entry -> entry.getKey() != grid)
                .flatMap(entry -> entry.getValue().stream())
                .anyMatch(other -> lineages.stream().anyMatch(current -> current.networkId().equals(other.networkId())));
        var result = IdentityReconciler.reconcile(lineages, copied, split, true);
        result.networkId().ifPresent(networkId -> settlements.put(networkId, result.status()));
        if (result.networkId().isPresent()) {
            setDirty();
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var entries = new ListTag();
        settlements.forEach((networkId, status) -> {
            var entry = new CompoundTag();
            entry.putUUID("network", networkId.value());
            entry.putString("status", status.name());
            entries.add(entry);
        });
        tag.put("settlements", entries);
        return tag;
    }

    private static NetworkIdentityRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        var registry = new NetworkIdentityRegistry();
        for (var rawEntry : tag.getList("settlements", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) rawEntry;
            if (entry.hasUUID("network")) {
                registry.settlements.put(new NetworkId(entry.getUUID("network")),
                        IdentityStatus.valueOf(entry.getString("status")));
            }
        }
        return registry;
    }
}
