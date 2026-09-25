package space.controlnet.ae2federation.domain;

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

public final class FederationDomainRegistry {
    private final FederationDomainRecomputeBudget budget;
    private final Map<FederationDomainNodeId, FederationDomainNodeEvidence> nodes = new HashMap<>();
    private final Map<FederationDomainNodeId, Set<FederationDomainPortId>> incomingFederation = new HashMap<>();
    private final Map<FederationDomainNodeId, FederationDomainId> nodeToFederationDomain = new HashMap<>();
    private final Map<FederationDomainId, FederationDomainSnapshot> federationDomains = new HashMap<>();
    private final Map<NetworkId, Set<FederationDomainId>> networkIndex = new HashMap<>();
    private final Map<FederationDomainNodeId, FederationDomainInvalidationReason> invalidations = new HashMap<>();
    private final Map<FederationDomainSourceId, FederationDomainId> directBridges = new HashMap<>();
    private long topologyRevision;

    public FederationDomainRegistry(FederationDomainRecomputeBudget budget) {
        this.budget = budget;
    }

    public void upsertDirectBridge(FederationDomainSourceId source, NetworkId mainNetwork, NetworkId outerNetwork) {
        removeDirectBridge(source, FederationDomainInvalidationReason.TOPOLOGY_CHANGED);
        if (mainNetwork.equals(outerNetwork)) {
            return;
        }
        var memberships = new LinkedHashMap<NetworkId, Set<FederationDomainSourceId>>();
        memberships.computeIfAbsent(mainNetwork, ignored -> new TreeSet<>()).add(source.child("main"));
        memberships.computeIfAbsent(outerNetwork, ignored -> new TreeSet<>()).add(source.child("outer"));
        var federationDomainId = FederationDomainId.direct(source);
        install(new FederationDomainSnapshot(federationDomainId, ++topologyRevision, Set.of(), memberships));
        directBridges.put(source, federationDomainId);
    }

    public void invalidateDirectBridge(FederationDomainSourceId source) {
        removeDirectBridge(source, FederationDomainInvalidationReason.INVALID_BRIDGE);
    }

    public void upsertNode(FederationDomainNodeEvidence evidence) {
        var previous = nodes.get(evidence.nodeId());
        if (evidence.equals(previous)) {
            return;
        }
        var seeds = affectedBy(evidence.nodeId(), previous, evidence);
        removeIncoming(previous);
        nodes.put(evidence.nodeId(), evidence);
        addIncoming(evidence);
        recompute(seeds, FederationDomainInvalidationReason.TOPOLOGY_CHANGED);
    }

    public void invalidateNode(FederationDomainNodeId nodeId, FederationDomainInvalidationReason reason) {
        var previous = nodes.remove(nodeId);
        var seeds = affectedBy(nodeId, previous, null);
        removeIncoming(previous);
        recompute(seeds, reason);
        invalidations.put(nodeId, reason);
    }

    public void removeNode(FederationDomainNodeId nodeId) {
        invalidateNode(nodeId, FederationDomainInvalidationReason.SOURCE_UNLOADED);
    }

    public Set<FederationDomainId> federationdomainsFor(NetworkId networkId) {
        return Set.copyOf(networkIndex.getOrDefault(networkId, Set.of()));
    }

    public boolean isCurrent(FederationDomainReference reference) {
        var current = federationDomains.get(reference.federationDomainId());
        return current != null && current.generation() == reference.generation();
    }

    public Optional<FederationDomainSnapshot> federationDomain(FederationDomainId federationDomainId) {
        return Optional.ofNullable(federationDomains.get(federationDomainId));
    }

    public FederationDomainRegistrySnapshot snapshot() {
        var copiedIndex = new HashMap<NetworkId, Set<FederationDomainId>>();
        networkIndex.forEach((network, indexedFederationDomains) -> copiedIndex.put(network, Set.copyOf(indexedFederationDomains)));
        return new FederationDomainRegistrySnapshot(federationDomains, copiedIndex, invalidations, topologyRevision);
    }

