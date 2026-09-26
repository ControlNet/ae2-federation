package space.controlnet.ae2federation.identity;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Index of the distinct lineages each live Grid publishes, keyed by node id and network id, with one cached settlement
 * per Grid. A Grid's settlement only looks up its own lineages. A claim change on one Grid clears the cached settlement
 * of that Grid and of every other Grid sharing a node id or network id with the changed lineage, so copied-node and
 * split conflicts that are observed through another Grid invalidate the unchanged Grid as well.
 *
 * <p>Grids are compared by identity. The index holds no Grid beyond {@link #release}: identity services release their
 * Grid when its last node leaves.
 */
public final class IdentityClaimIndex<G> {
    private final Map<G, GridClaims> liveClaims = new IdentityHashMap<>();
    private final Map<UUID, Map<G, Integer>> nodeIndex = new HashMap<>();
    private final Map<NetworkId, Map<G, Integer>> networkIndex = new HashMap<>();
    private long settlementComputations;
    private long lineageLookups;
    private long cachedReads;

    public void add(G grid, NodeLineage lineage) {
        var claims = liveClaims.computeIfAbsent(grid, ignored -> new GridClaims());
        if (!claims.lineages.add(lineage)) {
            return;
        }
        invalidateSharing(grid, lineage);
        nodeIndex.computeIfAbsent(lineage.nodeId(), ignored -> new IdentityHashMap<>()).merge(grid, 1, Integer::sum);
        networkIndex.computeIfAbsent(lineage.networkId(), ignored -> new IdentityHashMap<>())
                .merge(grid, 1, Integer::sum);
    }

    public void remove(G grid, NodeLineage lineage) {
        var claims = liveClaims.get(grid);
        if (claims == null || !claims.lineages.remove(lineage)) {
            return;
        }
        unindex(grid, lineage);
        invalidateSharing(grid, lineage);
        if (claims.lineages.isEmpty()) {
            liveClaims.remove(grid);
        }
    }

    public void release(G grid) {
        var claims = liveClaims.remove(grid);
        if (claims == null) {
            return;
        }
        for (var lineage : claims.lineages) {
            unindex(grid, lineage);
            invalidateSharing(grid, lineage);
        }
    }

    /**
     * Returns the cached settlement, computing it only after a relevant claim change. {@code onComputed} observes
     * fresh results so the caller can persist a changed status; cached reads never invoke it.
     */
    public IdentitySettlement settle(G grid, Consumer<IdentitySettlement> onComputed) {
        var claims = liveClaims.get(grid);
        if (claims == null) {
            return IdentityReconciler.reconcile(Set.of(), false, false, true);
        }
        if (claims.settlement != null) {
            cachedReads++;
            return claims.settlement;
        }
        settlementComputations++;
        var copied = false;
        var split = false;
        for (var lineage : claims.lineages) {
            lineageLookups++;
            copied |= sharedWithOtherGrid(nodeIndex.get(lineage.nodeId()), grid);
            split |= sharedWithOtherGrid(networkIndex.get(lineage.networkId()), grid);
        }
        var result = IdentityReconciler.reconcile(claims.lineages, copied, split, true);
        claims.settlement = result;
        onComputed.accept(result);
        return result;
    }

    public Map<G, Set<NodeLineage>> liveClaims() {
        var snapshot = new IdentityHashMap<G, Set<NodeLineage>>();
        liveClaims.forEach((grid, claims) -> snapshot.put(grid, Set.copyOf(claims.lineages)));
        return Collections.unmodifiableMap(snapshot);
    }

    public int indexedNodeIds() {
        return nodeIndex.size();
    }

    public int indexedNetworkIds() {
        return networkIndex.size();
    }

    public long settlementComputations() {
        return settlementComputations;
    }

    public long lineageLookups() {
        return lineageLookups;
    }

    public long cachedReads() {
        return cachedReads;
    }

    private void unindex(G grid, NodeLineage lineage) {
        decrement(nodeIndex, lineage.nodeId(), grid);
        decrement(networkIndex, lineage.networkId(), grid);
    }

    private void invalidateSharing(G changed, NodeLineage lineage) {
        invalidateOthers(nodeIndex.get(lineage.nodeId()), changed);
        invalidateOthers(networkIndex.get(lineage.networkId()), changed);
        var own = liveClaims.get(changed);
        if (own != null) {
            own.settlement = null;
        }
    }

    private void invalidateOthers(Map<G, Integer> grids, G changed) {
        if (grids == null) {
            return;
        }
        for (var grid : grids.keySet()) {
            if (grid != changed) {
                var claims = liveClaims.get(grid);
                if (claims != null) {
                    claims.settlement = null;
                }
            }
        }
    }

    private boolean sharedWithOtherGrid(Map<G, Integer> grids, G grid) {
        return grids != null && (grids.size() > 1 || !grids.containsKey(grid));
    }

    private <K> void decrement(Map<K, Map<G, Integer>> index, K key, G grid) {
        var grids = index.get(key);
        if (grids == null) {
            return;
        }
        grids.computeIfPresent(grid, (ignored, count) -> count == 1 ? null : count - 1);
        if (grids.isEmpty()) {
            index.remove(key);
        }
    }

    private final class GridClaims {
        private final Set<NodeLineage> lineages = new HashSet<>();
        private IdentitySettlement settlement;
    }
}
