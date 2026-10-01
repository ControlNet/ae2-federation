package space.controlnet.ae2federation.energy;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IAEPowerStorage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.policy.BackendStatus;
import space.controlnet.ae2federation.policy.PolicyActivationState;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRuntimeEndpoints;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.observability.LevelObservabilityService;
import space.controlnet.ae2federation.observability.state.ResourceUnit;

public final class EnergyBindingService implements AutoCloseable {
    private static final Map<ServerLevel, EnergyBindingService> SERVICES = new WeakHashMap<>();
    private static final java.util.function.Supplier<String> ENERGY_RESOURCE = () -> "ae2:energy";

    private final ServerLevel level;
    private final EnergyFederationDomainObserver federationDomains;
    private final NativeEnergyBackendRegistry backends = new NativeEnergyBackendRegistry();
    private final Map<PolicyKey, EnergyCapabilityBinding> bindings = new HashMap<>();
    private final Map<PolicyKey, space.controlnet.ae2federation.policy.BindingDiagnostic> diagnostics = new java.util.HashMap<>();
    private int publications;
    private int withdrawals;
    private int demandDepth;
    /** Game tick of the last full reconciliation; a demand reconciles at most once per tick. */
    private long reconciledTick = Long.MIN_VALUE;
    /**
     * Advances after any binding change; each consumer source keeps its bindings in provider-network order, listed
     * at a revision, and they are listed again at the next.
     */
    private long candidatesRevision;

    private EnergyBindingService(ServerLevel level) {
        this.level = level;
        federationDomains = new EnergyFederationDomainObserver(level);
    }

    /**
     * The service {@link #get} or {@link #find} returned last, read without the lock: every energy demand looks it up.
     * Replaced under the lock, cleared on close.
     */
    private static volatile EnergyBindingService last;

    public static EnergyBindingService get(ServerLevel level) {
        var cached = last;
        return cached != null && cached.level == level ? cached : getLocked(level);
    }

    private static synchronized EnergyBindingService getLocked(ServerLevel level) {
        var service = SERVICES.computeIfAbsent(level, EnergyBindingService::new);
        last = service;
        return service;
    }

    @Nullable
    static EnergyBindingService find(ServerLevel level) {
        var cached = last;
        return cached != null && cached.level == level ? cached : findLocked(level);
    }

    @Nullable
    private static synchronized EnergyBindingService findLocked(ServerLevel level) {
        var service = SERVICES.get(level);
        if (service != null) {
            last = service;
        }
        return service;
    }

    /** Last reconciliation snapshot only; does not create a service, reconcile, or authorize an operation. */
    public static synchronized boolean hasPublishedBinding(ServerLevel level, PolicyKey key) {
        var service = SERVICES.get(level);
        return service != null && service.bindings.containsKey(key);
    }

    /** Read-only historical reason, discarded when policy or topology revisions no longer match. */
    public static synchronized Optional<space.controlnet.ae2federation.policy.BindingDiagnostic> lastDiagnostic(
            ServerLevel level, PolicyKey key) {
        var service = SERVICES.get(level);
        if (service == null) return Optional.empty();
        var diagnostic = service.diagnostics.get(key);
        return diagnostic != null && diagnostic.matches(PolicyService.get(level).revision(key),
                FederationDomainRegistryAccess.get(level).topologyRevision())
                ? Optional.of(diagnostic) : Optional.empty();
    }

    private void recordDiagnostic(PolicyKey key, space.controlnet.ae2federation.policy.BindingDiagnostic.Reason reason) {
        diagnostics.put(key, new space.controlnet.ae2federation.policy.BindingDiagnostic(reason,
                PolicyService.get(level).revision(key), federationDomains.topologyRevision()));
    }

    public static synchronized void reconcileIfPresent(ServerLevel level) {
        var service = SERVICES.get(level);
        if (service != null) {
            service.reconcileAll();
        }
    }