    private Set<FederationDomainNodeId> affectedBy(FederationDomainNodeId nodeId, FederationDomainNodeEvidence previous,
            FederationDomainNodeEvidence replacement) {
        var affected = new TreeSet<FederationDomainNodeId>();
        affected.add(nodeId);
        addFederationPeers(affected, previous);
        addFederationPeers(affected, replacement);
        incomingFederation.getOrDefault(nodeId, Set.of()).forEach(port -> affected.add(port.node()));
        return affected;
    }

    private void recompute(Set<FederationDomainNodeId> initial, FederationDomainInvalidationReason reason) {
        topologyRevision++;
        var affected = invalidateComponents(initial, reason);
        var visited = new HashSet<FederationDomainNodeId>();
        var counters = new int[2];
        for (var seed : new TreeSet<>(affected)) {
            if (!nodes.containsKey(seed) || visited.contains(seed)) {
                continue;
            }
            var component = collectComponent(seed, visited, counters);
            if (component.budgetExhausted()) {
                affected.forEach(node -> invalidations.put(node, FederationDomainInvalidationReason.BUDGET_EXHAUSTED));
                component.nodes().forEach(node -> invalidations.put(node, FederationDomainInvalidationReason.BUDGET_EXHAUSTED));
                return;
            }
            if (component.reason() != null) {
                component.nodes().forEach(node -> invalidations.put(node, component.reason()));
                continue;
            }
            installPhysical(component.nodes());
        }
    }

    private Set<FederationDomainNodeId> invalidateComponents(Set<FederationDomainNodeId> initial, FederationDomainInvalidationReason reason) {
        var affected = new TreeSet<>(initial);
        var oldFederationDomains = new TreeSet<FederationDomainId>();
        initial.stream().map(nodeToFederationDomain::get).filter(java.util.Objects::nonNull).forEach(oldFederationDomains::add);
        for (var federationDomainId : oldFederationDomains) {
            var snapshot = removeFederationDomain(federationDomainId);
            if (snapshot != null) {
                affected.addAll(snapshot.nodes());
                snapshot.nodes().forEach(node -> invalidations.put(node, reason));
            }
        }
        return affected;
    }

    private ComponentResult collectComponent(FederationDomainNodeId seed, Set<FederationDomainNodeId> visited, int[] counters) {
        var queue = new ArrayDeque<FederationDomainNodeId>();
        var component = new TreeSet<FederationDomainNodeId>();
        queue.add(seed);
        FederationDomainInvalidationReason reason = null;
        while (!queue.isEmpty()) {
            var current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (++counters[0] > budget.maxNodeVisits()) {
                return new ComponentResult(component, FederationDomainInvalidationReason.BUDGET_EXHAUSTED, true);
            }
            component.add(current);
            var evidence = nodes.get(current);
            for (var entry : evidence.ports().entrySet()) {
                if (++counters[1] > budget.maxPortVisits()) {
                    return new ComponentResult(component, FederationDomainInvalidationReason.BUDGET_EXHAUSTED, true);
                }
                if (entry.getValue() instanceof FederationDomainPortEvidence.Unsettled) {
                    reason = FederationDomainInvalidationReason.IDENTITY_UNSETTLED;
                } else if (entry.getValue() instanceof FederationDomainPortEvidence.Federation federation) {
                    var peer = nodes.get(federation.peer().node());
                    var reciprocal = new FederationDomainPortId(current, entry.getKey());
                    if (peer == null || !(peer.port(federation.peer().port()) instanceof FederationDomainPortEvidence.Federation back)
                            || !back.peer().equals(reciprocal)) {
                        reason = FederationDomainInvalidationReason.NON_RECIPROCAL_EDGE;
                    } else {
                        queue.addLast(federation.peer().node());
                    }
                }
            }
            for (var source : incomingFederation.getOrDefault(current, Set.of())) {
                var sourceEvidence = nodes.get(source.node());
                var outgoing = sourceEvidence == null ? null : sourceEvidence.port(source.port());
                if (!(outgoing instanceof FederationDomainPortEvidence.Federation target)
                        || !target.peer().node().equals(current)
                        || !(evidence.port(target.peer().port()) instanceof FederationDomainPortEvidence.Federation back)
                        || !back.peer().equals(source)) {
                    reason = FederationDomainInvalidationReason.NON_RECIPROCAL_EDGE;
                }
            }
        }
        return new ComponentResult(component, reason, false);
    }

