package space.controlnet.ae2federation.crafting.projection;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.me.service.helpers.CraftingServiceStorage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import space.controlnet.ae2federation.ae2.storage.NativeMountLedger;
import space.controlnet.ae2federation.identity.NetworkId;
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
        for (var storage : own) {
            if (available >= wanted) break;
            available += storage.extract(key, wanted - available, Actionable.SIMULATE, source);
        }
        long promised = Math.min(wanted, available);
        if (promised <= 0) return 0;
        promised = Math.min(promised, target.insert(key, promised, Actionable.SIMULATE, source));
        if (promised <= 0) return 0;
        long extracted = ledger.takeTransit(executing, key, promised, Actionable.MODULATE);
        for (var storage : own) {
            if (extracted >= promised) break;
            extracted += storage.extract(key, promised - extracted, Actionable.MODULATE, source);
        }
        if (extracted <= 0) return 0;
        long inserted = target.insert(key, extracted, Actionable.MODULATE, source);
        long residual = extracted - inserted;
        // Highest priority first, where AE2 would store it.
        for (int index = own.size() - 1; index >= 0 && residual > 0; index--) {
            residual -= own.get(index).insert(key, residual, Actionable.MODULATE, source);
        }
        if (residual > 0) {
            ledger.addTransit(executing, key, residual);
            executingGrid.getStorageService().invalidateCache();
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
            long left = entry.getLongValue();
            for (int index = own.size() - 1; index >= 0 && left > 0; index--) {
                long stored = own.get(index).insert(key, left, Actionable.MODULATE, source);
                left -= ledger.takeTransit(executing, key, stored, Actionable.MODULATE);
            }
        }
        executingGrid.getStorageService().invalidateCache();
    }

    /**
     * The executing network's own inventories in AE2's extraction order, lowest priority first: what its providers
     * mounted, without Federation's own mounts (other networks' storage, the return router) and AE2's crafting storage.
     */
    private static List<MEStorage> ownStorage(IGrid grid) {
        var service = grid.getStorageService();
        if (!NativeMountLedger.available(service)) return List.of();
        var snapshot = NativeMountLedger.snapshot(service);
        var mounts = new ArrayList<NativeMountLedger.Mount>();
        for (var providers : List.of(snapshot.nodeProviders(), snapshot.globalProviders())) {
            for (var provider : providers) {
                if (provider.provider() instanceof FederationManagedStorageProvider
                        || provider.provider() instanceof CraftingServiceStorage) continue;
                for (var mount : provider.mounts()) {
                    if (!(mount.storage() instanceof FederationManagedStorage)) mounts.add(mount);
                }
            }
        }
        mounts.sort(Comparator.comparingInt(NativeMountLedger.Mount::priority));
        return mounts.stream().map(NativeMountLedger.Mount::storage).toList();
    }
}
