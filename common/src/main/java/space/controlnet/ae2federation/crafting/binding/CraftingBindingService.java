package space.controlnet.ae2federation.crafting.binding;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingRequester;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkIdentityService;
import space.controlnet.ae2federation.policy.BackendStatus;
import space.controlnet.ae2federation.policy.PolicyActivationState;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRuntimeEndpoints;
import space.controlnet.ae2federation.policy.PolicyService;

public final class CraftingBindingService implements AutoCloseable {
    private static final Map<ServerLevel, CraftingBindingService> SERVICES = new WeakHashMap<>();

    private final ServerLevel level;
    private final CraftingFederationDomainObserver federationDomains;
    private final NativeCraftingBackendRegistry backends = new NativeCraftingBackendRegistry();
    private final Map<PolicyKey, CraftingCapabilityBinding> bindings = new java.util.HashMap<>();
    private final NativeCraftingRequestRegistry nativeRequests = new NativeCraftingRequestRegistry();
    private final Map<PolicyKey, space.controlnet.ae2federation.policy.BindingDiagnostic> diagnostics = new java.util.HashMap<>();
    private int publications;
    private int withdrawals;

    private CraftingBindingService(ServerLevel level) {
        this.level = level;
        federationDomains = new CraftingFederationDomainObserver(level);
    }

    public static synchronized CraftingBindingService get(ServerLevel level) {
        return SERVICES.computeIfAbsent(level, CraftingBindingService::new);
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
                FederationDomainRegistryAccess.get(level).snapshot().topologyRevision())
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

    public static synchronized void topologyChangedIfPresent(ServerLevel level) {
        reconcileIfPresent(level);
    }

    public static synchronized CloseReceipt closeLevel(ServerLevel level) {
        var service = SERVICES.remove(level);
        var active = service == null ? 0 : service.bindings.size();
        if (service != null) {
            service.nativeRequests.retireTerminal();
        }
        var nativeRequests = service == null ? 0 : service.nativeRequests.requestCount();
        if (service != null) {
            service.close();
        }
        return new CloseReceipt(service != null, active, nativeRequests);
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
        diagnostics.clear();
        nativeRequests.retireTerminal();
        var desired = federationDomains.relationships();
        var policies = PolicyService.get(level);
        var eligible = new HashSet<PolicyKey>();
        for (var key : desired.keySet()) {
            var configured = policies.configured(key).orElse(null);
            if (configured != null && configured.rule().enabled()
                    && configured.rule().operations().contains(PolicyOperation.REQUEST)) {
                eligible.add(key);
            }
        }
        var cyclic = CraftingDependencyCycleGuard.cyclicKeys(eligible);
        cyclic.forEach(key -> recordDiagnostic(key,
                space.controlnet.ae2federation.policy.BindingDiagnostic.Reason.CRAFTING_CYCLE));
        List.copyOf(bindings.keySet()).stream()
                .filter(key -> !eligible.contains(key) || cyclic.contains(key))
                .forEach(this::remove);
        desired.values().stream()
                .filter(relationship -> eligible.contains(relationship.key()) && !cyclic.contains(relationship.key()))
                .forEach(this::reconcile);
    }

    public Optional<CraftingCapabilityBinding> capability(PolicyKey key) {
        reconcileAll();
        var binding = bindings.get(key);
        return binding != null && binding.isCurrent() ? Optional.of(binding) : Optional.empty();
    }

