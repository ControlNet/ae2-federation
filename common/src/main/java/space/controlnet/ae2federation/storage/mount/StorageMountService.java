package space.controlnet.ae2federation.storage.mount;

import appeng.api.networking.IGrid;
import appeng.api.networking.storage.IStorageService;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.storage.dependency.EffectiveSourceRelationship;
import space.controlnet.ae2federation.storage.dependency.EffectiveSourceRelationshipKey;
import space.controlnet.ae2federation.storage.provenance.ExportSource;
import space.controlnet.ae2federation.storage.provenance.MountGeneration;
import space.controlnet.ae2federation.storage.provenance.NativeSourceDomain;
import space.controlnet.ae2federation.storage.provenance.NativeSourceDomainRegistry;
import space.controlnet.ae2federation.storage.provenance.ProvenanceDiagnostic;

public final class StorageMountService implements AutoCloseable {
    private static final Map<ServerLevel, StorageMountService> SERVICES = new WeakHashMap<>();
    private final Map<PolicyKey, MountedStorageRelationship> mounts = new HashMap<>();
    private final Map<PolicyKey, MountGeneration> mountGenerations = new HashMap<>();
    private final NativeSourceDomainRegistry provenance = new NativeSourceDomainRegistry();
    private final StorageFabricObserver fabrics;
    private final StorageDependencyIndex dependencies;
    private int removedProviderCount;

    private StorageMountService(ServerLevel level) {
        fabrics = new StorageFabricObserver(level);
        dependencies = new StorageDependencyIndex(level, fabrics, provenance);
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

    static synchronized LevelCloseResult closeLevel(ServerLevel level) {
        var registered = SERVICES.get(level);
        var removed = SERVICES.remove(level);
        var mountedProvidersBefore = removed == null ? 0 : removed.mounts.size();
        var mountedProvidersRemoved = removed == null ? 0 : removed.closeState();
        return new LevelCloseResult(registered != null, mountedProvidersBefore, mountedProvidersRemoved,
                registered != null && removed == registered && !SERVICES.containsKey(level));
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

    public StorageMountState observe(StorageRelationship relationship) {
        fabrics.register(relationship.consumerGrid());
        fabrics.register(relationship.providerGrid());
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
                .forEach(this::remove);
        desired.values().forEach(this::reconcileEffective);
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
        var mounted = mounts.remove(key);
        if (mounted != null) {
            nextMountGeneration(key);
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
        remove(key);
        var delegate = aggregate(domain.sources());
        var holder = new MountedStorageRelationship[1];
        var authority = new StorageRelationshipAuthority(() -> dependencies.relationship(effective.key()),
                candidate -> dependencies.current(candidate, domain),
                () -> holder[0] != null && sourceCurrent(holder[0]));
        var projection = new AuthorizedStorageProjection(delegate, authority);
        var priority = domain.sources().stream().mapToInt(ExportSource::priority).max().orElse(0);
        var provider = new RelationshipStorageProvider(projection, priority);
        var next = new MountedStorageRelationship(relationship, domain, nextMountGeneration(key), provider, effective.key());
        holder[0] = next;
        consumer.getService(IStorageService.class).addGlobalStorageProvider(provider);
        mounts.put(key, next);
    }

    private boolean sourceCurrent(MountedStorageRelationship mounted) {
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
        List.copyOf(mounts.values()).forEach(mounted -> mounted.relationship().consumerGrid()
                .getService(IStorageService.class).removeGlobalStorageProvider(mounted.provider()));
        mounts.clear();
        removedProviderCount += removed;
        mountGenerations.clear();
        dependencies.clear();
        provenance.clear();
        fabrics.clear();
        return removed;
    }

    private static MEStorage aggregate(List<ExportSource> sources) {
        if (sources.size() == 1) return sources.getFirst().storage();
        var aggregate = new NetworkStorage();
        sources.forEach(source -> aggregate.mount(source.priority(), source.storage()));
        return aggregate;
    }

    private void removeIfCurrent(MountedStorageRelationship mounted) {
        if (mounts.get(mounted.relationship().key()) == mounted) remove(mounted.relationship().key());
    }

    private MountGeneration nextMountGeneration(PolicyKey key) {
        var current = mountGenerations.get(key);
        var next = current == null ? new MountGeneration(1) : current.next();
        mountGenerations.put(key, next);
        return next;
    }

    record LevelCloseResult(boolean servicePresentBefore, int mountedProvidersBefore, int mountedProvidersRemoved,
            boolean serviceRemoved) {
    }
}
