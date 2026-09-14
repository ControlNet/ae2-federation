package space.controlnet.ae2federation.fabric;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import space.controlnet.ae2federation.identity.NetworkId;

public final class FabricRegistry {
    private final FabricRecomputeBudget budget;
    private final Map<FabricNodeId, FabricNodeEvidence> nodes = new HashMap<>();
    private final Map<FabricNodeId, Set<FabricPortId>> incomingFederation = new HashMap<>();
    private final Map<FabricNodeId, FabricId> nodeToFabric = new HashMap<>();
    private final Map<FabricId, FabricSnapshot> fabrics = new HashMap<>();
    private final Map<NetworkId, Set<FabricId>> networkIndex = new HashMap<>();
    private final Map<FabricNodeId, FabricInvalidationReason> invalidations = new HashMap<>();
    private final Map<FabricSourceId, FabricId> directBridges = new HashMap<>();
    private long topologyRevision;

    public FabricRegistry(FabricRecomputeBudget budget) {
        this.budget = budget;
    }

    public void upsertDirectBridge(FabricSourceId source, NetworkId mainNetwork, NetworkId outerNetwork) {
        removeDirectBridge(source, FabricInvalidationReason.TOPOLOGY_CHANGED);
        if (mainNetwork.equals(outerNetwork)) {
            return;
        }
        var memberships = new LinkedHashMap<NetworkId, Set<FabricSourceId>>();
        memberships.computeIfAbsent(mainNetwork, ignored -> new TreeSet<>()).add(source.child("main"));
        memberships.computeIfAbsent(outerNetwork, ignored -> new TreeSet<>()).add(source.child("outer"));
        var fabricId = FabricId.direct(source);
        install(new FabricSnapshot(fabricId, ++topologyRevision, Set.of(), memberships));
        directBridges.put(source, fabricId);
    }

    public void invalidateDirectBridge(FabricSourceId source) {
        removeDirectBridge(source, FabricInvalidationReason.INVALID_BRIDGE);
    }

    public void upsertNode(FabricNodeEvidence evidence) {
        var previous = nodes.get(evidence.nodeId());
        if (evidence.equals(previous)) {
            return;
        }
        var seeds = affectedBy(evidence.nodeId(), previous, evidence);
        removeIncoming(previous);
        nodes.put(evidence.nodeId(), evidence);
        addIncoming(evidence);
        recompute(seeds, FabricInvalidationReason.TOPOLOGY_CHANGED);
    }

    public void invalidateNode(FabricNodeId nodeId, FabricInvalidationReason reason) {
        var previous = nodes.remove(nodeId);
        var seeds = affectedBy(nodeId, previous, null);
        removeIncoming(previous);
        recompute(seeds, reason);
        invalidations.put(nodeId, reason);
    }

    public void removeNode(FabricNodeId nodeId) {
        invalidateNode(nodeId, FabricInvalidationReason.SOURCE_UNLOADED);
    }

    public Set<FabricId> fabricsFor(NetworkId networkId) {
        return Set.copyOf(networkIndex.getOrDefault(networkId, Set.of()));
    }

    public boolean isCurrent(FabricReference reference) {
        var current = fabrics.get(reference.fabricId());
        return current != null && current.generation() == reference.generation();
    }

    public Optional<FabricSnapshot> fabric(FabricId fabricId) {
        return Optional.ofNullable(fabrics.get(fabricId));
    }

    public FabricRegistrySnapshot snapshot() {
        var copiedIndex = new HashMap<NetworkId, Set<FabricId>>();
        networkIndex.forEach((network, indexedFabrics) -> copiedIndex.put(network, Set.copyOf(indexedFabrics)));
        return new FabricRegistrySnapshot(fabrics, copiedIndex, invalidations, topologyRevision);
    }

    private Set<FabricNodeId> affectedBy(FabricNodeId nodeId, FabricNodeEvidence previous,
            FabricNodeEvidence replacement) {
        var affected = new TreeSet<FabricNodeId>();
        affected.add(nodeId);
        addFederationPeers(affected, previous);
        addFederationPeers(affected, replacement);
        incomingFederation.getOrDefault(nodeId, Set.of()).forEach(port -> affected.add(port.node()));
        return affected;
    }

    private void recompute(Set<FabricNodeId> initial, FabricInvalidationReason reason) {
        topologyRevision++;
        var affected = invalidateComponents(initial, reason);
        var visited = new HashSet<FabricNodeId>();
        var counters = new int[2];
        for (var seed : new TreeSet<>(affected)) {
            if (!nodes.containsKey(seed) || visited.contains(seed)) {
                continue;
            }
            var component = collectComponent(seed, visited, counters);
            if (component.budgetExhausted()) {
                affected.forEach(node -> invalidations.put(node, FabricInvalidationReason.BUDGET_EXHAUSTED));
                component.nodes().forEach(node -> invalidations.put(node, FabricInvalidationReason.BUDGET_EXHAUSTED));
                return;
            }
            if (component.reason() != null) {
                component.nodes().forEach(node -> invalidations.put(node, component.reason()));
                continue;
            }
            installPhysical(component.nodes());
        }
    }

    private Set<FabricNodeId> invalidateComponents(Set<FabricNodeId> initial, FabricInvalidationReason reason) {
        var affected = new TreeSet<>(initial);
        var oldFabrics = new TreeSet<FabricId>();
        initial.stream().map(nodeToFabric::get).filter(java.util.Objects::nonNull).forEach(oldFabrics::add);
        for (var fabricId : oldFabrics) {
            var snapshot = removeFabric(fabricId);
            if (snapshot != null) {
                affected.addAll(snapshot.nodes());
                snapshot.nodes().forEach(node -> invalidations.put(node, reason));
            }
        }
        return affected;
    }

