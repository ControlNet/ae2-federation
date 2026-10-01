package space.controlnet.ae2federation.energy;

import appeng.api.networking.IGrid;
import appeng.me.energy.IEnergyOverlayGridConnection;
import appeng.me.service.EnergyService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.IdentityEpoch;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.BackendStatus;
import space.controlnet.ae2federation.policy.BindingDiagnostic;
import space.controlnet.ae2federation.policy.PolicyActivationState;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRuntimeEndpoints;
import space.controlnet.ae2federation.policy.PolicyService;

/**
 * Shares energy between Federation networks the way Quartz Fibers do. An enabled, active ME power rule between two
 * networks of a common Federation domain, in either direction, joins their Grids; AE2 merges every Grid reachable this
 * way into one energy pool, which each member draws from and charges as its own ({@link FederationEnergyConnection}).
 * Sharing is mutual and transitive, like AE2's: A sharing with B and B with C puts A, B and C in one pool.
 *
 * <p>Nothing runs per energy operation. Each change of who shares with whom re-forms the pools of the Grids it
 * touches at once ({@code EnergyService.invalidateOverlayEnergyGrid}): rule edits and topology publications reconcile
 * immediately, and each server tick reconciles again when the domain topology, any rule or any Grid identity changed.
 */
public final class EnergySharingService implements AutoCloseable {
    private static final Map<ServerLevel, EnergySharingService> SERVICES = new WeakHashMap<>();
    private static final Comparator<IGrid> BY_NETWORK = Comparator.comparing(grid -> FederationDomainRegistryAccess
            .confirmedNetworkId(grid).map(NetworkId::toString).orElse(""));

    private final ServerLevel level;
    private final EnergyFederationDomainObserver federationDomains;
    /** Each Grid's sharing peers, as their energy services, in network-id order. */
    private Map<IGrid, List<Peer>> peers = Map.of();
    /** The unordered network pairs that share, for the policy UI. */
    private Set<Set<NetworkId>> sharedPairs = Set.of();
    private final Map<PolicyKey, BindingDiagnostic> diagnostics = new HashMap<>();
    /** The inputs the last reconciliation saw; a tick reconciles again only when one of them changed. */
    private Object reconciledRegistry;
    private long reconciledTopology = Long.MIN_VALUE;
    private Object reconciledPolicies;
    private long reconciledWatermark = Long.MIN_VALUE;
    private long reconciledEpoch = Long.MIN_VALUE;
    private int reconciliations;

    private EnergySharingService(ServerLevel level) {
        this.level = level;
        federationDomains = new EnergyFederationDomainObserver(level);
    }

    public static synchronized EnergySharingService get(ServerLevel level) {
        return SERVICES.computeIfAbsent(level, EnergySharingService::new);
    }

    @Nullable
    static synchronized EnergySharingService find(ServerLevel level) {
        return SERVICES.get(level);
    }

    public static synchronized void reconcileIfPresent(ServerLevel level) {
        var service = SERVICES.get(level);
        if (service != null) {
            service.reconcileAll();
        }
    }

    /** Reconciles every level whose domain topology, rules or Grid identities changed since its last reconciliation. */
    public static synchronized void tickAll() {
        SERVICES.values().forEach(EnergySharingService::reconcileIfChanged);
    }

    public static synchronized CloseReceipt closeLevel(ServerLevel level) {
        var service = SERVICES.remove(level);
        var pairs = service == null ? 0 : service.sharedPairs.size();
        if (service != null) {
            service.close();
        }
        return new CloseReceipt(service != null, pairs);
    }

    /** Whether the two networks of {@code key} share energy, reconciling first if an input changed. */
    public static synchronized boolean shares(ServerLevel level, PolicyKey key) {
        var service = SERVICES.get(level);
        if (service == null) {
            return false;
        }
        service.reconcileIfChanged();
        return service.sharedPairs.contains(pair(key));
    }

    /**
     * Dissolves every shared pool of {@code level} after a domain topology change, without reading the registry (this
     * runs inside its mutation). AE2 forms each pool again on its next energy operation, and asking for
     * {@link #peers} then reconciles against the new topology first.
     */
    public static synchronized void topologyChanged(ServerLevel level) {
        var service = SERVICES.get(level);
        if (service != null) {
            reform(service.peers, Map.of());
        }
    }

    /** Read-only historical reason, discarded when policy or topology revisions no longer match. */
    public static synchronized Optional<BindingDiagnostic> lastDiagnostic(ServerLevel level, PolicyKey key) {
        var service = SERVICES.get(level);
        if (service == null) return Optional.empty();
        var diagnostic = service.diagnostics.get(key);
        return diagnostic != null && diagnostic.matches(PolicyService.get(level).revision(key),
                FederationDomainRegistryAccess.get(level).topologyRevision())
                ? Optional.of(diagnostic) : Optional.empty();
    }

    public void observeConnectedGrids(IGrid first, IGrid second) {
        federationDomains.register(first);
        federationDomains.register(second);
        reconcileAll();
    }

    public void observeFederationDomainMembers(Iterable<IGrid> grids) {
        federationDomains.register(grids);
        reconcileAll();
    }

    /**
     * The energy services {@code grid} shares with, after reconciling if any input changed; empty for a Grid this
     * service does not know (yet). AE2 asks while it forms a pool.
     */
    List<EnergyService> peers(@Nullable IGrid grid) {
        reconcileIfChanged();
        var listed = grid == null ? null : peers.get(grid);
        if (listed == null) {
            return List.of();
        }
        var services = new ArrayList<EnergyService>(listed.size());
        for (var peer : listed) {
            // A Grid that dissolved since (merged, split or destroyed) keeps no nodes and joins no pool.
            if (!peer.grid().isEmpty()) {
                services.add(peer.service());
            }
        }
        return services;
    }

