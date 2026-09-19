package space.controlnet.ae2federation.storage.mount;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.storage.IStorageService;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.ae2.storage.StorageProvenanceException;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.policy.BackendStatus;
import space.controlnet.ae2federation.policy.PolicyActivationState;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyRuntimeEndpoints;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.storage.provenance.ExportSource;
import space.controlnet.ae2federation.storage.provenance.MountGeneration;
import space.controlnet.ae2federation.storage.provenance.NativeSourceDomain;
import space.controlnet.ae2federation.storage.provenance.NativeSourceDomainRegistry;
import space.controlnet.ae2federation.storage.provenance.ProvenanceDiagnostic;
import space.controlnet.ae2federation.storage.provenance.ProvenanceException;

public final class StorageMountService implements AutoCloseable {
    private static final Map<ServerLevel, StorageMountService> SERVICES = new WeakHashMap<>();
    private final ServerLevel level;
    private final Map<PolicyKey, StorageRelationship> observed = new HashMap<>();
    private final Map<PolicyKey, MountedStorageRelationship> mounts = new HashMap<>();
    private final Map<PolicyKey, MountGeneration> mountGenerations = new HashMap<>();
    private final Map<PolicyKey, ProvenanceDiagnostic> diagnostics = new HashMap<>();
    private int removedProviderCount;
    private final NativeSourceDomainRegistry provenance = new NativeSourceDomainRegistry();
    private final StorageFabricObserver fabrics;

    private StorageMountService(ServerLevel level) {
        this.level = level;
        fabrics = new StorageFabricObserver(level);
    }

    public static synchronized StorageMountService get(ServerLevel level) {
        return SERVICES.computeIfAbsent(level, StorageMountService::new);
    }

    public static synchronized void reconcileIfPresent(ServerLevel level) {
        var service = SERVICES.get(level);
        if (service != null) {
            service.reconcileAll();
        }
    }

    public static synchronized void topologyChangedIfPresent(ServerLevel level) {
        var service = SERVICES.get(level);
        if (service != null) {
            service.reconcileTopology();
        }
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
        var firstId = FabricRegistryAccess.confirmedNetworkId(first);
        var secondId = FabricRegistryAccess.confirmedNetworkId(second);
        if (firstId.isEmpty() || secondId.isEmpty() || firstId.equals(secondId)) {
            reconcileAll();
            return;
        }
        observe(new StorageRelationship(new PolicyKey(firstId.orElseThrow(), secondId.orElseThrow(),
                PolicyCapability.STORAGE), first, second));
        observe(new StorageRelationship(new PolicyKey(secondId.orElseThrow(), firstId.orElseThrow(),
                PolicyCapability.STORAGE), second, first));
    }

    public void observeFabricMembers(Iterable<IGrid> grids) {
        fabrics.register(grids);
        reconcileTopology();
    }

    public StorageMountState observe(StorageRelationship relationship) {
        observed.put(relationship.key(), relationship);
        return reconcile(relationship);
    }

    public StorageMountState reconcile(StorageRelationship relationship) {
        if (!fabrics.contains(relationship)) {
            remove(relationship.key());
            return StorageMountState.INACTIVE;
        }
        Discovery discovery;
        try {
            discovery = discover(relationship.providerGrid());
        } catch (ProvenanceException exception) {
            diagnostics.put(relationship.key(), exception.diagnostic());
            remove(relationship.key());
            return StorageMountState.BACKEND_UNREADY;
        } catch (StorageProvenanceException exception) {
            remove(relationship.key());
            return StorageMountState.BACKEND_UNREADY;
        }
        if (discovery.domain().sources().isEmpty() || !discovery.ready()) {
            remove(relationship.key());
            return StorageMountState.BACKEND_UNREADY;
        }
        var endpoints = new PolicyRuntimeEndpoints(relationship.consumerGrid(), relationship.providerGrid(),
                BackendStatus.READY);
        if (PolicyService.get(level).activation(relationship.key(), endpoints) != PolicyActivationState.ACTIVE) {
            remove(relationship.key());
            return StorageMountState.INACTIVE;
        }
        var mounted = mounts.get(relationship.key());
        if (mounted != null) {
            if (mounted.relationship().consumerGrid() == relationship.consumerGrid()
                    && mounted.relationship().providerGrid() == relationship.providerGrid()
                    && mounted.domain() == discovery.domain()) {
                return StorageMountState.UNCHANGED;
            }
            remove(relationship.key());
        }
        var delegate = aggregate(discovery.domain().sources());
        var holder = new MountedStorageRelationship[1];
        var authority = new StorageRelationshipAuthority(level, relationship,
                () -> holder[0] != null && sourceCurrent(holder[0]));
        var projection = new AuthorizedStorageProjection(delegate, authority);
        var priority = discovery.domain().sources().stream().mapToInt(ExportSource::priority).max().orElse(0);
        var provider = new RelationshipStorageProvider(projection, priority);
        var next = new MountedStorageRelationship(relationship, discovery.domain(),
                nextMountGeneration(relationship.key()), provider);
        holder[0] = next;
        relationship.consumerGrid().getService(IStorageService.class).addGlobalStorageProvider(provider);
        mounts.put(relationship.key(), next);
        diagnostics.remove(relationship.key());
        return StorageMountState.MOUNTED;
    }

