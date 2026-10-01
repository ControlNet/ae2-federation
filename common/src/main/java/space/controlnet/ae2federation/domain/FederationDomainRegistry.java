package space.controlnet.ae2federation.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import space.controlnet.ae2federation.identity.NetworkId;

/**
 * Physical Federation Domains follow AE2's Grid model. A link exists only while both ports name each other; a
 * declaration the other side does not return yet is pending, links nothing and leaves the rest of the domain alone. A
 * change recomputes only the components it touches, and each recomputed component inherits the id of the old domain it
 * resembles most, so a domain keeps its id while nodes join, leave, merge or split off. Its generation advances only when
 * the set of member networks changes, which is what bindings and sessions hold on to: a cable or Endpoint joining or
 * leaving leaves every reference current, while a split that separates members makes them stale at once.
 *
 * <p>Node changes are applied to the evidence at once and their components are recomputed lazily, together, before the
 * next read: a chunk of cables publishing in one tick costs one walk of the domain, not one per cable. Every read sees
 * the same domains an immediate recomputation would give, up to which new id a merged component receives.
 */
public final class FederationDomainRegistry {
    private static final Comparator<Candidate> INHERITANCE_ORDER = Comparator.comparing(Candidate::membersEqual).reversed()
            .thenComparing(Comparator.comparingInt(Candidate::memberOverlap).reversed())
            .thenComparing(Comparator.comparingInt(Candidate::nodeOverlap).reversed())
            .thenComparingLong(Candidate::sequence)
            .thenComparingInt(Candidate::component);

    private final FederationDomainRecomputeBudget budget;
    private final Map<FederationDomainNodeId, FederationDomainNodeEvidence> nodes = new HashMap<>();
    private final Map<FederationDomainNodeId, Set<FederationDomainPortId>> incomingFederation = new HashMap<>();
    private final Map<FederationDomainNodeId, FederationDomainId> nodeToFederationDomain = new HashMap<>();
    private final Map<FederationDomainId, FederationDomainSnapshot> federationDomains = new HashMap<>();
    private final Map<FederationDomainId, Long> physicalSequences = new HashMap<>();
    private final Map<NetworkId, Set<FederationDomainId>> networkIndex = new HashMap<>();
    /** Diagnostic records of removed nodes kept at most; older ones are dropped first (every unloaded chunk adds some). */
    public static final int MAX_UNLOADED_INVALIDATIONS = 4096;

    private final Map<FederationDomainNodeId, FederationDomainInvalidationReason> invalidations = new HashMap<>();
    /** Nodes recorded as {@code SOURCE_UNLOADED}, oldest first; may hold nodes whose record changed since. */
    private final java.util.LinkedHashSet<FederationDomainNodeId> unloaded = new java.util.LinkedHashSet<>();
    private final Map<FederationDomainSourceId, FederationDomainId> directBridges = new HashMap<>();
    private long topologyRevision;
    private long physicalSequence;
    /** Nodes whose components changed since the last recomputation; recomputed together before the next read. */
    private final Set<FederationDomainNodeId> pendingSeeds = new TreeSet<>();
    /** Nodes removed since the last recomputation: one that returns first lets its removal take effect. */
    private final Set<FederationDomainNodeId> pendingRemovals = new HashSet<>();

    public FederationDomainRegistry(FederationDomainRecomputeBudget budget) {
        this.budget = budget;
    }

    public void upsertDirectBridge(FederationDomainSourceId source, NetworkId mainNetwork, NetworkId outerNetwork) {
        flush();
        if (mainNetwork.equals(outerNetwork)) {
            removeDirectBridge(source);
            return;
        }
        var memberships = new LinkedHashMap<NetworkId, Set<FederationDomainSourceId>>();
        memberships.computeIfAbsent(mainNetwork, ignored -> new TreeSet<>()).add(source.child("main"));
        memberships.computeIfAbsent(outerNetwork, ignored -> new TreeSet<>()).add(source.child("outer"));
        var federationDomainId = FederationDomainId.direct(source);
        var current = federationDomains.get(federationDomainId);
        if (current != null && directBridges.containsKey(source) && current.memberships().equals(memberships)) {
            return;
        }
        removeDirectBridge(source);
        install(new FederationDomainSnapshot(federationDomainId, ++topologyRevision, Set.of(), memberships));
        directBridges.put(source, federationDomainId);
    }

    public void invalidateDirectBridge(FederationDomainSourceId source) {
        flush();
        removeDirectBridge(source);
    }

