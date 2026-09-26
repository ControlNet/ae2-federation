package space.controlnet.ae2federation.identity;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IGridServiceProvider;
import appeng.me.GridNode;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

public final class NetworkIdentityGridService implements NetworkIdentityService, IGridServiceProvider {
    private static final String DATA_KEY = "ae2federation_network_identity";
    private static final int SCHEMA_VERSION = 1;

    private final IGrid grid;
    private final NetworkId newGridId = NetworkId.create();
    private final Map<IGridNode, NodeLineage> nodes = new IdentityHashMap<>();
    private final Map<NodeLineage, Integer> lineageCounts = new HashMap<>();
    /**
     * Nodes whose lineage was minted while they were alone in this Grid. Such a lineage is not a prior identity: AE2
     * readies a part on an existing cable in its own one-node Grid before connecting it, so the minted id must not
     * compete with the Grid the node joins. It becomes durable as soon as the node shares a Grid with another node.
     */
    private final Set<IGridNode> provisional = Collections.newSetFromMap(new IdentityHashMap<>());
    private IdentitySettlement settlement = new IdentitySettlement(IdentityStatus.NEW_NETWORK, java.util.Optional.empty());
    private @Nullable NetworkIdentityRegistry registry;

    public NetworkIdentityGridService(IGrid grid) {
        this.grid = grid;
    }

    @Override
    public void addNode(IGridNode gridNode, @Nullable CompoundTag savedData) {
        var lineage = read(savedData);
        var provisionalData = lineage != null && provisional(savedData);
        if (provisionalData && !nodes.isEmpty()) {
            // A lone node's minted id is no evidence against the Grid it joins; it adopts that Grid's id below.
            lineage = null;
        }
        var minted = lineage == null;
        if (minted) {
            var existingId = nodes.values().stream().findFirst().map(NodeLineage::networkId).orElse(newGridId);
            lineage = new NodeLineage(existingId, UUID.randomUUID(), 1);
            ((GridNode) gridNode).callListener(IGridNodeListener::onSaveChanges);
        }
        registry = NetworkIdentityRegistry.get(gridNode.getLevel());
        put(gridNode, lineage);
        if (nodes.size() == 1 && (minted || provisionalData)) {
            provisional.add(gridNode);
        } else if (nodes.size() > 1 && !provisional.isEmpty()) {
            settleProvisional();
        }
    }

    /**
     * The Grid now has several nodes: provisional lineages adopt the id of a durable lineage when one is present (a
     * lone node's Grid absorbed an established one) and all lineages are durable from now on.
     */
    private void settleProvisional() {
        var durable = nodes.entrySet().stream().filter(entry -> !provisional.contains(entry.getKey()))
                .map(entry -> entry.getValue().networkId()).findFirst();
        for (var node : provisional) {
            var current = nodes.get(node);
            if (durable.isPresent() && !durable.get().equals(current.networkId())) {
                put(node, new NodeLineage(durable.get(), current.nodeId(), current.revision()));
                ((GridNode) node).callListener(IGridNodeListener::onSaveChanges);
            }
        }
        provisional.clear();
    }

    private void put(IGridNode gridNode, NodeLineage lineage) {
        var previous = nodes.put(gridNode, lineage);
        if (previous != null) {
            release(previous);
        }
        if (lineageCounts.merge(lineage, 1, Integer::sum) == 1) {
            registry.add(grid, lineage);
        }
    }

    @Override
    public void removeNode(IGridNode gridNode) {
        provisional.remove(gridNode);
        var lineage = nodes.remove(gridNode);
        registry = NetworkIdentityRegistry.get(gridNode.getLevel());
        if (nodes.isEmpty()) {
            lineageCounts.clear();
            registry.release(grid);
            settlement = IdentityReconciler.reconcile(nodes.values(), false, false, true);
        } else if (lineage != null) {
            release(lineage);
        }
    }

    @Override
    public void saveNodeData(IGridNode gridNode, CompoundTag savedData) {
        var lineage = nodes.get(gridNode);
        if (lineage == null) {
            return;
        }
        var data = new CompoundTag();
        data.putInt("schema", SCHEMA_VERSION);
        data.putUUID("network", lineage.networkId().value());
        data.putUUID("node", lineage.nodeId());
        data.putLong("revision", lineage.revision());
        if (provisional.contains(gridNode)) {
            data.putBoolean("provisional", true);
        }
        savedData.put(DATA_KEY, data);
    }

    @Override
    public IdentitySettlement settlement() {
        if (registry != null && !nodes.isEmpty()) {
            settlement = registry.settle(grid);
        }
        return settlement;
    }

    @Override
    public NodeLineage lineage(IGridNode node) {
        var lineage = nodes.get(node);
        if (lineage == null) {
            throw new IllegalArgumentException("Node does not belong to this identity service");
        }
        return lineage;
    }

    private static @Nullable NodeLineage read(@Nullable CompoundTag savedData) {
        if (savedData == null || !(savedData.get(DATA_KEY) instanceof CompoundTag data)
                || data.getInt("schema") != SCHEMA_VERSION
                || !data.hasUUID("network") || !data.hasUUID("node") || data.getLong("revision") < 1) {
            return null;
        }
        return new NodeLineage(new NetworkId(data.getUUID("network")), data.getUUID("node"), data.getLong("revision"));
    }

    private static boolean provisional(@Nullable CompoundTag savedData) {
        return savedData != null && savedData.get(DATA_KEY) instanceof CompoundTag data
                && data.getBoolean("provisional");
    }

    private void release(NodeLineage lineage) {
        if (lineageCounts.merge(lineage, -1, Integer::sum) == 0) {
            lineageCounts.remove(lineage);
            registry.remove(grid, lineage);
        }
    }
}
