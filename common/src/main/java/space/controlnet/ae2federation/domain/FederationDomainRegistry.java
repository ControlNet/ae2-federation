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
import space.controlnet.ae2federation.policy.AuthorityEpoch;

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
 *
 * <p>A domain with any Federation Cable over {@link CableCapacityLimit}, times the cable's capacity factor, is over
 * capacity: it keeps its nodes, members and id, so nothing it counts changes, but the features that link networks treat
 * it as stopped. Only domains whose evidence changed, or every domain when the limit changes, are judged again, and only
 * a changed verdict advances the topology revision and the authority epoch.
 *
 * <p>Physical ids outlive the registry through {@link FederationDomainRecords}: after a restart, a record whose domain
 * is not loaded is one more old domain a component can inherit from, matched by its anchor nodes, and new ids continue
 * the saved sequence. An id that existed before its domain was last unloaded is restored, and a restored id wins over
 * one handed out since, so the part of a domain that loads first claims its id and keeps it when the rest joins.
 */
public final class FederationDomainRegistry {
    private static final Comparator<Candidate> INHERITANCE_ORDER = Comparator.comparing(Candidate::restored).reversed()
            .thenComparing(Comparator.comparing(Candidate::membersEqual).reversed())
            .thenComparing(Comparator.comparingInt(Candidate::memberOverlap).reversed())
            .thenComparing(Comparator.comparingInt(Candidate::nodeOverlap).reversed())
            .thenComparingLong(Candidate::sequence)
            .thenComparingInt(Candidate::component);

    private final Map<FederationDomainNodeId, FederationDomainNodeEvidence> nodes = new HashMap<>();
    private final Map<FederationDomainNodeId, Set<FederationDomainPortId>> incomingFederation = new HashMap<>();
    private final Map<FederationDomainNodeId, FederationDomainId> nodeToFederationDomain = new HashMap<>();
    private final Map<FederationDomainId, FederationDomainSnapshot> federationDomains = new HashMap<>();
    private final Map<FederationDomainId, Long> physicalSequences = new HashMap<>();
    /** The anchors and sequence that outlive this registry. */
    private final FederationDomainRecords records;
    /** Live domains whose id came from a record, directly or by inheriting from one that did. */
    private final Set<FederationDomainId> restored = new HashSet<>();
    private final Map<NetworkId, Set<FederationDomainId>> networkIndex = new HashMap<>();
    /** Diagnostic records of removed nodes kept at most; older ones are dropped first (every unloaded chunk adds some). */
    public static final int MAX_UNLOADED_INVALIDATIONS = 4096;

    private final Map<FederationDomainNodeId, FederationDomainInvalidationReason> invalidations = new HashMap<>();
    /** Nodes recorded as {@code SOURCE_UNLOADED}, oldest first; may hold nodes whose record changed since. */
    private final java.util.LinkedHashSet<FederationDomainNodeId> unloaded = new java.util.LinkedHashSet<>();
    private final Map<FederationDomainSourceId, FederationDomainId> directBridges = new HashMap<>();
    /** The dimension each Bridge's part lives in, so closing that level withdraws it. */
    private final Map<FederationDomainSourceId, String> bridgeDimensions = new HashMap<>();
    private long topologyRevision;
    /** Advances on every evidence change, including ones that leave every domain as it was. */
    private long evidenceRevision;
    private long physicalSequence;
    /** Nodes whose components changed since the last recomputation; recomputed together before the next read. */
    private final Set<FederationDomainNodeId> pendingSeeds = new TreeSet<>();
    /** Nodes removed since the last recomputation: one that returns first lets its removal take effect. */
    private final Set<FederationDomainNodeId> pendingRemovals = new HashSet<>();
    /** Domains whose cables exceed the capacity limit; they stop as a whole. */
    private final Set<FederationDomainId> overCapacity = new HashSet<>();
    /** Domains whose Beam Converters cannot pay their use; the operating power service sets them. They stop as a whole. */
    private final Set<FederationDomainId> unpowered = new HashSet<>();
    /** Nodes whose evidence changed since their domains were last judged against the capacity limit. */
    private final Set<FederationDomainNodeId> capacityNodes = new HashSet<>();
    /** Domains installed since they were last judged against the capacity limit. */
    private final Set<FederationDomainId> capacityDomains = new HashSet<>();
    /** Each domain's cable demand as last judged: current after every flush, so a cable's lookup counts nothing. */
    private final Map<FederationDomainId, Map<FederationDomainNodeId, CableCapacityCounter.Demand>> capacityCounts =
            new HashMap<>();
    /** The limit the verdicts were judged with; null before the first judgement. */
    private java.util.OptionalInt judgedLimit;

