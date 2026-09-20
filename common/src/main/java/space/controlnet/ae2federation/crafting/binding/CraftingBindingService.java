package space.controlnet.ae2federation.crafting.binding;

import appeng.api.networking.IGrid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.policy.BackendStatus;
import space.controlnet.ae2federation.policy.PolicyActivationState;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRuntimeEndpoints;
import space.controlnet.ae2federation.policy.PolicyService;

public final class CraftingBindingService implements AutoCloseable {
    private static final Map<ServerLevel, CraftingBindingService> SERVICES = new WeakHashMap<>();

    private final ServerLevel level;
    private final CraftingFabricObserver fabrics;
    private final NativeCraftingBackendRegistry backends = new NativeCraftingBackendRegistry();
    private final Map<PolicyKey, CraftingCapabilityBinding> bindings = new HashMap<>();
    private int publications;
    private int withdrawals;

    private CraftingBindingService(ServerLevel level) {
        this.level = level;
        fabrics = new CraftingFabricObserver(level);
    }

    public static synchronized CraftingBindingService get(ServerLevel level) {
        return SERVICES.computeIfAbsent(level, CraftingBindingService::new);
    }

    public static synchronized void reconcileIfPresent(ServerLevel level) {
        var service = SERVICES.get(level);
        if (service != null) {
            service.reconcileAll();
        }
    }

    public static synchronized void topologyChangedIfPresent(ServerLevel level) {
        reconcileIfPresent(level);
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
        fabrics.register(first);
        fabrics.register(second);
        reconcileAll();
    }

    public void observeFabricMembers(Iterable<IGrid> grids) {
        fabrics.register(grids);
        reconcileAll();
    }

    public void reconcileAll() {
        var desired = fabrics.relationships();
        List.copyOf(bindings.keySet()).stream().filter(key -> !desired.containsKey(key)).forEach(this::remove);
        desired.values().forEach(this::reconcile);
    }

    public Optional<CraftingCapabilityBinding> capability(PolicyKey key) {
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

    @Override
    public void close() {
        withdrawals += bindings.size();
        bindings.clear();
        backends.clear();
        fabrics.clear();
    }

    private void reconcile(CraftingRelationship relationship) {
        var policies = PolicyService.get(level);
        var configured = policies.configured(relationship.key()).orElse(null);
        if (configured == null || !configured.rule().enabled()
                || !configured.rule().operations().contains(PolicyOperation.REQUEST)) {
            remove(relationship.key());
            return;
        }
        NativeCraftingBackend backend;
        try {
            backend = backends.discover(relationship.providerGrid());
        } catch (CraftingBackendUnavailableException exception) {
            remove(relationship.key());
            return;
        }
        if (policies.activation(relationship.key(), new PolicyRuntimeEndpoints(relationship.consumerGrid(),
                relationship.providerGrid(), BackendStatus.READY)) != PolicyActivationState.ACTIVE) {
            remove(relationship.key());
            return;
        }
        var references = fabrics.references(relationship);
        if (references.isEmpty()) {
            remove(relationship.key());
            return;
        }
        var revision = new CraftingBindingRevision(configured.revision(), fabrics.topologyRevision(), references,
                backend.generation());
        var active = bindings.get(relationship.key());
        if (active != null && active.relationship().consumerGrid() == relationship.consumerGrid()
                && active.relationship().providerGrid() == relationship.providerGrid()
                && active.revision().equals(revision) && active.nativeService().orElse(null) == backend.service()) {
            return;
        }
        remove(relationship.key());
        var holder = new CraftingCapabilityBinding[1];
        var binding = new CraftingCapabilityBinding(relationship, backend, revision,
                () -> current(holder[0], backend));
        holder[0] = binding;
        bindings.put(relationship.key(), binding);
        publications++;
    }

    private boolean current(CraftingCapabilityBinding binding, NativeCraftingBackend backend) {
        if (binding == null || bindings.get(binding.relationship().key()) != binding
                || fabrics.topologyRevision() != binding.revision().topologyRevision()
                || !binding.revision().providerGeneration().equals(backend.generation())
                || !backends.isCurrent(backend)) {
            return false;
        }
        var registry = FabricRegistryAccess.get(level);
        if (binding.revision().fabrics().stream().noneMatch(registry::isCurrent)) {
            return false;
        }
        var policies = PolicyService.get(level);
        var configured = policies.configured(binding.relationship().key()).orElse(null);
        return configured != null && configured.revision().equals(binding.revision().policyRevision())
                && configured.rule().enabled() && configured.rule().operations().contains(PolicyOperation.REQUEST)
                && policies.activation(binding.relationship().key(), new PolicyRuntimeEndpoints(
                        binding.relationship().consumerGrid(), binding.relationship().providerGrid(), BackendStatus.READY))
                        == PolicyActivationState.ACTIVE;
    }

    private void remove(PolicyKey key) {
        if (bindings.remove(key) != null) {
            withdrawals++;
        }
    }

    public record CloseReceipt(boolean servicePresent, int bindingsWithdrawn) {
    }
}
