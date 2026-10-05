package space.controlnet.ae2federation.crafting.projection;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.MEStorage;
import appeng.me.service.helpers.CraftingServiceStorage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.ae2.storage.NativeMountLedger;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.storage.provenance.FederationManagedStorage;
import space.controlnet.ae2federation.storage.provenance.FederationManagedStorageProvider;

/**
 * Hands the outputs a network's machines owe other networks' CPUs back to those networks. Mounted on the executing
 * network at the top priority and preferred only for keys the {@link CraftingReturnLedger} says it owes, so a
 * returning output reaches it before any drive, as AE2's own crafting storage does for the network's own jobs. It
 * hands a consumer no more than the ledger owes and its CPUs still wait for; the rest stays on the executing network.
 * What a consumer is owed but cannot take now, because the networks are not linked or the consumer is not loaded, also
 * stays there and is counted as held, to be handed back by {@link #handBackHeld} once the consumer can take it.
 *
 * <p>It holds nothing: it lists no stacks and extracts nothing, so a terminal or storage watcher that walks the mounts
 * every tick pays only an empty call. It is Federation-managed, so storage rules never export it.
 */
final class CraftingReturnRouter implements FederationManagedStorageProvider {
    private final NetworkId executing;
    private final IGrid executingGrid;
    private final CraftingReturnLedger ledger;
    private final Function<NetworkId, @Nullable IGrid> consumerGrids;
    private final Storage storage = new Storage();

    CraftingReturnRouter(NetworkId executing, IGrid executingGrid, CraftingReturnLedger ledger,
            Function<NetworkId, @Nullable IGrid> consumerGrids) {
        this.executing = executing;
        this.executingGrid = executingGrid;
        this.ledger = ledger;
        this.consumerGrids = consumerGrids;
    }

    IGrid grid() {
        return executingGrid;
    }

    /**
     * How much of {@code what} the consumer's CPUs still wait for, at most {@code owed}. AE2 counts only its own CPU
     * clusters in the requested amount; an addon's CPU that it lists as requesting the key, such as Neo ECO's
     * computation system, waits for an amount AE2 cannot tell, so it is taken to wait for all it is owed.
     */
    static long waiting(IGrid consumer, AEKey what, long owed) {
        var crafting = consumer.getCraftingService();
        long requested = crafting.getRequestedAmount(what);
        return requested > 0 || !crafting.isRequesting(what) ? Math.min(requested, owed) : owed;
    }

    /**
     * Hands {@code consumer} what {@code executing}'s storage holds for it of {@code what}, as far as its CPUs still wait
     * for it. Taken only from the executing network's own storage, never from other networks' storage it sees through
     * Federation, which may be the consumer's own.
     *
     * @return how much was handed back
     */
    static long handBackHeld(CraftingReturnLedger ledger, NetworkId executing, IGrid executingGrid,
            CraftingReturnLedger.Owed owed, IGrid consumer) {
        long wanted = Math.min(owed.held(), waiting(consumer, owed.key(), owed.amount()));
        if (wanted <= 0) return 0;
        var source = IActionSource.empty();
        var inventory = consumer.getStorageService().getInventory();
        wanted = inventory.insert(owed.key(), wanted, Actionable.SIMULATE, source);
        if (wanted <= 0) return 0;
        var own = ownStorage(executingGrid);
        long extracted = 0;
        for (var storage : own) {
            if (extracted >= wanted) break;
            extracted += storage.extract(owed.key(), wanted - extracted, Actionable.MODULATE, source);
        }
        if (extracted <= 0) return 0;
        long inserted = inventory.insert(owed.key(), extracted, Actionable.MODULATE, source);
        long back = extracted - inserted;
        for (int index = own.size() - 1; index >= 0 && back > 0; index--) {
            back -= own.get(index).insert(owed.key(), back, Actionable.MODULATE, source);
        }
        ledger.take(executing, owed.consumer(), owed.key(), inserted, inserted);
        return inserted;
    }

    /**
     * The executing network's own inventories in AE2's extraction order, lowest priority first: what its providers
     * mounted, without Federation's own mounts (other networks' storage, this router) and AE2's crafting storage.
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

    @Override
    public void mountInventories(IStorageMounts storageMounts) {
        storageMounts.mount(storage, Integer.MAX_VALUE);
    }

    private final class Storage implements FederationManagedStorage {
        @Override
        public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
            return ledger.owes(executing, what);
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (!ledger.owes(executing, what)) return 0;
            long handed = 0;
            long left = amount;
            for (var owed : ledger.consumers(executing, what).entrySet()) {
                if (left <= 0) break;
                var consumer = consumerGrids.apply(owed.getKey());
                if (consumer == null) {
                    // The consumer cannot take it now: it stays on this network, kept for the consumer.
                    long kept = Math.min(left, owed.getValue() - ledger.held(executing, owed.getKey(), what));
                    if (mode == Actionable.MODULATE) ledger.hold(executing, owed.getKey(), what, kept);
                    left -= Math.max(0, kept);
                    continue;
                }
                if (consumer == executingGrid) continue;
                long offer = Math.min(left, waiting(consumer, what, owed.getValue()));
                if (offer <= 0) continue;
                // The consumer's own crafting storage takes it for the waiting CPU, as for any insert there.
                long accepted = consumer.getStorageService().getInventory().insert(what, offer, mode, source);
                if (mode == Actionable.MODULATE) ledger.take(executing, owed.getKey(), what, accepted, 0);
                handed += accepted;
                left -= accepted;
            }
            return handed;
        }

        @Override
        public Component getDescription() {
            return Component.translatable("ae2federation.storage.crafting_returns");
        }
    }
}