    /** The number of unordered network pairs that share energy. */
    public int sharedPairCount() {
        return sharedPairs.size();
    }

    public int reconciliationCount() {
        return reconciliations;
    }

    private void reconcileIfChanged() {
        var registry = FederationDomainRegistryAccess.get(level);
        var policies = PolicyService.get(level);
        if (registry != reconciledRegistry || registry.topologyRevision() != reconciledTopology
                || policies != reconciledPolicies || policies.highWatermark() != reconciledWatermark
                || IdentityEpoch.current() != reconciledEpoch) {
            reconcileAll();
        }
    }

    public void reconcileAll() {
        var registry = FederationDomainRegistryAccess.get(level);
        var policies = PolicyService.get(level);
        reconciledRegistry = registry;
        reconciledTopology = registry.topologyRevision();
        reconciledPolicies = policies;
        reconciledWatermark = policies.highWatermark();
        reconciledEpoch = IdentityEpoch.current();
        reconciliations++;
        diagnostics.clear();
        var adjacency = new IdentityHashMap<IGrid, Set<IGrid>>();
        var pairs = new HashSet<Set<NetworkId>>();
        var linked = new IdentityHashMap<IGrid, Boolean>();
        for (var relationship : federationDomains.relationships().values()) {
            var key = relationship.key();
            if (!eligible(policies, key)) {
                continue;
            }
            var activation = policies.activation(key, new PolicyRuntimeEndpoints(relationship.consumerGrid(),
                    relationship.providerGrid(), BackendStatus.READY), registry);
            if (activation != PolicyActivationState.ACTIVE) {
                recordDiagnostic(policies, key, BindingDiagnostic.inactiveReason(activation));
                continue;
            }
            if (!linked.computeIfAbsent(relationship.consumerGrid(), EnergySharingService::hasConnection)
                    || !linked.computeIfAbsent(relationship.providerGrid(), EnergySharingService::hasConnection)) {
                recordDiagnostic(policies, key, BindingDiagnostic.Reason.ENERGY_CONNECTION_MISSING);
                continue;
            }
            adjacency.computeIfAbsent(relationship.consumerGrid(), ignored -> new HashSet<>())
                    .add(relationship.providerGrid());
            adjacency.computeIfAbsent(relationship.providerGrid(), ignored -> new HashSet<>())
                    .add(relationship.consumerGrid());
            pairs.add(pair(key));
        }
        var next = new IdentityHashMap<IGrid, List<Peer>>();
        adjacency.forEach((grid, neighbours) -> {
            var sorted = new ArrayList<>(neighbours);
            sorted.sort(BY_NETWORK);
            var listed = new ArrayList<Peer>(sorted.size());
            for (var neighbour : sorted) {
                if (neighbour.getEnergyService() instanceof EnergyService service) {
                    listed.add(new Peer(neighbour, service));
                }
            }
            next.put(grid, List.copyOf(listed));
        });
        var previous = peers;
        peers = Collections.unmodifiableMap(next);
        sharedPairs = Set.copyOf(pairs);
        reform(previous, next);
    }

    /**
     * Re-forms the pool of every Grid whose peers changed. Dissolving a pool clears it from all of its members, so the
     * Grids it no longer reaches form their own on their next energy operation, as AE2 does after a Quartz Fiber
     * changes.
     */
    private static void reform(Map<IGrid, List<Peer>> previous, Map<IGrid, List<Peer>> next) {
        var changed = Collections.newSetFromMap(new IdentityHashMap<IGrid, Boolean>());
        previous.forEach((grid, listed) -> {
            if (!listed.equals(next.get(grid))) changed.add(grid);
        });
        next.forEach((grid, listed) -> {
            if (!listed.equals(previous.get(grid))) changed.add(grid);
        });
        for (var grid : changed) {
            if (!grid.isEmpty() && grid.getEnergyService() instanceof EnergyService service) {
                service.invalidateOverlayEnergyGrid();
            }
        }
    }

    private static boolean eligible(PolicyService policies, PolicyKey key) {
        var configured = policies.configured(key).orElse(null);
        return configured != null && configured.rule().enabled()
                && configured.rule().operations().contains(PolicyOperation.SUPPLY);
    }

    /** Whether one of {@code grid}'s nodes carries a Federation energy link that AE2 asks when forming its pool. */
    private static boolean hasConnection(IGrid grid) {
        for (var ownerClass : FederationEnergyConnection.nodeOwnerClasses()) {
            for (var node : grid.getMachineNodes(ownerClass)) {
                if (node.getService(IEnergyOverlayGridConnection.class) instanceof FederationEnergyConnection connection
                        && connection.node() == node) {
                    return true;
                }
            }
        }
        return false;
    }

    private void recordDiagnostic(PolicyService policies, PolicyKey key, BindingDiagnostic.Reason reason) {
        diagnostics.put(key, new BindingDiagnostic(reason, policies.revision(key), federationDomains.topologyRevision()));
    }

    private static Set<NetworkId> pair(PolicyKey key) {
        return Set.of(key.consumerNetworkId(), key.providerNetworkId());
    }

    @Override
    public void close() {
        var previous = peers;
        peers = Map.of();
        sharedPairs = Set.of();
        diagnostics.clear();
        reform(previous, Map.of());
        federationDomains.clear();
    }

    private record Peer(IGrid grid, EnergyService service) {
    }

    public record CloseReceipt(boolean servicePresent, int sharedPairs) {
    }
}