    public void reconcileAll() {
        List.copyOf(observed.values()).forEach(this::reconcile);
    }

    public int mountedRelationshipCount() {
        return mounts.size();
    }

    public MEStorage projection(PolicyKey key) {
        var mounted = mounts.get(key);
        return mounted == null ? null : mounted.provider().projection();
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
        return diagnostics.get(key);
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

    private int closeState() {
        var mountedProvidersRemoved = mounts.size();
        List.copyOf(mounts.values()).forEach(mounted -> mounted.relationship().consumerGrid()
                .getService(IStorageService.class).removeGlobalStorageProvider(mounted.provider()));
        mounts.clear();
        removedProviderCount += mountedProvidersRemoved;
        observed.clear();
        mountGenerations.clear();
        diagnostics.clear();
        provenance.clear();
        fabrics.clear();
        return mountedProvidersRemoved;
    }

    private boolean sourceCurrent(MountedStorageRelationship mounted) {
        if (mounts.get(mounted.relationship().key()) != mounted
                || !mounted.generation().equals(mountGenerations.get(mounted.relationship().key()))) {
            return false;
        }
        if (!mounted.sourceReady() || !fabrics.contains(mounted.relationship())) {
            removeIfCurrent(mounted);
            return false;
        }
        try {
            var current = discover(mounted.relationship().providerGrid()).domain();
            if (current == mounted.domain() && provenance.isCurrent(current)) {
                return true;
            }
        } catch (ProvenanceException exception) {
            diagnostics.put(mounted.relationship().key(), exception.diagnostic());
        } catch (StorageProvenanceException exception) {
        }
        removeIfCurrent(mounted);
        return false;
    }

    private void reconcileTopology() {
        observed.putAll(fabrics.relationships());
        reconcileAll();
    }

    private Discovery discover(IGrid providerGrid) {
        var domain = provenance.discover(providerGrid);
        return new Discovery(domain, !domain.sourceNodes().isEmpty()
                && domain.sourceNodes().stream().allMatch(IGridNode::hasGridBooted));
    }

    private static MEStorage aggregate(List<ExportSource> sources) {
        if (sources.size() == 1) {
            return sources.getFirst().storage();
        }
        var aggregate = new NetworkStorage();
        sources.forEach(source -> aggregate.mount(source.priority(), source.storage()));
        return aggregate;
    }

    private void removeIfCurrent(MountedStorageRelationship mounted) {
        if (mounts.get(mounted.relationship().key()) == mounted) {
            remove(mounted.relationship().key());
        }
    }

    private MountGeneration nextMountGeneration(PolicyKey key) {
        var current = mountGenerations.get(key);
        var next = current == null ? new MountGeneration(1) : current.next();
        mountGenerations.put(key, next);
        return next;
    }

    private record Discovery(NativeSourceDomain domain, boolean ready) {
    }

    record LevelCloseResult(boolean servicePresentBefore, int mountedProvidersBefore, int mountedProvidersRemoved,
            boolean serviceRemoved) {
    }
}