    private ComponentResult collectComponent(FabricNodeId seed, Set<FabricNodeId> visited, int[] counters) {
        var queue = new ArrayDeque<FabricNodeId>();
        var component = new TreeSet<FabricNodeId>();
        queue.add(seed);
        FabricInvalidationReason reason = null;
        while (!queue.isEmpty()) {
            var current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (++counters[0] > budget.maxNodeVisits()) {
                return new ComponentResult(component, FabricInvalidationReason.BUDGET_EXHAUSTED, true);
            }
            component.add(current);
            var evidence = nodes.get(current);
            for (var entry : evidence.ports().entrySet()) {
                if (++counters[1] > budget.maxPortVisits()) {
                    return new ComponentResult(component, FabricInvalidationReason.BUDGET_EXHAUSTED, true);
                }
                if (entry.getValue() instanceof FabricPortEvidence.Unsettled) {
                    reason = FabricInvalidationReason.IDENTITY_UNSETTLED;
                } else if (entry.getValue() instanceof FabricPortEvidence.Federation federation) {
                    var peer = nodes.get(federation.peer().node());
                    var reciprocal = new FabricPortId(current, entry.getKey());
                    if (peer == null || !(peer.port(federation.peer().port()) instanceof FabricPortEvidence.Federation back)
                            || !back.peer().equals(reciprocal)) {
                        reason = FabricInvalidationReason.NON_RECIPROCAL_EDGE;
                    } else {
                        queue.addLast(federation.peer().node());
                    }
                }
            }
            for (var source : incomingFederation.getOrDefault(current, Set.of())) {
                var sourceEvidence = nodes.get(source.node());
                var outgoing = sourceEvidence == null ? null : sourceEvidence.port(source.port());
                if (!(outgoing instanceof FabricPortEvidence.Federation target)
                        || !target.peer().node().equals(current)
                        || !(evidence.port(target.peer().port()) instanceof FabricPortEvidence.Federation back)
                        || !back.peer().equals(source)) {
                    reason = FabricInvalidationReason.NON_RECIPROCAL_EDGE;
                }
            }
        }
        return new ComponentResult(component, reason, false);
    }

    private void installPhysical(Set<FabricNodeId> component) {
        var memberships = new LinkedHashMap<NetworkId, Set<FabricSourceId>>();
        for (var nodeId : component) {
            for (var port : nodes.get(nodeId).ports().values()) {
                if (port instanceof FabricPortEvidence.Native nativeEvidence) {
                    memberships.computeIfAbsent(nativeEvidence.networkId(), ignored -> new TreeSet<>())
                            .add(nativeEvidence.source());
                }
            }
        }
        var fabricId = FabricId.physical(component.iterator().next());
        install(new FabricSnapshot(fabricId, ++topologyRevision, component, memberships));
        component.forEach(node -> {
            nodeToFabric.put(node, fabricId);
            invalidations.remove(node);
        });
    }

    private void install(FabricSnapshot snapshot) {
        fabrics.put(snapshot.fabricId(), snapshot);
        snapshot.memberships().keySet().forEach(network -> networkIndex
                .computeIfAbsent(network, ignored -> new TreeSet<>()).add(snapshot.fabricId()));
    }

    private FabricSnapshot removeFabric(FabricId fabricId) {
        var removed = fabrics.remove(fabricId);
        if (removed == null) {
            return null;
        }
        removed.nodes().forEach(nodeToFabric::remove);
        removed.memberships().keySet().forEach(network -> {
            var indexed = networkIndex.get(network);
            indexed.remove(fabricId);
            if (indexed.isEmpty()) {
                networkIndex.remove(network);
            }
        });
        return removed;
    }

    private void removeDirectBridge(FabricSourceId source, FabricInvalidationReason reason) {
        var fabricId = directBridges.remove(source);
        if (fabricId != null) {
            removeFabric(fabricId);
            topologyRevision++;
        }
    }

    private void addIncoming(FabricNodeEvidence evidence) {
        evidence.ports().forEach((port, state) -> {
            if (state instanceof FabricPortEvidence.Federation federation) {
                incomingFederation.computeIfAbsent(federation.peer().node(), ignored -> new TreeSet<>())
                        .add(new FabricPortId(evidence.nodeId(), port));
            }
        });
    }

    private void removeIncoming(FabricNodeEvidence evidence) {
        if (evidence == null) {
            return;
        }
        evidence.ports().forEach((port, state) -> {
            if (state instanceof FabricPortEvidence.Federation federation) {
                var incoming = incomingFederation.get(federation.peer().node());
                incoming.remove(new FabricPortId(evidence.nodeId(), port));
                if (incoming.isEmpty()) {
                    incomingFederation.remove(federation.peer().node());
                }
            }
        });
    }

    private static void addFederationPeers(Set<FabricNodeId> affected, FabricNodeEvidence evidence) {
        if (evidence != null) {
            evidence.ports().values().stream()
                    .filter(FabricPortEvidence.Federation.class::isInstance)
                    .map(FabricPortEvidence.Federation.class::cast)
                    .map(link -> link.peer().node())
                    .forEach(affected::add);
        }
    }

    private record ComponentResult(Set<FabricNodeId> nodes, FabricInvalidationReason reason,
            boolean budgetExhausted) {
    }
}