    public boolean submissionAuthorityCurrent(CraftingSubmissionSnapshot snapshot) {
        var binding = snapshot.binding();
        if (bindings.get(binding.relationship().key()) != binding
                || binding.relationship().providerGrid() != snapshot.sourceGrid()
                || snapshot.sourceGrid().getCraftingService() != snapshot.service()
                || federationDomains.topologyRevision() != binding.revision().topologyRevision()) {
            return false;
        }
        var registry = FederationDomainRegistryAccess.get(level);
        if (binding.revision().federationDomains().stream().noneMatch(registry::isCurrent)) {
            return false;
        }
        var policies = PolicyService.get(level);
        var configured = policies.configured(binding.relationship().key()).orElse(null);
        if (configured == null || !configured.revision().equals(binding.revision().policyRevision())
                || !configured.rule().enabled()
                || !configured.rule().operations().contains(PolicyOperation.REQUEST)
                || policies.activation(binding.relationship().key(), new PolicyRuntimeEndpoints(
                        binding.relationship().consumerGrid(), binding.relationship().providerGrid(), BackendStatus.READY))
                        != PolicyActivationState.ACTIVE) {
            return false;
        }
        var identity = snapshot.sourceGrid().getService(
                space.controlnet.ae2federation.identity.NetworkIdentityService.class);
        var currentProviders = new java.util.ArrayList<NativeCraftingProviderSource>();
        for (var node : snapshot.sourceGrid().getNodes()) {
            var provider = node.getService(ICraftingProvider.class);
            if (provider != null && node.isActive() && node.hasGridBooted()
                    && node.getGrid() == snapshot.sourceGrid()) {
                currentProviders.add(new NativeCraftingProviderSource(identity.lineage(node).nodeId(), node, provider));
            }
        }
        currentProviders.sort((left, right) -> left.registrationNodeId().toString()
                .compareTo(right.registrationNodeId().toString()));
        if (currentProviders.size() != snapshot.providerSources().size()) {
            return false;
        }
        for (var index = 0; index < currentProviders.size(); index++) {
            var current = currentProviders.get(index);
            var captured = snapshot.providerSources().get(index);
            if (!current.registrationNodeId().equals(captured.registrationNodeId())
                    || current.node() != captured.node() || current.provider() != captured.provider()) {
                return false;
            }
        }
        return true;
    }

    public Optional<NativeCraftingRequestBinding> synchronizeNativeRequest(CraftingSubmissionSnapshot snapshot,
            IGridNode requesterNode, int slot, ICraftingRequester requester, ICraftingLink link) {
        if (requesterNode.getGrid() != snapshot.sourceGrid()
                || requester.getActionableNode() != requesterNode) {
            return Optional.empty();
        }
        var lineage = snapshot.sourceGrid().getService(NetworkIdentityService.class).lineage(requesterNode);
        var key = new NativeCraftingRequestKey(snapshot.binding().relationship().key(), lineage.nodeId(), slot);
        return nativeRequests.synchronize(key, requester, link, submissionAuthorityCurrent(snapshot));
    }

    public int retireNativeRequester(ICraftingRequester requester) {
        var node = requester.getActionableNode();
        return node == null || !node.isActive() ? nativeRequests.retireRequester(requester) : 0;
    }

    public Optional<NativeCraftingRequestBinding> nativeRequest(NativeCraftingRequestKey key) {
        return nativeRequests.get(key);
    }

    public int nativeRequestCount() {
        nativeRequests.retireTerminal();
        return nativeRequests.requestCount();
    }

    public int nativeLinkOwnerCount() {
        nativeRequests.retireTerminal();
        return nativeRequests.linkOwnerCount();
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
        diagnostics.clear();
        withdrawals += bindings.size();
        bindings.clear();
        nativeRequests.close();
        backends.clear();
        federationDomains.clear();
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
            recordDiagnostic(relationship.key(), exception.reason());
            remove(relationship.key());
            return;
        }
        var activation = policies.activation(relationship.key(), new PolicyRuntimeEndpoints(relationship.consumerGrid(),
                relationship.providerGrid(), BackendStatus.READY));
        if (activation != PolicyActivationState.ACTIVE) {
            recordDiagnostic(relationship.key(), space.controlnet.ae2federation.policy.BindingDiagnostic.inactiveReason(activation));
            remove(relationship.key());
            return;
        }
        var references = federationDomains.references(relationship);
        if (references.isEmpty()) {
            recordDiagnostic(relationship.key(), space.controlnet.ae2federation.policy.BindingDiagnostic.Reason.DOMAIN_REFERENCE_MISSING);
            remove(relationship.key());
            return;
        }
        var revision = new CraftingBindingRevision(configured.revision(), federationDomains.topologyRevision(), references,
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
                || federationDomains.topologyRevision() != binding.revision().topologyRevision()
                || !binding.revision().providerGeneration().equals(backend.generation())
                || !backends.isCurrent(backend)) {
            return false;
        }
        var registry = FederationDomainRegistryAccess.get(level);
        if (binding.revision().federationDomains().stream().noneMatch(registry::isCurrent)) {
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

    public record CloseReceipt(boolean servicePresent, int bindingsWithdrawn, int nativeRequestsRetired) {
    }
}
