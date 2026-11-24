package space.controlnet.ae2federation.identity;

import appeng.api.networking.IGrid;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Level-wide identity registry. Live claims are held in an {@link IdentityClaimIndex}, updated incrementally by each
 * Grid's identity service as nodes join and leave. Nothing is saved: each node keeps its own lineage in the Grid's save,
 * and settlement is recomputed from the live claims. The SavedData only gives the server one shared instance.
 */
public final class NetworkIdentityRegistry extends SavedData {
    private static final String DATA_NAME = "ae2federation_network_identities";
    private static final Factory<NetworkIdentityRegistry> FACTORY =
            new Factory<>(NetworkIdentityRegistry::new, NetworkIdentityRegistry::load);

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
        return claims.settle(grid, result -> { });
    }

    /** Read-only view of each live Grid's published lineages, for diagnostics. */
    public Map<IGrid, Set<NodeLineage>> liveClaims() {
        return claims.liveClaims();
    }

    /** Each live Grid, without its lineages. */
    public java.util.List<IGrid> liveGrids() {
        return claims.liveGrids();
    }

    public IdentityClaimIndex<IGrid> claimIndex() {
        return claims;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return tag;
    }

    private static NetworkIdentityRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        return new NetworkIdentityRegistry();
    }
}
