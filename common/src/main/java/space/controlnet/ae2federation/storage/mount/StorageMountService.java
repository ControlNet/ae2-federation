package space.controlnet.ae2federation.storage.mount;

import appeng.api.networking.IGrid;
import appeng.api.networking.storage.IStorageService;
import appeng.api.storage.MEStorage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.storage.dependency.EffectiveSourceRelationship;
import space.controlnet.ae2federation.storage.provenance.MountGeneration;
import space.controlnet.ae2federation.storage.provenance.NativeSourceDomain;
import space.controlnet.ae2federation.storage.provenance.NativeSourceDomainRegistry;
import space.controlnet.ae2federation.storage.provenance.ProvenanceDiagnostic;
import space.controlnet.ae2federation.storage.subscription.SourceSubscriptionKey;
import space.controlnet.ae2federation.storage.subscription.StorageSubscriptionService;
import space.controlnet.ae2federation.observability.LevelObservabilityService;

public final class StorageMountService implements AutoCloseable {
    private static final Map<ServerLevel, StorageMountService> SERVICES = new WeakHashMap<>();
    private final Map<PolicyKey, MountedStorageRelationship> mounts = new HashMap<>();
    private final Map<PolicyKey, MountGeneration> mountGenerations = new HashMap<>();
    private final NativeSourceDomainRegistry provenance = new NativeSourceDomainRegistry();
    private final StorageFederationDomainObserver federationDomains;
    private final StorageDependencyIndex dependencies;
    private final StorageSubscriptionService subscriptions = new StorageSubscriptionService();
    private final StorageSubscriptionPlanner subscriptionPlanner;
    private final LevelObservabilityService observability;
    private int removedProviderCount;
    private long sourceValidations;

    private StorageMountService(ServerLevel level) {
        federationDomains = new StorageFederationDomainObserver(level);
        dependencies = new StorageDependencyIndex(level, federationDomains, provenance);
        subscriptionPlanner = new StorageSubscriptionPlanner(dependencies, subscriptions);
        observability = LevelObservabilityService.get(level);
    }

    public static synchronized StorageMountService get(ServerLevel level) {
        return SERVICES.computeIfAbsent(level, StorageMountService::new);
    }

    public static synchronized void reconcileIfPresent(ServerLevel level) {
        var service = SERVICES.get(level);
        if (service != null) service.reconcileAll();
    }

    public static synchronized void topologyChangedIfPresent(ServerLevel level) {
        reconcileIfPresent(level);
    }

    /**
     * Called at most once per tick per AE2 StorageService whose real mount table changed (drive/chest cell swap,
     * priority change, node join/leave, global provider add/remove). Held projections are already fail-closed by the
     * per-operation stamp check; this re-establishes relationships against the new native mounts without polling.
     */
    public static synchronized void nativeMountsChanged(IStorageService service) {
        for (var entry : List.copyOf(SERVICES.values())) {
            if (entry.observesStorageService(service)) {
                entry.reconcileAll();
            }
        }
    }

    private boolean observesStorageService(IStorageService service) {
        for (var grid : federationDomains.loadedGrids().values()) {
            if (grid.getStorageService() == service) {
                return true;
            }
        }
        return false;
    }

    /** Full source-domain rebuilds performed by this level's source index. Diagnostic counter only. */
    public long sourceDiscoveryRebuilds() {
        return provenance.discoveryRebuilds();
    }

    /** Discoveries answered by the stamp-validated source index cache. Diagnostic counter only. */
    public long sourceDiscoveryCacheHits() {
        return provenance.cachedDiscoveries();
    }

    /** Provider mount-table entries visited by rebuilds (Federation never scans Grid nodes). Diagnostic only. */
    public long sourceProviderScans() {
        return provenance.providerScans();
    }

    /** Per-operation/per-enumeration source validity evaluations of held projections. Diagnostic counter only. */
    public long sourceValidationCount() {
        return sourceValidations;
    }