    /** Records a node's evidence; returns false when it equals what the node already published. */
    public boolean upsertNode(FederationDomainNodeEvidence evidence) {
        var previous = nodes.get(evidence.nodeId());
        if (evidence.equals(previous)) {
            return false;
        }
        if (pendingRemovals.contains(evidence.nodeId())) {
            // A node that left and comes back joins a new domain, as it would if the removal had been read.
            flush();
        }
        pendingSeeds.addAll(affectedBy(evidence.nodeId(), previous, evidence));
        removeIncoming(previous);
        nodes.put(evidence.nodeId(), evidence);
        addIncoming(evidence);
        return true;
    }

    public void invalidateNode(FederationDomainNodeId nodeId, FederationDomainInvalidationReason reason) {
        var previous = nodes.remove(nodeId);
        if (previous != null) {
            pendingSeeds.addAll(affectedBy(nodeId, previous, null));
            pendingRemovals.add(nodeId);
            removeIncoming(previous);
        }
        invalidations.put(nodeId, reason);
        if (reason == FederationDomainInvalidationReason.SOURCE_UNLOADED) {
            unloaded.remove(nodeId);
            unloaded.add(nodeId);
            while (unloaded.size() > MAX_UNLOADED_INVALIDATIONS) {
                invalidations.remove(unloaded.removeFirst(), FederationDomainInvalidationReason.SOURCE_UNLOADED);
            }
        }
    }

    public void removeNode(FederationDomainNodeId nodeId) {
        invalidateNode(nodeId, FederationDomainInvalidationReason.SOURCE_UNLOADED);
    }

    public Set<FederationDomainId> federationdomainsFor(NetworkId networkId) {
        flush();
        return Set.copyOf(networkIndex.getOrDefault(networkId, Set.of()));
    }

    /** Whether two networks are members of a common Federation Domain, without copying either membership set. */
    public boolean shareFederationDomain(NetworkId first, NetworkId second) {
        flush();
        var firstDomains = networkIndex.getOrDefault(first, Set.of());
        var secondDomains = networkIndex.getOrDefault(second, Set.of());
        // Usually each network is in one domain: both sets then hold the same id object, which equals answers at
        // once, where the sorted set would compare its characters.
        if (firstDomains.size() == 1 && secondDomains.size() == 1) {
            return sole(firstDomains).equals(sole(secondDomains));
        }
        return !java.util.Collections.disjoint(firstDomains, secondDomains);
    }

    /** The one id of a single-domain membership; the index's sorted sets give it without an iterator. */
    private static FederationDomainId sole(Set<FederationDomainId> domains) {
        return domains instanceof java.util.SortedSet<FederationDomainId> sorted ? sorted.first()
                : domains.iterator().next();
    }

    public boolean isCurrent(FederationDomainReference reference) {
        flush();
        var current = federationDomains.get(reference.federationDomainId());
        return current != null && current.generation() == reference.generation();
    }

    public Optional<FederationDomainSnapshot> federationDomain(FederationDomainId federationDomainId) {
        flush();
        return Optional.ofNullable(federationDomains.get(federationDomainId));
    }

    /** The domain that contains a node, e.g. a Processing Endpoint that joins through its Federation face only. */
    public Optional<FederationDomainSnapshot> federationDomainOf(FederationDomainNodeId nodeId) {
        flush();
        return Optional.ofNullable(nodeToFederationDomain.get(nodeId)).map(federationDomains::get);
    }

    /** The revision {@link #snapshot()} would report, without copying the registry. */
    public long topologyRevision() {
        flush();
        return topologyRevision;
    }

    /** The current domains, like {@code snapshot().federationDomains().values()} without copying the indexes. */
    public List<FederationDomainSnapshot> federationDomains() {
        flush();
        return List.copyOf(federationDomains.values());
    }

    public FederationDomainRegistrySnapshot snapshot() {
        flush();
        var copiedIndex = new HashMap<NetworkId, Set<FederationDomainId>>();
        networkIndex.forEach((network, indexedFederationDomains) -> copiedIndex.put(network, Set.copyOf(indexedFederationDomains)));
        return new FederationDomainRegistrySnapshot(federationDomains, copiedIndex, invalidations, topologyRevision);
    }

