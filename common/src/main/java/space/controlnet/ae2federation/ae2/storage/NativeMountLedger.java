package space.controlnet.ae2federation.ae2.storage;

import appeng.api.networking.IGridNode;
import appeng.api.networking.storage.IStorageService;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.me.storage.DelegatingMEInventory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.storage.provenance.FederationManagedStorageProvider;

/**
 * Read-only mirror of AE2's real per-Grid storage mount table.
 *
 * <p>Why an internal hook is needed: AE2's public {@link IStorageService} exposes {@code addGlobalStorageProvider},
 * {@code refreshNodeStorageProvider} and friends, but no way to <em>query</em> which inventories a provider actually
 * mounted (and at which priority) or to <em>listen</em> for mount/unmount. The only way to learn that without replaying
 * {@link IStorageProvider#mountInventories} (which is side-effecting for drives/chests and is exactly the expensive
 * per-operation work this ledger removes) is to observe {@code StorageService$ProviderState}, AE2's own mount table
 * for both node providers and global providers. The compatibility mixins in
 * {@code space.controlnet.ae2federation.mixin.compat} record every successful {@code ProviderState.mount(MEStorage,
 * int)} and every {@code ProviderState.unmount()} and bump a per-service mount generation. Nothing here mutates AE2
 * state, suppresses notifications, or participates in insert/extract/list: AE2 remains the only inventory authority.
 *
 * <p>Lifecycle: the per-provider mount lists and the per-service generation are {@code @Unique} fields on the AE2
 * objects themselves, so the ledger lives and dies with each {@code StorageService} (Grid split/merge/destroy, level
 * unload). There is no global map to leak or clear.
 */
public final class NativeMountLedger {
    private static final AtomicLong MOUNT_EVENTS = new AtomicLong();

    private NativeMountLedger() {
    }

    /** One inventory mounted by a provider, in AE2's mount order, with the priority AE2 received. */
    public record Mount(MEStorage storage, int priority) {
    }

    /**
     * One provider's current mount table. {@code node} is null for global providers; {@code globalOrdinal} is the
     * provider's index in AE2's global provider list (-1 for node providers).
     */
    public record ProviderMounts(@Nullable IGridNode node, IStorageProvider provider, int globalOrdinal,
            List<Mount> mounts) {
        public ProviderMounts {
            mounts = List.copyOf(mounts);
        }
    }

    /** Consistent copy of one service's mount table taken at {@code generation}. */
    public record Snapshot(long generation, List<ProviderMounts> nodeProviders, List<ProviderMounts> globalProviders) {
        public Snapshot {
            nodeProviders = List.copyOf(nodeProviders);
            globalProviders = List.copyOf(globalProviders);
        }
    }

    /** Implemented by the {@code StorageService$ProviderState} compatibility mixin. */
    public interface ProviderMountTable {
        IStorageProvider ae2federation$provider();

        List<Mount> ae2federation$mounts();
    }

    /** Implemented by the {@code StorageService} compatibility mixin. */
    public interface ServiceMountLedger {
        long ae2federation$mountGeneration();

        void ae2federation$markMountChanged();

        java.util.Map<IGridNode, ?> ae2federation$nodeProviderStates();

        List<?> ae2federation$globalProviderStates();
    }

    /** Implemented by the {@code DelegatingMEInventory} accessor mixin. */
    public interface DelegatingInventoryAccess {
        MEStorage ae2federation$delegate();
    }

    public static boolean available(IStorageService service) {
        return service instanceof ServiceMountLedger;
    }

    /** Current mount generation of {@code service}, or -1 when the service is not AE2's StorageService. */
    public static long generation(IStorageService service) {
        return service instanceof ServiceMountLedger ledger ? ledger.ae2federation$mountGeneration() : -1;
    }

    /** Copies the mount table. Only called when a domain has to be rebuilt, never per operation. */
    public static Snapshot snapshot(IStorageService service) {
        if (!(service instanceof ServiceMountLedger ledger)) {
            throw new IllegalStateException("Grid storage service is not AE2's StorageService mount table");
        }
        var nodes = new ArrayList<ProviderMounts>();
        for (var entry : ledger.ae2federation$nodeProviderStates().entrySet()) {
            var table = (ProviderMountTable) entry.getValue();
            nodes.add(new ProviderMounts(entry.getKey(), table.ae2federation$provider(), -1,
                    table.ae2federation$mounts()));
        }
        var globals = new ArrayList<ProviderMounts>();
        var states = ledger.ae2federation$globalProviderStates();
        for (var index = 0; index < states.size(); index++) {
            var table = (ProviderMountTable) states.get(index);
            globals.add(new ProviderMounts(null, table.ae2federation$provider(), index, table.ae2federation$mounts()));
        }
        return new Snapshot(ledger.ae2federation$mountGeneration(), nodes, globals);
    }

    /** AE2's own {@link DelegatingMEInventory} forwarding target, or null when {@code storage} is not one. */
    public static @Nullable MEStorage delegateOf(MEStorage storage) {
        return storage instanceof DelegatingMEInventory && storage instanceof DelegatingInventoryAccess access
                ? access.ae2federation$delegate()
                : null;
    }

    public static long mountEventCount() {
        return MOUNT_EVENTS.get();
    }

    /** Called by the ProviderState mixin after AE2 really mounted or unmounted inventories. */
    public static void recordChange(Object service, IStorageProvider provider) {
        MOUNT_EVENTS.incrementAndGet();
        // Federation's own projections never become sources, so their (re)mounts must not invalidate the consumer
        // Grid's own source domain nor trigger a reconcile feedback loop.
        if (!(provider instanceof FederationManagedStorageProvider) && service instanceof ServiceMountLedger ledger) {
            ledger.ae2federation$markMountChanged();
        }
    }
}
