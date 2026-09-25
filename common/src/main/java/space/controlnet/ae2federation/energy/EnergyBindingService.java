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

    private final ServerLevel level;
    private final EnergyFederationDomainObserver federationDomains;
    private final NativeEnergyBackendRegistry backends = new NativeEnergyBackendRegistry();
    private final Map<PolicyKey, EnergyCapabilityBinding> bindings = new HashMap<>();
    private int publications;
    private int withdrawals;
    private int demandDepth;

    private EnergyBindingService(ServerLevel level) {
        this.level = level;
        federationDomains = new EnergyFederationDomainObserver(level);
    }

    public static synchronized EnergyBindingService get(ServerLevel level) {
        return SERVICES.computeIfAbsent(level, EnergyBindingService::new);
    }

    @Nullable
    static synchronized EnergyBindingService find(ServerLevel level) {
        return SERVICES.get(level);
    }

    public static synchronized void reconcileIfPresent(ServerLevel level) {
        var service = SERVICES.get(level);
        if (service != null) {
            service.reconcileAll();
        }
    }

    public static synchronized CloseReceipt closeLevel(ServerLevel level) {
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
        var desired = federationDomains.relationships();
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
        var outermost = demandDepth == 0;
        demandDepth++;
        try {
            reconcileAll();
            var candidates = bindings.values().stream()
                    .filter(binding -> binding.consumerSource() == source && binding.consumerGrid() == consumerGrid)
                    .sorted(Comparator.comparing(binding -> binding.providerGrid().getService(
                            space.controlnet.ae2federation.identity.NetworkIdentityService.class).settlement()
                            .networkId().orElseThrow().toString()))
                    .toList();
            var extracted = 0.0;
            for (var binding : candidates) {
                var accepted = binding.extract(amount - extracted, mode);
                extracted += accepted;
                if (outermost && mode == Actionable.MODULATE && accepted > 0) {
                    LevelObservabilityService.get(level).recordAccepted(binding.revision().federationDomains(),
                            space.controlnet.ae2federation.observability.meter.OperationEventId.create(), "ae2:energy",
                            nanoAe(accepted), ResourceUnit.NANO_AE,
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

    static long nanoAe(double amount) {
        if (!Double.isFinite(amount) || amount < 0) {
            throw new ArithmeticException("Energy amount is not exactly representable");
        }
        return java.math.BigDecimal.valueOf(amount).movePointRight(9).longValueExact();
    }

    @Override
    public void close() {
        withdrawals += bindings.size();
        bindings.clear();
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
        } catch (IllegalStateException exception) {
            remove(relationship.key());
            return;
        }
        var policies = PolicyService.get(level);
        if (policies.activation(relationship.key(), new PolicyRuntimeEndpoints(relationship.consumerGrid(),
                relationship.providerGrid(), BackendStatus.READY)) != PolicyActivationState.ACTIVE) {
            remove(relationship.key());
            return;
        }
        var references = federationDomains.references(relationship);
        var source = selectSource(relationship.consumerGrid());
        if (references.isEmpty() || source == null) {
            remove(relationship.key());
            return;
        }
        var configured = policies.configured(relationship.key()).orElseThrow();
        var revision = new EnergyBindingRevision(configured.revision(), federationDomains.topologyRevision(), references,
                backend.generation());
        var active = bindings.get(relationship.key());
        if (active != null && active.consumerGrid() == relationship.consumerGrid()
                && active.providerGrid() == relationship.providerGrid() && active.providerService() == backend.service()
                && active.consumerSource() == source && active.revision().equals(revision)) {
            return;
        }
        remove(relationship.key());
        var holder = new EnergyCapabilityBinding[1];
        var binding = new EnergyCapabilityBinding(relationship, backend, revision, source,
                () -> current(holder[0], backend));
        holder[0] = binding;
        bindings.put(relationship.key(), binding);
        publications++;
        if (demandDepth == 0) {
            source.announceAvailability();
        }
    }

    private boolean current(EnergyCapabilityBinding binding, NativeEnergyBackend backend) {
        if (binding == null || bindings.get(binding.key()) != binding) {
            return false;
        }
        if (federationDomains.topologyRevision() != binding.revision().topologyRevision()
                || !binding.revision().providerGeneration().equals(backend.generation())
                || !backends.isCurrent(backend)) {
            remove(binding.key());
            reconcileAll();
            return false;
        }
        var registry = FederationDomainRegistryAccess.get(level);
        if (binding.revision().federationDomains().stream().noneMatch(registry::isCurrent)) {
            remove(binding.key());
            return false;
        }
        var policies = PolicyService.get(level);
        var key = binding.key();
        var configured = policies.configured(key).orElse(null);
        var authorized = configured != null && configured.revision().equals(binding.revision().policyRevision())
                && configured.rule().enabled() && configured.rule().operations().contains(PolicyOperation.SUPPLY)
                && policies.activation(key, new PolicyRuntimeEndpoints(binding.consumerGrid(), binding.providerGrid(),
                        BackendStatus.READY)) == PolicyActivationState.ACTIVE;
        if (!authorized) {
            remove(binding.key());
        }
        return authorized;
    }

    @Nullable
    private static DirectionalEnergySource selectSource(IGrid consumerGrid) {
        var candidates = new ArrayList<DirectionalEnergySource>();
        for (var node : consumerGrid.getNodes()) {
            var storage = node.getService(IAEPowerStorage.class);
            if (storage instanceof DirectionalEnergySource source && source.node() == node) {
                candidates.add(source);
            }
        }
        return candidates.stream().min(Comparator.comparingInt(System::identityHashCode)).orElse(null);
    }

    private void remove(PolicyKey key) {
        if (bindings.remove(key) != null) {
            withdrawals++;
        }
    }

    public record CloseReceipt(boolean servicePresent, int bindingsWithdrawn) {
    }
}