    public static synchronized CloseReceipt closeLevel(ServerLevel level) {
        last = null;
        var service = SERVICES.remove(level);
        var active = service == null ? 0 : service.bindings.size();
        if (service != null) {
            service.close();
        }
        return new CloseReceipt(service != null, active);
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

    public void reconcileAll() {
        reconciledTick = level.getGameTime();
        EnergyRouteGuard.current().forget();
        diagnostics.clear();
        var desired = federationDomains.relationships();
        backends.retain(desired.values().stream().map(EnergyRelationship::providerGrid).toList());
        var policies = PolicyService.get(level);
        List.copyOf(bindings.keySet()).stream().filter(key -> !eligible(policies, desired.get(key)))
                .forEach(this::remove);
        desired.values().stream().filter(relationship -> eligible(policies, relationship))
                .forEach(this::reconcile);
    }

    public Optional<EnergyCapabilityBinding> capability(PolicyKey key) {
        reconcileAll();
        var binding = bindings.get(key);
        return binding != null && binding.isCurrent() ? Optional.of(binding) : Optional.empty();
    }

    public int relationshipCount() {
        return bindings.size();
    }

    public int publicationCount() {
        return publications;
    }

    public int withdrawalCount() {
        return withdrawals;
    }

    double extract(DirectionalEnergySource source, IGrid consumerGrid, double amount, Actionable mode) {
        if (!Double.isFinite(amount) || amount < 0) {
            throw new IllegalArgumentException("Energy amount must be finite and nonnegative");
        }
        var guard = EnergyRouteGuard.current();
        var started = guard.enter(consumerGrid);
        try {
            return demand(source, consumerGrid, amount, mode, guard);
        } finally {
            guard.exit(started);
        }
    }

    private double demand(DirectionalEnergySource source, IGrid consumerGrid, double amount, Actionable mode,
            EnergyRouteGuard.Demand guard) {
        var outermost = demandDepth == 0;
        demandDepth++;
        try {
            // Policy edits and topology publications reconcile at once; this catches the rest (a provider Grid that
            // gained its first energy source, a node that finished booting) within one tick.
            if (reconciledTick != level.getGameTime()) {
                reconcileAll();
            }
            var extracted = 0.0;
            var candidates = candidates(source);
            for (var index = 0; index < candidates.size(); index++) {
                var binding = candidates.get(index);
                if (binding.consumerGrid() != consumerGrid) {
                    continue;
                }
                var accepted = binding.extract(amount - extracted, mode, guard);
                extracted += accepted;
                if (outermost && mode == Actionable.MODULATE && accepted > 0) {
                    var observability = LevelObservabilityService.get(level);
                    var acceptedNanoAe = nanoAe(accepted);
                    // The binding's key is the ordered (consumer, provider, ME_POWER) Policy key of its two Grids.
                    observability.recordPairFlow(binding.key(), acceptedNanoAe);
                    observability.recordAccepted(binding.scopes(), ENERGY_RESOURCE, acceptedNanoAe, ResourceUnit.NANO_AE,
                            space.controlnet.ae2federation.observability.state.FlowState.Attribution.EXACT_OPERATION);
                }
                if (extracted >= amount) {
                    break;
                }
            }
            return Math.min(amount, extracted);
        } finally {
            demandDepth--;
        }
    }

    private static boolean anyCurrent(Iterable<space.controlnet.ae2federation.domain.FederationDomainReference> references,
            space.controlnet.ae2federation.domain.FederationDomainRegistry registry) {
        for (var reference : references) {
            if (registry.isCurrent(reference)) {
                return true;
            }
        }
        return false;
    }

    static long nanoAe(double amount) {
        if (!Double.isFinite(amount) || amount < 0) {
            throw new ArithmeticException("Energy amount is not exactly representable");
        }
        // A whole number of AE below 2^53 converts exactly; the decimal path would give the same value.
        if (amount < 0x1p53 && amount == Math.rint(amount)) {
            return Math.multiplyExact((long) amount, 1_000_000_000L);
        }
        return java.math.BigDecimal.valueOf(amount).movePointRight(9).longValueExact();
    }

    @Override
    public void close() {
        diagnostics.clear();
        withdrawals += bindings.size();
        bindings.values().forEach(EnergyCapabilityBinding::withdraw);
        bindings.clear();
        candidatesRevision++;
        backends.clear();
        federationDomains.clear();
    }

    private boolean eligible(PolicyService policies, @Nullable EnergyRelationship relationship) {
        if (relationship == null) {
            return false;
        }
        var configured = policies.configured(relationship.key()).orElse(null);
        return configured != null && configured.rule().enabled()
                && configured.rule().operations().contains(PolicyOperation.SUPPLY);
    }

    private void reconcile(EnergyRelationship relationship) {
        NativeEnergyBackend backend;
        try {
            backend = backends.discover(relationship.providerGrid());
        } catch (EnergyBackendUnavailableException exception) {
            recordDiagnostic(relationship.key(), exception.reason());
            remove(relationship.key());
            return;
        } catch (IllegalStateException exception) {
            remove(relationship.key());
            return;
        }
        var policies = PolicyService.get(level);
        var activation = policies.activation(relationship.key(), new PolicyRuntimeEndpoints(relationship.consumerGrid(),
                relationship.providerGrid(), BackendStatus.READY));
        if (activation != PolicyActivationState.ACTIVE) {
            recordDiagnostic(relationship.key(), space.controlnet.ae2federation.policy.BindingDiagnostic.inactiveReason(activation));
            remove(relationship.key());
            return;
        }
        var references = federationDomains.references(relationship);
        var source = selectSource(relationship.consumerGrid());
        if (references.isEmpty() || source == null) {
            recordDiagnostic(relationship.key(), references.isEmpty()
                    ? space.controlnet.ae2federation.policy.BindingDiagnostic.Reason.DOMAIN_REFERENCE_MISSING
                    : space.controlnet.ae2federation.policy.BindingDiagnostic.Reason.CONSUMER_ENERGY_INTERFACE_MISSING);
            remove(relationship.key());
            return;
        }
        var configured = policies.configured(relationship.key()).orElseThrow();
        var revision = new EnergyBindingRevision(configured.revision(), federationDomains.topologyRevision(), references,
                backend.generation());
        var active = bindings.get(relationship.key());
        if (active != null && active.consumerGrid() == relationship.consumerGrid()
                && active.providerGrid() == relationship.providerGrid() && active.providerService() == backend.service()
                && active.consumerSource() == source && active.revision().sameAuthority(revision)) {
            return;
        }
        remove(relationship.key());
        var holder = new EnergyCapabilityBinding[1];
        var binding = new EnergyCapabilityBinding(relationship, backend, revision, source,
                () -> current(holder[0], backend));
        holder[0] = binding;
        bindings.put(relationship.key(), binding);
        candidatesRevision++;
        publications++;
        if (demandDepth == 0) {
            source.announceAvailability();
        }
    }

    private List<EnergyCapabilityBinding> candidates(DirectionalEnergySource source) {
        if (source.candidatesOwner == this && source.candidatesRevision == candidatesRevision) {
            return source.candidates;
        }
        var list = bindings.values().stream()
                .filter(binding -> binding.consumerSource() == source)
                .sorted(Comparator.comparing(binding -> binding.key().providerNetworkId().toString()))
                .toList();
        source.candidatesOwner = this;
        source.candidatesRevision = candidatesRevision;
        source.candidates = list;
        return list;
    }

    /** Rediscovers a provider's native sources at most once per tick; any change is caught on the next tick. */
    private boolean backendCurrent(NativeEnergyBackend backend) {
        var tick = level.getGameTime();
        if (!backend.checkedAt(tick)) {
            backend.checked(tick, backends.isCurrent(backend));
        }
        return backend.checkedCurrent();
    }

    private boolean current(EnergyCapabilityBinding binding, NativeEnergyBackend backend) {
        // Every path that drops a binding from the map withdraws it, and a put always follows remove(key), so this is
        // the map's own answer to whether the binding is still the published one.
        if (binding == null || binding.withdrawn()) {
            return false;
        }
        if (!binding.revision().providerGeneration().equals(backend.generation())
                || !backendCurrent(backend)) {
            remove(binding.key());
            reconcileAll();
            return false;
        }
        var registry = FederationDomainRegistryAccess.get(level);
        var topology = registry.topologyRevision();
        var policies = PolicyService.get(level);
        var watermark = policies.highWatermark();
        var key = binding.key();
        if (binding.passedAt(registry, topology, policies, watermark)) {
            // No rule and no domain changed since the full check below passed, so its rule, domain-reference and
            // shared-domain parts still hold; the Grids' identities are read again.
            var epoch = space.controlnet.ae2federation.identity.IdentityEpoch.current();
            if (binding.matchedAt(epoch)
                    || policies.identitiesMatch(key, binding.consumerIdentity(), binding.providerIdentity())) {
                binding.matched(epoch);
                return true;
            }
            remove(key);
            return false;
        }
        if (!anyCurrent(binding.revision().federationDomains(), registry)) {
            remove(key);
            return false;
        }
        var configured = policies.configured(key).orElse(null);
        var authorized = configured != null && configured.revision().equals(binding.revision().policyRevision())
                && configured.rule().enabled() && configured.rule().operations().contains(PolicyOperation.SUPPLY)
                && policies.activation(configured, binding.consumerIdentity(), binding.providerIdentity(),
                        BackendStatus.READY, registry) == PolicyActivationState.ACTIVE;
        if (authorized) {
            binding.passed(registry, topology, policies, watermark);
        } else {
            remove(key);
        }
        return authorized;
    }

    @Nullable
    private static DirectionalEnergySource selectSource(IGrid consumerGrid) {
        var candidates = new ArrayList<DirectionalEnergySource>();
        for (var ownerClass : DirectionalEnergySource.nodeOwnerClasses()) {
            for (var node : consumerGrid.getMachineNodes(ownerClass)) {
                var storage = node.getService(IAEPowerStorage.class);
                if (storage instanceof DirectionalEnergySource source && source.node() == node) {
                    candidates.add(source);
                }
            }
        }
        return candidates.stream().min(Comparator.comparingInt(System::identityHashCode)).orElse(null);
    }

    private void remove(PolicyKey key) {
        var removed = bindings.remove(key);
        if (removed != null) {
            removed.withdraw();
            candidatesRevision++;
            withdrawals++;
        }
    }

    public record CloseReceipt(boolean servicePresent, int bindingsWithdrawn) {
    }
}
