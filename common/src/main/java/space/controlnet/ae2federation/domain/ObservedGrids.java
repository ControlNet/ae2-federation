package space.controlnet.ae2federation.domain;

import appeng.api.networking.IGrid;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import space.controlnet.ae2federation.identity.NetworkId;

/**
 * The Grids a binding service has been told about, kept by identity. A Grid whose NetworkId is not confirmed for a
 * while (its identity split, then healed on the same Grids) stays observed and counts again once its id is confirmed,
 * without a Federation block having to report it again. Only a Grid that has emptied is forgotten.
 */
public final class ObservedGrids {
    private final Set<IGrid> grids = Collections.newSetFromMap(new IdentityHashMap<>());

    public void add(IGrid grid) {
        if (grid != null && !grid.isEmpty()) {
            grids.add(grid);
        }
    }

    /** Each confirmed NetworkId with the observed Grid that carries it now. */
    public Map<NetworkId, IGrid> confirmed() {
        grids.removeIf(IGrid::isEmpty);
        var result = new HashMap<NetworkId, IGrid>();
        for (var grid : grids) {
            FederationDomainRegistryAccess.confirmedNetworkId(grid).ifPresent(networkId -> result.put(networkId, grid));
        }
        return result;
    }

    public boolean contains(IGrid grid) {
        return grids.contains(grid);
    }

    public void clear() {
        grids.clear();
    }
}
