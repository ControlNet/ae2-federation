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
    // Only nodes minted in the current native multipart initialization call may adopt another lineage.
    private final Set<IGridNode> provisional = Collections.newSetFromMap(new IdentityHashMap<>());
    private int duplicateLineages;
    private IdentitySettlement settlement = new IdentitySettlement(IdentityStatus.NEW_NETWORK, java.util.Optional.empty());
    private @Nullable NetworkIdentityRegistry registry;

    public NetworkIdentityGridService(IGrid grid) {
        this.grid = grid;
    }

    @Override
    public void addNode(IGridNode gridNode, @Nullable CompoundTag savedData) {
        var lineage = read(savedData);
        var transientNode = NativeIdentityInitialization.contains(gridNode);
        if (lineage == null) {
            var existingId = nodes.values().stream().findFirst().map(NodeLineage::networkId).orElse(newGridId);
            lineage = new NodeLineage(existingId, UUID.randomUUID(), 1);
            transientNode = NativeIdentityInitialization.register(gridNode);
            ((GridNode) gridNode).callListener(IGridNodeListener::onSaveChanges);
        }
        // Saved provisional flags are legacy durable evidence. Only the original live node in this call scope
        // is transient; copied NBT never carries this authority, and node UUIDs are never regenerated on transfer.
        registry = NetworkIdentityRegistry.get(gridNode.getLevel());
        put(gridNode, lineage);
        if (transientNode) {
            provisional.add(gridNode);
        }
        adoptEstablishedLineage();
    }

    private void adoptEstablishedLineage() {
        if (provisional.isEmpty()) {
            return;
        }
        var durable = nodes.entrySet().stream().filter(entry -> !provisional.contains(entry.getKey()))
                .map(entry -> entry.getValue().networkId()).distinct().toList();
        if (durable.size() > 1) {
            return;
        }
        var networkId = durable.isEmpty() ? newGridId : durable.getFirst();
        for (var node : provisional) {
            var current = nodes.get(node);
            if (!networkId.equals(current.networkId())) {
                put(node, new NodeLineage(networkId, current.nodeId(), current.revision()));
                ((GridNode) node).callListener(IGridNodeListener::onSaveChanges);
            }
        }
    }

    void finishInitialization(IGridNode node) {
        if (provisional.remove(node)) {
            ((GridNode) node).callListener(IGridNodeListener::onSaveChanges);
        }
    }

    private void put(IGridNode gridNode, NodeLineage lineage) {
        var previous = nodes.put(gridNode, lineage);
        if (previous != null) {
            release(previous);
        }
        if (lineageCounts.merge(lineage, 1, Integer::sum) == 1) {
            registry.add(grid, lineage);
        } else {
            duplicateLineages++;
        }
    }

    @Override
    public void removeNode(IGridNode gridNode) {
        provisional.remove(gridNode);
        var lineage = nodes.remove(gridNode);
        registry = NetworkIdentityRegistry.get(gridNode.getLevel());
        if (nodes.isEmpty()) {
            lineageCounts.clear();
            duplicateLineages = 0;
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
        savedData.put(DATA_KEY, data);
    }

    @Override
    public IdentitySettlement settlement() {
        if (!provisional.isEmpty()) {
            return new IdentitySettlement(IdentityStatus.PARTIAL_LOAD, java.util.Optional.empty());
        }
        if (duplicateLineages > 0) {
            return new IdentitySettlement(IdentityStatus.CONFLICTING_NODE_DATA, java.util.Optional.empty());
        }
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

    private void release(NodeLineage lineage) {
        if (lineageCounts.merge(lineage, -1, Integer::sum) == 0) {
            lineageCounts.remove(lineage);
            registry.remove(grid, lineage);
        } else {
            duplicateLineages--;
        }
    }
}