    private long recomputes;
    private long recomputeNanos;
    /** Runs after every change of node or bridge evidence, before the domains are recomputed from it. */
    private Runnable mutationListener = () -> {
    };

    public FederationDomainRegistry() {
        this(new FederationDomainRecords());
    }

    public FederationDomainRegistry(FederationDomainRecords records) {
        this.records = records;
        physicalSequence = records.sequence();
    }

    /**
     * Sets what runs after each evidence change. It runs inside the mutation, so it must not read this registry; the
     * domains it reads later are recomputed from the new evidence.
     */
    public void onMutation(Runnable listener) {
        mutationListener = java.util.Objects.requireNonNull(listener);
    }

    public void upsertDirectBridge(FederationDomainSourceId source, String dimension, NetworkId mainNetwork,
            NetworkId outerNetwork) {
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
        // Advanced before the change: pending evidence is recomputed only when the domains are next read, so a held
        // authorization learns of a change from AuthorityEpoch alone.
        AuthorityEpoch.advance();
        removeDirectBridge(source);
        install(new FederationDomainSnapshot(federationDomainId, ++topologyRevision, Set.of(), memberships));
        directBridges.put(source, federationDomainId);
        bridgeDimensions.put(source, dimension);
        mutated();
    }

    public void invalidateDirectBridge(FederationDomainSourceId source) {
        flush();
        AuthorityEpoch.advance();
        removeDirectBridge(source);
        mutated();
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
        AuthorityEpoch.advance();
        pendingSeeds.addAll(affectedBy(evidence.nodeId(), previous, evidence));
        capacityNodes.add(evidence.nodeId());
        removeIncoming(previous);
        nodes.put(evidence.nodeId(), evidence);
        addIncoming(evidence);
        mutated();
        return true;
    }

