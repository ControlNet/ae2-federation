package space.controlnet.ae2federation.crafting.projection;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.me.service.helpers.CraftingServiceStorage;
import appeng.me.storage.NetworkStorage;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import space.controlnet.ae2federation.mixin.compat.NetworkStorageAccess;
import java.util.List;
import space.controlnet.ae2federation.ae2.storage.NativeMountLedger;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.IdentityEpoch;
import space.controlnet.ae2federation.storage.provenance.FederationManagedStorage;
import space.controlnet.ae2federation.storage.provenance.FederationManagedStorageProvider;

/**
 * Moves what an executing network holds for a consumer to that consumer. The stock comes from the executing network's
 * transit stock first, then from its own inventories, never from other networks' storage it sees through Federation,
 * which may be the consumer's own.
 *
 * <p>Nothing that was taken out is lost on the way. A simulated insert is not a reservation and the real one may take
 * less, and a source that lets stock out may not take it back (an extract-only storage bus, a full drive). So only what
 * the sources and the target both promise is taken out; what the target then refuses goes back into the executing
 * network's own inventories, and what they refuse too stays in the ledger's transit stock, real stock that the
 * executing network lists and that the next transfer tries first. The debt goes down only by what the target took.
 *
 * <p>Transit stock is saved with the ledger. After a restart the executing network lists it again, and stores it back
 * where it fits, once Federation sees that network in a Federation Domain again; until then it stays in the ledger.
 */
public final class HeldReturnTransfer {
    private HeldReturnTransfer() {
    }

    /**
     * Moves up to {@code wanted} of {@code key} from {@code executingGrid} into {@code target}, counting it as handed
     * back from what {@code executing} holds for {@code consumer}.
     *
     * @return how much {@code target} took
     */
    public static long handBack(CraftingReturnLedger ledger, NetworkId executing, IGrid executingGrid,
            NetworkId consumer, AEKey key, long wanted, MEStorage target) {
        if (wanted <= 0) return 0;
        var source = IActionSource.empty();
        var own = ownStorage(executingGrid);
        long available = ledger.takeTransit(executing, key, wanted, Actionable.SIMULATE);
        available += own.extract(key, wanted - available, Actionable.SIMULATE, source);
        long promised = Math.min(wanted, available);
        if (promised <= 0) return 0;
        promised = Math.min(promised, target.insert(key, promised, Actionable.SIMULATE, source));
        if (promised <= 0) return 0;
        long extracted = ledger.takeTransit(executing, key, promised, Actionable.MODULATE);
        extracted += own.extract(key, promised - extracted, Actionable.MODULATE, source);
        if (extracted <= 0) return 0;
        long inserted = target.insert(key, extracted, Actionable.MODULATE, source);
        long residual = extracted - inserted;
        residual -= ownStorage(executingGrid).insert(key, residual, Actionable.MODULATE, source);
        if (residual > 0) {
            ledger.addTransit(executing, key, residual);
        }
        ledger.take(executing, consumer, key, inserted, inserted);
        return inserted;
    }

    /**
     * Stores {@code executing}'s transit stock back in its own inventories, as far as they take it; the rest stays in
     * transit.
     */
    static void storeTransit(CraftingReturnLedger ledger, NetworkId executing, IGrid executingGrid) {
        var stock = ledger.transit(executing);
        if (stock.isEmpty()) return;
        var source = IActionSource.empty();
        var own = ownStorage(executingGrid);
        for (var entry : stock) {
            var key = entry.getKey();
            // Remove the actual stock before calling a mounted inventory. Its insertion can reenter the Grid,
            // whose return router must not expose these same in-flight items to extraction or another transfer.
            long reserved = ledger.takeTransit(executing, key, entry.getLongValue(), Actionable.MODULATE);
            if (reserved <= 0) continue;
            long stored = 0;
            try {
                stored = own.insert(key, reserved, Actionable.MODULATE, source);
            } finally {
                ledger.addTransit(executing, key, reserved - stored);
            }
        }
    }

    /**
     * The executing network's own inventories using native AE2 insertion and extraction order: what its providers
     * mounted, without Federation's own mounts (other networks' storage, the return router) and AE2's crafting storage.
     */
    static MEStorage ownStorage(IGrid grid) {
        var service = grid.getStorageService();
        long generation = NativeMountLedger.generation(service);
        long epoch = IdentityEpoch.current();
        if (views != null) {
            var cached = views.get(grid);
            if (cached != null && cached.generation() == generation && cached.epoch() == epoch) return cached.storage();
        }
        long started = System.nanoTime();
        var result = new NetworkStorage();
        if (!NativeMountLedger.available(service)
                || !(service.getInventory() instanceof NetworkStorageAccess ordered)) return result;
        var snapshot = NativeMountLedger.snapshot(service);
        var included = Collections.newSetFromMap(new IdentityHashMap<MEStorage, Boolean>());
        for (var providers : List.of(snapshot.nodeProviders(), snapshot.globalProviders())) {
            for (var provider : providers) {
                if (provider.provider() instanceof FederationManagedStorageProvider
                        || provider.provider() instanceof CraftingServiceStorage) continue;
                for (var mount : provider.mounts()) {
                    if (!(mount.storage() instanceof FederationManagedStorage)) included.add(mount.storage());
                }
            }
        }
        // Preserve AE2's actual mount order, not the provider-map iteration order. Keep all native wrappers intact.
        ordered.ae2federation$orderedMounts().forEach((priority, mounts) -> {
            for (var storage : mounts) {
                if (included.contains(storage)) result.mount(priority, new appeng.me.storage.DelegatingMEInventory(storage) {
                    private boolean valid() {
                        return !grid.isEmpty() && NativeMountLedger.generation(service) == generation
                                && IdentityEpoch.current() == epoch;
                    }
                    @Override
                    public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                        return valid() ? super.insert(key, amount, mode, source) : 0;
                    }
                    @Override
                    public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
                        return valid() ? super.extract(key, amount, mode, source) : 0;
                    }
                });
            }
        });
        ProjectionDiagnostics.viewBuilt(included.size(), System.nanoTime() - started);
        if (views != null) views.put(grid, new View(generation, epoch, result));
        return result;
    }

    private record View(long generation, long epoch, MEStorage storage) {}
    private static Map<IGrid, View> views;

    /** Server-thread sweep scope. Mount changes invalidate views immediately, including reentrant operations. */
    static Scope scope() {
        return new Scope();
    }

    static final class Scope implements AutoCloseable {
        private final Map<IGrid, View> previous = views;
        private Scope() { views = new IdentityHashMap<>(); }
        @Override
        public void close() { views = previous; }
    }
}