    /** Recomputes every component a node change touched since the last read. */
    private void flush() {
        if (pendingSeeds.isEmpty()) {
            return;
        }
        var seeds = new TreeSet<>(pendingSeeds);
        pendingSeeds.clear();
        pendingRemovals.clear();
        recompute(seeds);
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

    /**
     * Rebuilds the components that contain the seeds and every node of the domains they belonged to, then replaces those
     * domains in one step. Nothing changes, not even the topology revision, when the rebuilt components equal them.
     */
    private void recompute(Set<FederationDomainNodeId> seeds) {
        var previous = new TreeSet<FederationDomainId>();
        seeds.stream().map(nodeToFederationDomain::get).filter(Objects::nonNull).forEach(previous::add);
        var affected = new TreeSet<>(seeds);
        previous.forEach(federationDomainId -> affected.addAll(federationDomains.get(federationDomainId).nodes()));
        var visited = new HashSet<FederationDomainNodeId>();
        var components = new ArrayList<Component>();
        for (var seed : affected) {
            if (!nodes.containsKey(seed) || visited.contains(seed)) {
                continue;
            }
            // The budget bounds each component, so recomputing several changes together fails no domain that
            // recomputing them one by one would keep.
            var component = collectComponent(seed, visited, new int[2]);
            if (component == null) {
                exhaustBudget(affected, visited, previous);
                return;
            }
            components.add(component);
        }
        // A component can reach a domain none of the seeds belonged to only through an edge that is already mutual, so
        // this is a safeguard: every domain a component overlaps is replaced with it.
        components.forEach(component -> component.nodes().stream().map(nodeToFederationDomain::get)
                .filter(Objects::nonNull).forEach(previous::add));
        var old = new LinkedHashMap<FederationDomainId, FederationDomainSnapshot>();
        previous.forEach(federationDomainId -> old.put(federationDomainId, federationDomains.get(federationDomainId)));
        var inherited = inherit(components, old);
        if (!changes(components, inherited, old)) {
            components.forEach(this::recordDiagnostics);
            return;
        }
        topologyRevision++;
        var sequences = new HashMap<FederationDomainId, Long>();
        old.keySet().forEach(federationDomainId -> sequences.put(federationDomainId, physicalSequences.get(federationDomainId)));
        old.keySet().forEach(this::removeFederationDomain);
        for (var index = 0; index < components.size(); index++) {
            var component = components.get(index);
            var federationDomainId = inherited.get(index);
            var before = federationDomainId == null ? null : old.get(federationDomainId);
            if (federationDomainId == null) {
                federationDomainId = FederationDomainId.physical(++physicalSequence);
            }
            physicalSequences.put(federationDomainId, before == null ? physicalSequence : sequences.get(federationDomainId));
            var generation = before != null && before.memberships().keySet().equals(component.memberships().keySet())
                    ? before.generation() : ++topologyRevision;
            installPhysical(new FederationDomainSnapshot(federationDomainId, generation, component.nodes(),
                    component.memberships()));
            recordDiagnostics(component);
        }
    }

    /**
     * Gives each component at most one old id and each old id to at most one component, preferring the old domain with
     * the same member networks, then the most shared members, the most shared nodes and finally the older domain: a split
     * leaves the id with the side that keeps the members, and a merge with the domain that already had most of them.
     */
    private Map<Integer, FederationDomainId> inherit(List<Component> components,
            Map<FederationDomainId, FederationDomainSnapshot> old) {
        var candidates = new ArrayList<Candidate>();
        for (var index = 0; index < components.size(); index++) {
            var component = components.get(index);
            for (var before : old.values()) {
                var nodeOverlap = (int) component.nodes().stream().filter(before.nodes()::contains).count();
                if (nodeOverlap == 0) {
                    continue;
                }
                var memberOverlap = (int) component.memberships().keySet().stream()
                        .filter(before.memberships()::containsKey).count();
                candidates.add(new Candidate(index, before.federationDomainId(),
                        component.memberships().keySet().equals(before.memberships().keySet()), memberOverlap, nodeOverlap,
                        physicalSequences.getOrDefault(before.federationDomainId(), Long.MAX_VALUE)));
            }
        }
        candidates.sort(INHERITANCE_ORDER);
        var inherited = new HashMap<Integer, FederationDomainId>();
        var taken = new HashSet<FederationDomainId>();
        for (var candidate : candidates) {
            if (!inherited.containsKey(candidate.component()) && taken.add(candidate.federationDomainId())) {
                inherited.put(candidate.component(), candidate.federationDomainId());
            }
        }
        return inherited;
    }

    private static boolean changes(List<Component> components, Map<Integer, FederationDomainId> inherited,
            Map<FederationDomainId, FederationDomainSnapshot> old) {
        if (inherited.size() != components.size() || inherited.size() != old.size()) {
            return true;
        }
        for (var index = 0; index < components.size(); index++) {
            var before = old.get(inherited.get(index));
            var component = components.get(index);
            if (!before.nodes().equals(component.nodes()) || !before.memberships().equals(component.memberships())) {
                return true;
            }
        }
        return false;
    }

    private void exhaustBudget(Set<FederationDomainNodeId> affected, Set<FederationDomainNodeId> visited,
            Set<FederationDomainId> previous) {
        visited.stream().map(nodeToFederationDomain::get).filter(Objects::nonNull).forEach(previous::add);
        previous.forEach(this::removeFederationDomain);
        affected.stream().filter(nodes::containsKey)
                .forEach(node -> invalidations.put(node, FederationDomainInvalidationReason.BUDGET_EXHAUSTED));
        visited.forEach(node -> invalidations.put(node, FederationDomainInvalidationReason.BUDGET_EXHAUSTED));
        topologyRevision++;
    }

    /** Walks mutual links only; returns null when the budget runs out, so no partial component is ever installed. */
    private Component collectComponent(FederationDomainNodeId seed, Set<FederationDomainNodeId> visited, int[] counters) {
        var queue = new ArrayDeque<FederationDomainNodeId>();
        var component = new TreeSet<FederationDomainNodeId>();
        var memberships = new LinkedHashMap<NetworkId, Set<FederationDomainSourceId>>();
        var pending = new HashSet<FederationDomainNodeId>();
        var unsettled = new HashSet<FederationDomainNodeId>();
        queue.add(seed);
        while (!queue.isEmpty()) {
            var current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (++counters[0] > budget.maxNodeVisits()) {
                return null;
            }
            component.add(current);
            for (var entry : nodes.get(current).ports().entrySet()) {
                if (++counters[1] > budget.maxPortVisits()) {
                    return null;
                }
                if (entry.getValue() instanceof FederationDomainPortEvidence.Federation federation) {
                    if (linked(current, entry.getKey(), federation)) {
                        queue.addLast(federation.peer().node());
                    } else {
                        pending.add(current);
                    }
                } else if (entry.getValue() instanceof FederationDomainPortEvidence.Native nativeEvidence) {
                    memberships.computeIfAbsent(nativeEvidence.networkId(), ignored -> new TreeSet<>())
                            .add(nativeEvidence.source());
                } else if (entry.getValue() instanceof FederationDomainPortEvidence.Unsettled) {
                    unsettled.add(current);
                }
            }
        }
        return new Component(component, memberships, pending, unsettled);
    }

    private boolean linked(FederationDomainNodeId node, String port, FederationDomainPortEvidence.Federation federation) {
        var peer = nodes.get(federation.peer().node());
        return peer != null && !federation.peer().node().equals(node)
                && peer.port(federation.peer().port()) instanceof FederationDomainPortEvidence.Federation back
                && back.peer().equals(new FederationDomainPortId(node, port));
    }

    /** A pending edge (NON_RECIPROCAL_EDGE) or an unsettled native port is reported on its node, not on the domain. */
    private void recordDiagnostics(Component component) {
        for (var node : component.nodes()) {
            if (component.pending().contains(node)) {
                invalidations.put(node, FederationDomainInvalidationReason.NON_RECIPROCAL_EDGE);
            } else if (component.unsettled().contains(node)) {
                invalidations.put(node, FederationDomainInvalidationReason.IDENTITY_UNSETTLED);
            } else {
                invalidations.remove(node);
                unloaded.remove(node);
            }
        }
    }

    private void installPhysical(FederationDomainSnapshot snapshot) {
        install(snapshot);
        snapshot.nodes().forEach(node -> nodeToFederationDomain.put(node, snapshot.federationDomainId()));
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
        physicalSequences.remove(federationDomainId);
        removed.nodes().forEach(node -> nodeToFederationDomain.remove(node, federationDomainId));
        removed.memberships().keySet().forEach(network -> {
            var indexed = networkIndex.get(network);
            indexed.remove(federationDomainId);
            if (indexed.isEmpty()) {
                networkIndex.remove(network);
            }
        });
        return removed;
    }

    private void removeDirectBridge(FederationDomainSourceId source) {
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

    private record Component(Set<FederationDomainNodeId> nodes, Map<NetworkId, Set<FederationDomainSourceId>> memberships,
            Set<FederationDomainNodeId> pending, Set<FederationDomainNodeId> unsettled) {
    }

    private record Candidate(int component, FederationDomainId federationDomainId, boolean membersEqual, int memberOverlap,
            int nodeOverlap, long sequence) {
    }
}