    static synchronized StorageMountLevelCloseResult closeLevel(ServerLevel level) {
        var registered = SERVICES.get(level);
        var removed = SERVICES.remove(level);
        var mountedProvidersBefore = removed == null ? 0 : removed.mounts.size();
        var mountedProvidersRemoved = removed == null ? 0 : removed.closeState();
        return new StorageMountLevelCloseResult(registered != null, mountedProvidersBefore, mountedProvidersRemoved,
                registered != null && removed == registered && !SERVICES.containsKey(level));
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

    public StorageMountState observe(StorageRelationship relationship) {
        federationDomains.register(relationship.consumerGrid());
        federationDomains.register(relationship.providerGrid());
        return reconcile(relationship);
    }

    public StorageMountState reconcile(StorageRelationship relationship) {
        var previous = mounts.get(relationship.key());
        reconcileAll();
        var current = mounts.get(relationship.key());
        if (current == null) return StorageMountState.INACTIVE;
        return current == previous ? StorageMountState.UNCHANGED : StorageMountState.MOUNTED;
    }

    public void reconcileAll() {
        dependencies.refresh();
        var desired = dependencies.relationships();
        List.copyOf(mounts.keySet()).stream()
                .filter(key -> desired.keySet().stream().noneMatch(effective -> effective.policyKey().equals(key)))
                .forEach(this::removeMount);
        desired.values().forEach(this::reconcileEffective);
        subscriptionPlanner.reconcile(mounts, mountGenerations);
    }

    public int mountedRelationshipCount() {
        return mounts.size();
    }

    public MEStorage projection(PolicyKey key) {
        var mounted = mounts.get(key);
        if (mounted == null) return null;
        var effective = dependencies.relationship(mounted.effectiveKey());
        return effective != null && effective.minimumDepth() == 1 ? mounted.provider().projection() : null;
    }

    public MEStorage effectiveProjection(PolicyKey key) {
        var mounted = mounts.get(key);
        return mounted == null ? null : mounted.provider().projection();
    }

    public EffectiveSourceRelationship effectiveRelationship(PolicyKey key) {
        return dependencies.relationship(key);
    }

    public int dependencyFrontierRelaxations() {
        return dependencies.frontierRelaxations();
    }

    public int rejectedOriginCycles() {
        return dependencies.originCycleRejections();
    }

    public long dependencyRefreshCount() {
        return dependencies.refreshCount();
    }

    public int activeSubscriptionCount() {
        return subscriptions.activeListenerCount();
    }

    public int subscriptionRegistrationCount() {
        return subscriptions.listenerRegistrationCount();
    }

    public int subscriptionRemovalCount() {
        return subscriptions.listenerRemovalCount();
    }

    public long sourceEventCount() {
        return subscriptions.sourceEventCount();
    }

    public long consumerDeliveryCount() {
        return subscriptions.consumerDeliveryCount();
    }

    public long consumerDeliveryCount(PolicyKey key) {
        var mounted = mounts.get(key);
        return mounted == null ? 0 : subscriptions.consumerDeliveryCount(mounted.effectiveKey());
    }

    public SourceSubscriptionKey subscriptionKey(PolicyKey key) {
        var mounted = mounts.get(key);
        if (mounted == null || mounted.domain().sources().isEmpty()) return null;
        var source = mounted.domain().sources().getFirst();
        return new SourceSubscriptionKey(source.id(), source.generation());
    }

    public long subscriptionEventVersion(PolicyKey key) {
        var subscriptionKey = subscriptionKey(key);
        return subscriptionKey == null ? 0 : subscriptions.eventVersion(subscriptionKey);
    }

    public long subscriptionSnapshotVersion(PolicyKey key) {
        var subscriptionKey = subscriptionKey(key);
        return subscriptionKey == null ? 0 : subscriptions.snapshotVersion(subscriptionKey);
    }

    public long subscriptionRegistrationId(PolicyKey key) {
        var subscriptionKey = subscriptionKey(key);
        return subscriptionKey == null ? 0 : subscriptions.registrationId(subscriptionKey);
    }

    public long subscriptionRegistrationId(MEStorage source) {
        return subscriptions.registrationId(source);
    }

    public void resetSubscription(PolicyKey key) {
        var subscriptionKey = subscriptionKey(key);
        if (subscriptionKey != null) subscriptions.reset(subscriptionKey);
    }

    public Integer mountedPriority(PolicyKey key) {
        var mounted = mounts.get(key);
        return mounted == null ? null : mounted.provider().priority();
    }

    public NativeSourceDomain sourceDomain(PolicyKey key) {
        var mounted = mounts.get(key);
        return mounted == null ? null : mounted.domain();
    }

    public MountGeneration mountGeneration(PolicyKey key) {
        var mounted = mounts.get(key);
        return mounted == null ? null : mounted.generation();
    }

    public int removedProviderCount() {
        return removedProviderCount;
    }

    public ProvenanceDiagnostic lastDiagnostic(PolicyKey key) {
        return dependencies.diagnostic(key);
    }

    public void remove(PolicyKey key) {
        removeMount(key);
        subscriptionPlanner.reconcile(mounts, mountGenerations);
    }

    private void removeMount(PolicyKey key) {
        var mounted = mounts.remove(key);
        if (mounted != null) {
            StorageMountHelpers.nextGeneration(mountGenerations, key);
            mounted.relationship().consumerGrid().getService(IStorageService.class)
                    .removeGlobalStorageProvider(mounted.provider());
            removedProviderCount++;
        }
    }

    @Override
    public void close() {
        closeState();
    }

    private void reconcileEffective(EffectiveSourceRelationship effective) {
        var key = effective.key().policyKey();
        var domain = dependencies.domain(effective.key().origin());
        var consumer = dependencies.grid(effective.key().consumerNetworkId());
        if (domain == null || consumer == null) {
            remove(key);
            return;
        }
        var relationship = new StorageRelationship(key, consumer, domain.runtimeGrid());
        var mounted = mounts.get(key);
        if (mounted != null && mounted.relationship().consumerGrid() == consumer
                && mounted.relationship().providerGrid() == domain.runtimeGrid() && mounted.domain() == domain) {
            return;
        }
        removeMount(key);
        var delegate = StorageMountHelpers.aggregate(domain.sources());
        var holder = new MountedStorageRelationship[1];
        var authority = new StorageRelationshipAuthority(() -> dependencies.relationship(effective.key()),
                candidate -> dependencies.current(candidate, domain),
                () -> holder[0] != null && sourceCurrent(holder[0]));
        var projection = new AuthorizedStorageProjection(delegate, authority,
                operation -> observability.recordAcceptedStorage(authority.scopes(), operation));
        var priority = domain.sources().stream().mapToInt(source -> source.priority()).max().orElse(0);
        var provider = new RelationshipStorageProvider(projection, priority);
        var next = new MountedStorageRelationship(relationship, domain,
                StorageMountHelpers.nextGeneration(mountGenerations, key), provider, effective.key());
        holder[0] = next;
        consumer.getService(IStorageService.class).addGlobalStorageProvider(provider);
        mounts.put(key, next);
    }

    private boolean sourceCurrent(MountedStorageRelationship mounted) {
        sourceValidations++;
        if (mounts.get(mounted.relationship().key()) != mounted
                || !mounted.generation().equals(mountGenerations.get(mounted.relationship().key()))
                || !mounted.sourceReady() || !dependencies.sourceCurrent(mounted.domain())) {
            removeIfCurrent(mounted);
            return false;
        }
        return true;
    }

    private int closeState() {
        var removed = mounts.size();
        subscriptions.close();
        List.copyOf(mounts.values()).forEach(mounted -> mounted.relationship().consumerGrid()
                .getService(IStorageService.class).removeGlobalStorageProvider(mounted.provider()));
        mounts.clear();
        removedProviderCount += removed;
        mountGenerations.clear();
        dependencies.clear();
        provenance.clear();
        federationDomains.clear();
        return removed;
    }

    private void removeIfCurrent(MountedStorageRelationship mounted) {
        if (mounts.get(mounted.relationship().key()) == mounted) {
            removeMount(mounted.relationship().key());
            subscriptionPlanner.reconcile(mounts, mountGenerations);
        }
    }

}
