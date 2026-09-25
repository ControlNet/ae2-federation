package space.controlnet.ae2federation.ae2.storage;

import appeng.api.networking.IGridNode;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class NativeStorageProvenance {
    private static final java.util.concurrent.atomic.AtomicLong MOUNT_REPLAYS = new java.util.concurrent.atomic.AtomicLong();
    private final Map<IStorageProvider, Set<MEStorage>> qualifiedMounts = new IdentityHashMap<>();
    private final Set<MEStorage> nativeSources = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<MEStorage> managedProjections = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<MEStorage, MEStorage> routeSources = new IdentityHashMap<>();

    public List<NativeStorageSource> qualify(IGridNode node) {
        var provider = node.getService(IStorageProvider.class);
        if (provider == null) {
            throw new StorageProvenanceException(StorageProvenanceException.Diagnostic.UNKNOWN_PROVIDER,
                    "Native Grid node has no registered storage provider");
        }
        if (provider instanceof space.controlnet.ae2federation.storage.provenance.FederationManagedStorageProvider) {
            throw new StorageProvenanceException(StorageProvenanceException.Diagnostic.UNKNOWN_PROVIDER,
                    "Federation providers cannot qualify native source handles");
        }
        var captured = capture(provider);
        for (var mounted : captured) {
            if (mounted.storage() instanceof NetworkStorage) {
                throw new StorageProvenanceException(StorageProvenanceException.Diagnostic.COMPLETE_AGGREGATE_MOUNT,
                        "A complete NetworkStorage aggregate cannot qualify as a native source");
            }
        }
        var providerSources = Collections.newSetFromMap(new IdentityHashMap<MEStorage, Boolean>());
        captured.forEach(mounted -> providerSources.add(mounted.storage()));
        qualifiedMounts.put(provider, providerSources);
        nativeSources.addAll(providerSources);
        return captured;
    }

    public NativeStorageProvider createProvider() {
        return new NativeStorageProvider(this);
    }

    public List<NativeStorageSource> sources(Iterable<? extends IStorageProvider> providers) {
        var sources = new IdentityHashMap<MEStorage, Integer>();
        for (var provider : providers) {
            if (provider instanceof NativeStorageProvider nativeProvider && nativeProvider.owner() != this) {
                throw new StorageProvenanceException(StorageProvenanceException.Diagnostic.UNKNOWN_PROVIDER,
                        "Federation storage provider belongs to another provenance owner");
            }
            for (var mounted : capture(provider)) {
                collect(provider, mounted.storage(), mounted.priority(), sources);
            }
        }
        var result = new ArrayList<NativeStorageSource>();
        sources.forEach((storage, priority) -> result.add(new NativeStorageSource(storage, priority)));
        return List.copyOf(result);
    }

    MEStorage createProjection(MEStorage delegate) {
        var projection = new FederationStorageView("projection", delegate);
        managedProjections.add(projection);
        return projection;
    }

    MEStorage createRoute(String route, MEStorage nativeSource) {
        requireNativeSource(nativeSource);
        var alias = new FederationStorageView(route, nativeSource);
        routeSources.put(alias, nativeSource);
        return alias;
    }

    private void collect(IStorageProvider provider, MEStorage mounted, int priority,
            IdentityHashMap<MEStorage, Integer> sources) {
        if (managedProjections.contains(mounted)) {
            return;
        }
        var routeSource = routeSources.get(mounted);
        if (routeSource != null) {
            requireNativeSource(routeSource);
            sources.merge(routeSource, priority, Math::max);
            return;
        }
        if (mounted instanceof NetworkStorage) {
            throw new StorageProvenanceException(StorageProvenanceException.Diagnostic.COMPLETE_AGGREGATE_MOUNT,
                    "A complete NetworkStorage aggregate cannot be exported as a source");
        }
        var providerSources = qualifiedMounts.get(provider);
        if (providerSources == null || !providerSources.contains(mounted)) {
            throw new StorageProvenanceException(StorageProvenanceException.Diagnostic.OPAQUE_ALIAS,
                    "Mounted storage has no verified native source identity");
        }
        sources.merge(mounted, priority, Math::max);
    }

    /** Total {@code mountInventories} replays performed by this class. Diagnostic counter only. */
    public static long mountReplayCount() {
        return MOUNT_REPLAYS.get();
    }

    private List<NativeStorageSource> capture(IStorageProvider provider) {
        MOUNT_REPLAYS.incrementAndGet();
        var mounts = new ArrayList<NativeStorageSource>();
        provider.mountInventories((storage, priority) -> mounts.add(new NativeStorageSource(storage, priority)));
        return List.copyOf(mounts);
    }

    private void requireNativeSource(MEStorage storage) {
        if (!nativeSources.contains(storage)) {
            throw new StorageProvenanceException(StorageProvenanceException.Diagnostic.INVALID_ALIAS_TARGET,
                    "Route target was not captured from a qualified native provider");
        }
    }
}
