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
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

public final class NetworkIdentityGridService implements NetworkIdentityService, IGridServiceProvider {
    private static final String DATA_KEY = "ae2federation_network_identity";
    private static final int SCHEMA_VERSION = 1;
    /**
     * The server tick in which each node without saved lineage was minted, kept across the Grids a node moves through.
     * AE2 readies a tick's new block entities chunk by chunk, in hash order, so nodes of one network can each start in
     * their own Grid before the neighbour joining them is readied. Until that tick ends they may still take one
     * NetworkId. Server thread only; weak keys, as nodes are never removed explicitly.
     */
    private static final Map<IGridNode, Integer> MINTED_TICK = new WeakHashMap<>();

    private final IGrid grid;
    private final NetworkId newGridId = NetworkId.create();
    private final Map<IGridNode, NodeLineage> nodes = new IdentityHashMap<>();
    private final Map<NodeLineage, Integer> lineageCounts = new HashMap<>();
    // Regular nodes per NetworkId, so adoption need not walk the whole Grid.
    private final Map<NetworkId, Integer> networkCounts = new HashMap<>();
    // Only nodes minted in the current native multipart initialization call may adopt another lineage.
    private final Set<IGridNode> provisional = Collections.newSetFromMap(new IdentityHashMap<>());
    // Nodes of this Grid minted in the tick freshTick; they may adopt another lineage until that tick ends.
    private final Set<IGridNode> fresh = Collections.newSetFromMap(new IdentityHashMap<>());
    private int freshTick;
    private @Nullable MinecraftServer server;
    private int duplicateLineages;
    private IdentitySettlement settlement = new IdentitySettlement(IdentityStatus.NEW_NETWORK, java.util.Optional.empty());
    private long nodeRevision;
    private @Nullable NetworkIdentityRegistry registry;

    public NetworkIdentityGridService(IGrid grid) {
        this.grid = grid;
    }

    @Override
    public void addNode(IGridNode gridNode, @Nullable CompoundTag savedData) {
        nodeRevision++;
        var lineage = read(savedData);
        var neutral = neutral(gridNode);
        var transientNode = !neutral && NativeIdentityInitialization.contains(gridNode);
        server = gridNode.getLevel().getServer();
        if (lineage == null) {
            lineage = new NodeLineage(establishedHint(), UUID.randomUUID(), 1);
            transientNode = !neutral && NativeIdentityInitialization.register(gridNode);
            if (!neutral) {
                MINTED_TICK.put(gridNode, server.getTickCount());
            }
            ((GridNode) gridNode).callListener(IGridNodeListener::onSaveChanges);
        } else if (neutral) {
            // Follow the network this boundary attaches to, so it can hand that id to a node that later joins through
            // it alone. No save callback: it fires inside Grid propagation, and the id is only a hint, never a claim.
            var attached = nodes.entrySet().stream().filter(entry -> !neutral(entry.getKey())).findFirst()
                    .map(entry -> entry.getValue().networkId());
            if (attached.isPresent() && !attached.get().equals(lineage.networkId())) {
                lineage = new NodeLineage(attached.get(), lineage.nodeId(), lineage.revision());
            }
        }
        // Saved provisional flags are legacy durable evidence. Only the original live node in this call scope
        // is transient; copied NBT never carries this authority, and node UUIDs are never regenerated on transfer.
        registry = NetworkIdentityRegistry.get(gridNode.getLevel());
        put(gridNode, lineage);
        if (transientNode) {
            provisional.add(gridNode);
        }
        if (!neutral && Integer.valueOf(server.getTickCount()).equals(MINTED_TICK.get(gridNode))) {
            freshNodes().add(gridNode);
        }
        adoptEstablishedLineage();
    }

    private void adoptEstablishedLineage() {
        if (provisional.isEmpty() && freshNodes().isEmpty()) {
            return;
        }
        var adopting = Collections.<IGridNode>newSetFromMap(new IdentityHashMap<>());
        adopting.addAll(provisional);
        adopting.addAll(freshNodes());
        var durable = durableNetworks(adopting);
        if (durable.size() > 1) {
            return;
        }
        var networkId = durable.isEmpty() ? adoptionTarget() : durable.getFirst();
        for (var node : adopting) {
            var current = nodes.get(node);
            if (!networkId.equals(current.networkId())) {
                put(node, new NodeLineage(networkId, current.nodeId(), current.revision()));
                ((GridNode) node).callListener(IGridNodeListener::onSaveChanges);
            }
        }
    }