    public void invalidateNode(FederationDomainNodeId nodeId, FederationDomainInvalidationReason reason) {
        AuthorityEpoch.advance();
        var previous = nodes.remove(nodeId);
        if (previous != null) {
            pendingSeeds.addAll(affectedBy(nodeId, previous, null));
            pendingRemovals.add(nodeId);
            removeIncoming(previous);
            mutated();
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

    /** Removes a node whose block was broken: unlike an unloaded node, it no longer anchors its domain's id. */
    public void removeBrokenNode(FederationDomainNodeId nodeId) {
        records.drop(nodeId);
        removeNode(nodeId);
    }

    /** What {@link #removeDimension} took out: the dimension's nodes and Bridges, and how many of its nodes are left. */
    public record DimensionRemoval(String dimension, int nodesRemoved, int bridgesRemoved, int nodesLeft) {
    }

    /**
     * Removes everything one dimension published, as when its level closes: its nodes leave, its Bridges are withdrawn
     * and its diagnostics are dropped. The other dimensions' domains are recomputed without them, like AE2 destroying
     * only the closing level's nodes of each Grid.
     */
    public DimensionRemoval removeDimension(String dimension) {
        flush();
        var removedNodes = nodes.keySet().stream().filter(node -> node.dimension().equals(dimension)).toList();
        var removedBridges = bridgeDimensions.entrySet().stream().filter(entry -> entry.getValue().equals(dimension))
                .map(Map.Entry::getKey).toList();
        AuthorityEpoch.advance();
        for (var node : removedNodes) {
            var previous = nodes.remove(node);
            pendingSeeds.addAll(affectedBy(node, previous, null));
            removeIncoming(previous);
        }
        removedBridges.forEach(this::removeDirectBridge);
        flush();
        invalidations.keySet().removeIf(node -> node.dimension().equals(dimension));
        unloaded.removeIf(node -> node.dimension().equals(dimension));
        if (!removedNodes.isEmpty() || !removedBridges.isEmpty()) {
            mutated();
        }
        var left = (int) nodes.keySet().stream().filter(node -> node.dimension().equals(dimension)).count();
        return new DimensionRemoval(dimension, removedNodes.size(), removedBridges.size(), left);
    }

    public Set<FederationDomainId> federationdomainsFor(NetworkId networkId) {
        flush();
        return Set.copyOf(networkIndex.getOrDefault(networkId, Set.of()));
    }

    /**
     * Whether two networks share a Federation domain that runs: one neither over capacity nor unpowered. The features
     * that link networks ask this; {@link #shareFederationDomain} answers for the topology alone.
     */
    public boolean shareRunningFederationDomain(NetworkId first, NetworkId second) {
        flush();
        return shareFederationDomain(first, second,
                domain -> !overCapacity.contains(domain) && !unpowered.contains(domain));
    }

    /** Whether two networks share a domain over capacity: the cause named when they share no running one. */
    public boolean shareOverCapacityFederationDomain(NetworkId first, NetworkId second) {
        flush();
        return shareFederationDomain(first, second, overCapacity::contains);
    }

    /** Whether a domain has a Federation Cable over the capacity limit, and so stops as a whole. */
    public boolean overCapacity(FederationDomainId federationDomainId) {
        flush();
        return overCapacity.contains(federationDomainId);
    }

    /** Whether a domain's Beam Converters cannot pay its use, and so it stops as a whole. */
    public boolean unpowered(FederationDomainId federationDomainId) {
        flush();
        return unpowered.contains(federationDomainId);
    }

    /** Whether a domain has stopped as a whole, over capacity or unpowered. */
    public boolean stopped(FederationDomainId federationDomainId) {
        flush();
        return overCapacity.contains(federationDomainId) || unpowered.contains(federationDomainId);
    }

    public Set<FederationDomainId> unpoweredDomains() {
        flush();
        return Set.copyOf(unpowered);
    }

    /**
     * Records the operating power service's verdict for a domain. Like a capacity verdict, only a change advances the
     * topology and the authority, so the services that link networks notice it; a domain the registry does not know is
     * ignored.
     */
    public void setUnpowered(FederationDomainId federationDomainId, boolean value) {
        flush();
        if (value && !federationDomains.containsKey(federationDomainId)) return;
        if (value ? unpowered.add(federationDomainId) : unpowered.remove(federationDomainId)) {
            topologyRevision++;
            AuthorityEpoch.advance();
        }
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
        return shareFederationDomain(first, second, domain -> true);
    }

    /**
     * Whether two networks share a domain that passes {@code filter}. Walks the smaller membership: a main network with
     * a Bridge to each of many subnets is in many domains, each subnet in one. ({@code Collections.disjoint} walks its
     * second argument whenever the first is a set, whatever their sizes.)
     */
    private boolean shareFederationDomain(NetworkId first, NetworkId second,
            java.util.function.Predicate<FederationDomainId> filter) {
        var firstDomains = networkIndex.getOrDefault(first, Set.of());
        var secondDomains = networkIndex.getOrDefault(second, Set.of());
        var smaller = firstDomains.size() <= secondDomains.size() ? firstDomains : secondDomains;
        var larger = smaller == firstDomains ? secondDomains : firstDomains;
        for (var domain : smaller) {
            if (larger.contains(domain) && filter.test(domain)) return true;
        }
        return false;
    }

    /**
     * The current references of the domains both networks are members of. Walks the smaller membership and copies
     * neither, so a main network with a Bridge to each of many subnets costs each subnet's lookup one domain.
     */
    public Set<FederationDomainReference> sharedFederationDomainReferences(NetworkId first, NetworkId second) {
        flush();
        var firstDomains = networkIndex.getOrDefault(first, Set.of());
        var secondDomains = networkIndex.getOrDefault(second, Set.of());
        var smaller = firstDomains.size() <= secondDomains.size() ? firstDomains : secondDomains;
        var larger = smaller == firstDomains ? secondDomains : firstDomains;
        List<FederationDomainReference> shared = null;
        for (var id : smaller) {
            var domain = larger.contains(id) ? federationDomains.get(id) : null;
            if (domain == null) continue;
            if (shared == null) shared = new ArrayList<>(smaller.size());
            shared.add(domain.reference());
        }
        return shared == null ? Set.of() : Set.copyOf(shared);
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

    /** Changes whenever any node's or Bridge's evidence changes, even when no domain does. */
    public long evidenceRevision() {
        return evidenceRevision;
    }

    /** The evidence each node of a domain published; empty for a direct Bridge domain or an unknown id. */
    public List<FederationDomainNodeEvidence> nodeEvidence(FederationDomainId federationDomainId) {
        flush();
        return evidenceOf(federationDomains.get(federationDomainId));
    }

    private List<FederationDomainNodeEvidence> evidenceOf(FederationDomainSnapshot domain) {
        return domain == null ? List.of()
                : domain.nodes().stream().map(nodes::get).filter(Objects::nonNull).toList();
    }

    private void mutated() {
        evidenceRevision++;
        mutationListener.run();
    }

    private long nextTopologyRevision() {
        AuthorityEpoch.advance();
        return ++topologyRevision;
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

    /** Recomputes every component a node change touched since the last read, then judges their capacity. */
    private void flush() {
        if (!pendingSeeds.isEmpty()) {
            var seeds = new TreeSet<>(pendingSeeds);
            pendingSeeds.clear();
            pendingRemovals.clear();
            long started = System.nanoTime();
            recompute(seeds);
            recomputes++;
            recomputeNanos += System.nanoTime() - started;
        }
        judgeCapacity();
        // A domain that is gone lets go of its power verdict; one that inherited the id keeps it.
        if (unpowered.removeIf(federationDomainId -> !federationDomains.containsKey(federationDomainId))) {
            topologyRevision++;
            AuthorityEpoch.advance();
        }
    }

    /**
     * Judges again the domains whose evidence changed or that were installed since, or every domain when the limit
     * changed. The verdict changes nothing the count reads, so a stopped domain stays stopped until its evidence or the
     * limit changes.
     */
    private void judgeCapacity() {
        var limit = CableCapacityLimit.current();
        if (!limit.equals(judgedLimit)) {
            judgedLimit = limit;
            capacityDomains.addAll(federationDomains.keySet());
        }
        if (capacityNodes.isEmpty() && capacityDomains.isEmpty()) {
            return;
        }
        capacityNodes.stream().map(nodeToFederationDomain::get).filter(Objects::nonNull).forEach(capacityDomains::add);
        capacityNodes.clear();
        boolean changed = false;
        for (var federationDomainId : capacityDomains) {
            var domain = federationDomains.get(federationDomainId);
            // Counted with or without a limit: the cables show the count either way.
            var counts = domain == null ? null : CableCapacityCounter.count(evidenceOf(domain));
            if (counts == null) {
                capacityCounts.remove(federationDomainId);
            } else {
                capacityCounts.put(federationDomainId, counts);
            }
            boolean over = counts != null && limit.isPresent() && counts.entrySet().stream()
                    .anyMatch(entry -> CableCapacityLimit.current(nodes.get(entry.getKey()).capacityFactor()).stream()
                            .anyMatch(cap -> entry.getValue().units() > cap));
            changed |= over ? overCapacity.add(federationDomainId) : overCapacity.remove(federationDomainId);
        }
        capacityDomains.clear();
        if (changed) {
            topologyRevision++;
            AuthorityEpoch.advance();
        }
    }

    /**
     * The demand of each cable, P2P tunnel and other counting node of the domain, as {@link CableCapacityCounter}
     * counts it; empty for a domain that does not exist.
     */
    public Map<FederationDomainNodeId, CableCapacityCounter.Demand> capacityDemand(FederationDomainId federationDomainId) {
        flush();
        return capacityCounts.getOrDefault(federationDomainId, Map.of());
    }

    /**
     * Whether evidence changed since the domains were last recomputed and judged; the next read does both. A caller that
     * only shows the domain, such as a cable's count, waits for a settled registry instead of recomputing it between
     * two block entities' changes.
     */
    public boolean pending() {
        return !pendingSeeds.isEmpty() || !capacityNodes.isEmpty() || !capacityDomains.isEmpty()
                || !federationDomains.isEmpty() && !CableCapacityLimit.current().equals(judgedLimit);
    }

    /** Recomputes and judges pending changes now, as the next read would. */
    public void settle() {
        if (pending()) flush();
    }

    /** How many times changed components were recomputed, for diagnostics and benchmarks. */
    public long recomputes() {
        return recomputes;
    }

    /** The time spent recomputing changed components, in nanoseconds, for diagnostics and benchmarks. */
    public long recomputeNanos() {
        return recomputeNanos;
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
            // A domain has no size limit, as an AE2 Grid has none; the registry recomputes at most once per tick.
            components.add(collectComponent(seed, visited));
        }
        // A component can reach a domain none of the seeds belonged to only through an edge that is already mutual, so
        // this is a safeguard: every domain a component overlaps is replaced with it.
        components.forEach(component -> component.nodes().stream().map(nodeToFederationDomain::get)
                .filter(Objects::nonNull).forEach(previous::add));
        var old = new LinkedHashMap<FederationDomainId, FederationDomainSnapshot>();
        previous.forEach(federationDomainId -> old.put(federationDomainId, federationDomains.get(federationDomainId)));
        var dormant = new HashMap<FederationDomainId, Long>();
        var inherited = inherit(components, old, dormant);
        if (!changes(components, inherited, old)) {
            components.forEach(this::recordDiagnostics);
            return;
        }
        topologyRevision++;
        AuthorityEpoch.advance();
        var sequences = new HashMap<>(dormant);
        old.keySet().forEach(federationDomainId -> sequences.put(federationDomainId, physicalSequences.get(federationDomainId)));
        var wasRestored = new HashSet<FederationDomainId>();
        old.keySet().stream().filter(restored::contains).forEach(wasRestored::add);
        var absorbed = absorbed(components, inherited, old);
        old.keySet().forEach(this::removeFederationDomain);
        for (var index = 0; index < components.size(); index++) {
            var component = components.get(index);
            var federationDomainId = inherited.get(index);
            var before = federationDomainId == null ? null : old.get(federationDomainId);
            if (federationDomainId == null) {
                federationDomainId = FederationDomainId.physical(++physicalSequence);
                records.advanceSequence(physicalSequence);
            }
            long sequence = sequences.getOrDefault(federationDomainId, physicalSequence);
            physicalSequences.put(federationDomainId, sequence);
            if (dormant.containsKey(federationDomainId) || wasRestored.contains(federationDomainId)) {
                restored.add(federationDomainId);
            }
            var generation = before != null && before.memberships().keySet().equals(component.memberships().keySet())
                    ? before.generation() : nextTopologyRevision();
            installPhysical(new FederationDomainSnapshot(federationDomainId, generation, component.nodes(),
                    component.memberships()));
            var absorbedSequences = absorbed.getOrDefault(index, List.of()).stream().map(sequences::get).toList();
            anchor(sequence, component, absorbedSequences);
            absorbedSequences.forEach(records::remove);
            recordDiagnostics(component);
        }
    }

    /** The old domains no component inherits, each under the first component that took some of its nodes. */
    private static Map<Integer, List<FederationDomainId>> absorbed(List<Component> components,
            Map<Integer, FederationDomainId> inherited, Map<FederationDomainId, FederationDomainSnapshot> old) {
        var absorbed = new HashMap<Integer, List<FederationDomainId>>();
        for (var before : old.values()) {
            if (inherited.containsValue(before.federationDomainId())) {
                continue;
            }
            for (var index = 0; index < components.size(); index++) {
                if (!java.util.Collections.disjoint(components.get(index).nodes(), before.nodes())) {
                    absorbed.computeIfAbsent(index, ignored -> new ArrayList<>()).add(before.federationDomainId());
                    break;
                }
            }
        }
        return absorbed;
    }

    /**
     * Records the anchors of a domain just installed: its devices, or its smallest cables when it has none, together
     * with the anchors that its record and the records of the domains it absorbed keep for nodes not loaded now.
     */
    private void anchor(long sequence, Component component, List<Long> absorbed) {
        var devices = new TreeSet<FederationDomainNodeId>();
        var cables = new TreeSet<FederationDomainNodeId>();
        for (var node : component.nodes()) {
            if (nodes.get(node).kind() != FederationDomainNodeKind.CABLE) {
                devices.add(node);
            }
        }
        var previous = new ArrayList<>(absorbed);
        previous.add(sequence);
        for (var record : previous) {
            records.anchors(record).ifPresent(anchors -> {
                anchors.devices().stream().filter(node -> !nodes.containsKey(node)).forEach(devices::add);
                anchors.cables().stream().filter(node -> !nodes.containsKey(node)).forEach(cables::add);
            });
        }
        if (devices.isEmpty()) {
            // The component's nodes are sorted, so only its first ones can be among the smallest.
            component.nodes().stream().limit(FederationDomainRecords.CABLE_ANCHORS).forEach(cables::add);
            while (cables.size() > FederationDomainRecords.CABLE_ANCHORS) {
                cables.pollLast();
            }
        } else {
            cables.clear();
        }
        records.put(sequence, devices, cables);
    }

    /**
     * Gives each component at most one old id and each old id to at most one component, preferring a restored id, then
     * the old domain with the same member networks, then the most shared members, the most shared nodes and finally the
     * older domain: a split leaves the id with the side that keeps the members, and a merge with the domain that already
     * had most of them. A saved record whose domain is not loaded counts as an old domain with its anchors as nodes.
     */
    private Map<Integer, FederationDomainId> inherit(List<Component> components,
            Map<FederationDomainId, FederationDomainSnapshot> old, Map<FederationDomainId, Long> dormant) {
        var candidates = new ArrayList<Candidate>();
        for (var index = 0; index < components.size(); index++) {
            var component = components.get(index);
            // A record whose domain is not loaded competes as an old domain that shares only its anchors.
            var anchored = new HashMap<Long, Integer>();
            component.nodes().forEach(node -> records.owner(node)
                    .ifPresent(owner -> anchored.merge(owner, 1, Integer::sum)));
            for (var entry : anchored.entrySet()) {
                var federationDomainId = FederationDomainId.physical(entry.getKey());
                if (!federationDomains.containsKey(federationDomainId)) {
                    dormant.put(federationDomainId, entry.getKey());
                    candidates.add(new Candidate(index, federationDomainId, true, false, 0, entry.getValue(),
                            entry.getKey()));
                }
            }
            for (var before : old.values()) {
                var nodeOverlap = (int) component.nodes().stream().filter(before.nodes()::contains).count();
                if (nodeOverlap == 0) {
                    continue;
                }
                var memberOverlap = (int) component.memberships().keySet().stream()
                        .filter(before.memberships()::containsKey).count();
                candidates.add(new Candidate(index, before.federationDomainId(), restored.contains(before.federationDomainId()),
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
            if (before == null || !before.nodes().equals(component.nodes()) || !before.memberships().equals(component.memberships())) {
                return true;
            }
        }
        return false;
    }

    /** Walks mutual links only. */
    private Component collectComponent(FederationDomainNodeId seed, Set<FederationDomainNodeId> visited) {
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
            component.add(current);
            for (var entry : nodes.get(current).ports().entrySet()) {
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
        capacityDomains.add(snapshot.federationDomainId());
        snapshot.memberships().keySet().forEach(network -> networkIndex
                .computeIfAbsent(network, ignored -> new TreeSet<>()).add(snapshot.federationDomainId()));
    }

    private FederationDomainSnapshot removeFederationDomain(FederationDomainId federationDomainId) {
        var removed = federationDomains.remove(federationDomainId);
        if (removed == null) {
            return null;
        }
        physicalSequences.remove(federationDomainId);
        restored.remove(federationDomainId);
        // Judged again, so a stopped domain that is gone lets go of its verdict with a topology change.
        capacityDomains.add(federationDomainId);
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
        bridgeDimensions.remove(source);
        var federationDomainId = directBridges.remove(source);
        if (federationDomainId != null) {
            AuthorityEpoch.advance();
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

    private record Candidate(int component, FederationDomainId federationDomainId, boolean restored, boolean membersEqual,
            int memberOverlap, int nodeOverlap, long sequence) {
    }
}
