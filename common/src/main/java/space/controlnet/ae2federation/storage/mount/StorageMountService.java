package space.controlnet.ae2federation.storage.mount;

import appeng.api.networking.IGrid;
import appeng.api.networking.storage.IStorageService;
import appeng.api.storage.MEStorage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.policy.AuthorityEpoch;
import space.controlnet.ae2federation.policy.BindingDiagnostic;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.storage.dependency.EffectiveSourceRelationship;
import space.controlnet.ae2federation.storage.dependency.EffectiveSourceRelationshipKey;
import space.controlnet.ae2federation.storage.dependency.EffectiveStorageAuthority;
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
    /** Advances before every change to {@link #mounts} or {@link #mountGenerations}. */
    private long mountsRevision;
    /** The rules some current mount depends on, directly or as a re-export hop; replaced whole when mounts change. */
    private Set<PolicyKey> inEffect = Set.of();
    private final NativeSourceDomainRegistry provenance = new NativeSourceDomainRegistry();
    private final StorageFederationDomainObserver federationDomains;
    private final StorageDependencyIndex dependencies;
    private final StorageSubscriptionService subscriptions;
    private final StorageSubscriptionPlanner subscriptionPlanner;
    private final LevelObservabilityService observability;
    private int removedProviderCount;
    private long sourceValidations;

    private StorageMountService(ServerLevel level) {
        subscriptions = new StorageSubscriptionService(level::getGameTime);
        federationDomains = new StorageFederationDomainObserver(level);
        dependencies = new StorageDependencyIndex(level, federationDomains, provenance);
        subscriptionPlanner = new StorageSubscriptionPlanner(dependencies, subscriptions);
        observability = LevelObservabilityService.get(level);
    }

    public static synchronized StorageMountService get(ServerLevel level) {
        return SERVICES.computeIfAbsent(level, StorageMountService::new);
    }

    /**
     * A storage rule's state for the pair editor: in effect, or why it shares nothing, and the provider network's last
     * source diagnostic. {@code skippedSources} counts the provider's storages that are not shared.
     */
    public record Status(boolean inEffect, Optional<BindingDiagnostic.Reason> reason,
            Optional<ProvenanceDiagnostic> source, int skippedSources) {
    }

    /**
     * {@code key}'s enabled rule as of the last reconciliation; empty while this level has none. Does not create a
     * service, reconcile, or authorize an operation.
     */
    public static synchronized Optional<Status> status(ServerLevel level, PolicyKey key) {
        var service = SERVICES.get(level);
        return service == null ? Optional.empty() : Optional.of(service.status(key));
    }

    private Status status(PolicyKey key) {
        var source = Optional.ofNullable(dependencies.diagnostic(key));
        var skipped = dependencies.skippedSources(key);
        return inEffect.contains(key) ? new Status(true, Optional.empty(), source, skipped)
                : new Status(false, dependencies.reason(key), source, skipped);
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
        var desiredPolicies = new java.util.HashSet<PolicyKey>();
        desired.keySet().forEach(effective -> desiredPolicies.add(effective.policyKey()));
        List.copyOf(mounts.keySet()).stream()
                .filter(key -> !desiredPolicies.contains(key))
                .forEach(this::removeMount);
        desired.values().forEach(this::reconcileEffective);
        subscriptionPlanner.reconcile(mounts, mountGenerations);
        refreshInEffect();
    }

    private void refreshInEffect() {
        var next = new java.util.HashSet<PolicyKey>();
        for (var mounted : mounts.values()) {
            next.add(mounted.relationship().key());
            var effective = dependencies.relationship(mounted.effectiveKey());
            if (effective != null) next.addAll(effective.revision().policyRevisions().keySet());
        }
        inEffect = Set.copyOf(next);
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
        refreshInEffect();
    }

    private void removeMount(PolicyKey key) {
        mountsRevision++;
        AuthorityEpoch.advance();
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
        AuthorityEpoch.advance();
        var delegate = StorageMountHelpers.aggregate(domain.sources());
        var holder = new MountedStorageRelationship[1];
        var check = new StorageDependencyIndex.CurrentCheck();
        var authority = new StorageRelationshipAuthority(() -> dependencies.relationship(effective.key(), check),
                () -> readyAuthority(effective.key(), domain, holder[0], check));
        var projection = new AuthorizedStorageProjection(delegate, authority,
                operation -> {
                    observability.recordAcceptedStorage(authority.scopes(), operation);
                    observability.recordPairFlow(key, operation.amount());
                });
        var priority = domain.sources().stream().mapToInt(source -> source.priority()).max().orElse(0);
        var provider = new RelationshipStorageProvider(projection, priority);
        var next = new MountedStorageRelationship(relationship, domain,
                StorageMountHelpers.nextGeneration(mountGenerations, key), provider, effective.key());
        holder[0] = next;
        consumer.getService(IStorageService.class).addGlobalStorageProvider(provider);
        mounts.put(key, next);
        mountsRevision++;
    }

    /**
     * The mounted relationship's authority while its source is ready and the relationship is current, else null.
     * While nothing the last passing check read on the Federation side changed ({@link AuthorityEpoch}), only the
     * source Grid's native state is read again, which is what that check would find different.
     */
    private @Nullable EffectiveStorageAuthority readyAuthority(EffectiveSourceRelationshipKey key,
            NativeSourceDomain domain, @Nullable MountedStorageRelationship mounted,
            StorageDependencyIndex.CurrentCheck check) {
        var still = check.stillAuthorized(provenance);
        if (still != null) {
            sourceValidations++;
            return still;
        }
        var epoch = AuthorityEpoch.current();
        EffectiveStorageAuthority authority = null;
        if (mounted != null && sourceCurrent(mounted, check)) {
            var candidate = dependencies.relationship(key, check);
            if (candidate != null && dependencies.current(candidate, domain, check)) {
                authority = candidate.authority();
            }
        }
        check.authorized(epoch, authority);
        return authority;
    }

    /**
     * The mount's provider Grid is its domain's runtime Grid, so {@link StorageDependencyIndex#sourceCurrent} covers
     * the source nodes' readiness on that Grid.
     */
    private boolean sourceCurrent(MountedStorageRelationship mounted, StorageDependencyIndex.CurrentCheck check) {
        sourceValidations++;
        // Neither map changed since this mount last found itself in both, so it still would.
        if (check.mountsRevision != mountsRevision) {
            if (mounts.get(mounted.relationship().key()) != mounted
                    || !mounted.generation().equals(mountGenerations.get(mounted.relationship().key()))) {
                removeIfCurrent(mounted);
                return false;
            }
            check.mountsRevision = mountsRevision;
        }
        if (!dependencies.sourceCurrent(mounted.domain(), check)) {
            removeIfCurrent(mounted);
            return false;
        }
        return true;
    }

    private int closeState() {
        var removed = mounts.size();
        mountsRevision++;
        AuthorityEpoch.advance();
        subscriptions.close();
        List.copyOf(mounts.values()).forEach(mounted -> mounted.relationship().consumerGrid()
                .getService(IStorageService.class).removeGlobalStorageProvider(mounted.provider()));
        mounts.clear();
        inEffect = Set.of();
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
            refreshInEffect();
        }
    }

}
