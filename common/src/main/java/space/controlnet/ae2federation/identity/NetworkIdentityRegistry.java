package space.controlnet.ae2federation.identity;

import appeng.api.networking.IGrid;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Level-wide identity registry. Live claims are held in an {@link IdentityClaimIndex}, updated incrementally by each
 * Grid's identity service as nodes join and leave. Only the settled status per network is persisted, and the data is
 * marked dirty only when that status changes.
 */
public final class NetworkIdentityRegistry extends SavedData {
    private static final String DATA_NAME = "ae2federation_network_identities";
    private static final Factory<NetworkIdentityRegistry> FACTORY =
            new Factory<>(NetworkIdentityRegistry::new, NetworkIdentityRegistry::load);

    private final Map<NetworkId, IdentityStatus> settlements = new HashMap<>();
    private final IdentityClaimIndex<IGrid> claims = new IdentityClaimIndex<>();

    public static NetworkIdentityRegistry get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void add(IGrid grid, NodeLineage lineage) {
        claims.add(grid, lineage);
    }

    public void remove(IGrid grid, NodeLineage lineage) {
        claims.remove(grid, lineage);
    }

    public void release(IGrid grid) {
        claims.release(grid);
    }

    public IdentitySettlement settle(IGrid grid) {
        return claims.settle(grid, result -> result.networkId().ifPresent(networkId -> {
            if (settlements.put(networkId, result.status()) != result.status()) {
                setDirty();
            }
        }));
    }

    /** Read-only view of each live Grid's published lineages, for diagnostics. */
    public Map<IGrid, Set<NodeLineage>> liveClaims() {
        return claims.liveClaims();
    }

    public IdentityClaimIndex<IGrid> claimIndex() {
        return claims;
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