    /** The NetworkIds held by regular nodes outside {@code adopting}: the identities those nodes must take. */
    private java.util.List<NetworkId> durableNetworks(Set<IGridNode> adopting) {
        var pending = new HashMap<NetworkId, Integer>();
        for (var node : adopting) {
            pending.merge(nodes.get(node).networkId(), 1, Integer::sum);
        }
        return networkCounts.entrySet().stream().filter(entry -> entry.getValue() > pending.getOrDefault(entry.getKey(), 0))
                .map(Map.Entry::getKey).toList();
    }

    /** This Grid's nodes minted in the current server tick; those of an earlier tick are final. */
    private Set<IGridNode> freshNodes() {
        if (server != null && freshTick != server.getTickCount()) {
            fresh.clear();
            freshTick = server.getTickCount();
        }
        return fresh;
    }

    /**
     * The id new nodes share when no established node holds one: the network a boundary here attaches to, else an id
     * one of them already carries, else this Grid's fresh id.
     */
    private NetworkId adoptionTarget() {
        for (var entry : nodes.entrySet()) {
            if (neutral(entry.getKey())) {
                return entry.getValue().networkId();
            }
        }
        return establishedHint();
    }

    /** A regular node's network id, else a boundary's attached-network hint, else this Grid's fresh id. */
    private NetworkId establishedHint() {
        NetworkId hint = null;
        for (var entry : nodes.entrySet()) {
            if (!neutral(entry.getKey())) {
                return entry.getValue().networkId();
            }
            if (hint == null) {
                hint = entry.getValue().networkId();
            }
        }
        return hint == null ? newGridId : hint;
    }

    /**
     * Boundary nodes (Router faces, Bridge sides) only attach to a native network. They keep a lineage for their node id
     * and persistence, but never publish it as a claim, so their own history can neither merge, split, nor copy-conflict
     * the network they attach to.
     */
    private static boolean neutral(IGridNode node) {
        return node.getOwner() instanceof IdentityNeutralNodeOwner;
    }

    void finishInitialization(IGridNode node) {
        if (provisional.remove(node)) {
            ((GridNode) node).callListener(IGridNodeListener::onSaveChanges);
        }
    }

    private void put(IGridNode gridNode, NodeLineage lineage) {
        var previous = nodes.put(gridNode, lineage);
        if (neutral(gridNode)) {
            // Boundary nodes are never identity evidence: no claim, so no merge, split, or copy conflict.
            return;
        }
        if (previous != null) {
            release(previous);
        }
        networkCounts.merge(lineage.networkId(), 1, Integer::sum);
        if (lineageCounts.merge(lineage, 1, Integer::sum) == 1) {
            registry.add(grid, lineage);
        } else {
            duplicateLineages++;
        }
    }

    @Override
    public void removeNode(IGridNode gridNode) {
        nodeRevision++;
        provisional.remove(gridNode);
        fresh.remove(gridNode);
        var lineage = nodes.remove(gridNode);
        registry = NetworkIdentityRegistry.get(gridNode.getLevel());
        if (nodes.isEmpty()) {
            lineageCounts.clear();
            networkCounts.clear();
            duplicateLineages = 0;
            registry.release(grid);
            settlement = IdentityReconciler.reconcile(nodes.values(), false, false, true);
        } else if (lineage != null && !neutral(gridNode)) {
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
    public long nodeRevision() {
        return nodeRevision;
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
        if (networkCounts.merge(lineage.networkId(), -1, Integer::sum) == 0) {
            networkCounts.remove(lineage.networkId());
        }
        if (lineageCounts.merge(lineage, -1, Integer::sum) == 0) {
            lineageCounts.remove(lineage);
            registry.remove(grid, lineage);
        } else {
            duplicateLineages--;
        }
    }
}