    private void installPhysical(Set<FederationDomainNodeId> component) {
        var memberships = new LinkedHashMap<NetworkId, Set<FederationDomainSourceId>>();
        for (var nodeId : component) {
            for (var port : nodes.get(nodeId).ports().values()) {
                if (port instanceof FederationDomainPortEvidence.Native nativeEvidence) {
                    memberships.computeIfAbsent(nativeEvidence.networkId(), ignored -> new TreeSet<>())
                            .add(nativeEvidence.source());
                }
            }
        }
        var federationDomainId = FederationDomainId.physical(component.iterator().next());
        install(new FederationDomainSnapshot(federationDomainId, ++topologyRevision, component, memberships));
        component.forEach(node -> {
            nodeToFederationDomain.put(node, federationDomainId);
            invalidations.remove(node);
        });
    }

    private void install(FederationDomainSnapshot snapshot) {
        federationDomains.put(snapshot.federationDomainId(), snapshot);
        snapshot.memberships().keySet().forEach(network -> networkIndex
                .computeIfAbsent(network, ignored -> new TreeSet<>()).add(snapshot.federationDomainId()));
    }

    private FederationDomainSnapshot removeFederationDomain(FederationDomainId federationDomainId) {
        var removed = federationDomains.remove(federationDomainId);
        if (removed == null) {
            return null;
        }
        removed.nodes().forEach(nodeToFederationDomain::remove);
        removed.memberships().keySet().forEach(network -> {
            var indexed = networkIndex.get(network);
            indexed.remove(federationDomainId);
            if (indexed.isEmpty()) {
                networkIndex.remove(network);
            }
        });
        return removed;
    }

    private void removeDirectBridge(FederationDomainSourceId source, FederationDomainInvalidationReason reason) {
        var federationDomainId = directBridges.remove(source);
        if (federationDomainId != null) {
            removeFederationDomain(federationDomainId);
            topologyRevision++;
        }
    }

    private void addIncoming(FederationDomainNodeEvidence evidence) {
        evidence.ports().forEach((port, state) -> {
            if (state instanceof FederationDomainPortEvidence.Federation federation) {
                incomingFederation.computeIfAbsent(federation.peer().node(), ignored -> new TreeSet<>())
                        .add(new FederationDomainPortId(evidence.nodeId(), port));
            }
        });
    }

    private void removeIncoming(FederationDomainNodeEvidence evidence) {
        if (evidence == null) {
            return;
        }
        evidence.ports().forEach((port, state) -> {
            if (state instanceof FederationDomainPortEvidence.Federation federation) {
                var incoming = incomingFederation.get(federation.peer().node());
                incoming.remove(new FederationDomainPortId(evidence.nodeId(), port));
                if (incoming.isEmpty()) {
                    incomingFederation.remove(federation.peer().node());
                }
            }
        });
    }

    private static void addFederationPeers(Set<FederationDomainNodeId> affected, FederationDomainNodeEvidence evidence) {
        if (evidence != null) {
            evidence.ports().values().stream()
                    .filter(FederationDomainPortEvidence.Federation.class::isInstance)
                    .map(FederationDomainPortEvidence.Federation.class::cast)
                    .map(link -> link.peer().node())
                    .forEach(affected::add);
        }
    }

    private record ComponentResult(Set<FederationDomainNodeId> nodes, FederationDomainInvalidationReason reason,
            boolean budgetExhausted) {
    }
}
