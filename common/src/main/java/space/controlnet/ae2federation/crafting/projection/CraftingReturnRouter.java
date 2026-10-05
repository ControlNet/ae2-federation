package space.controlnet.ae2federation.crafting.projection;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.IStorageMounts;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.storage.provenance.FederationManagedStorage;
import space.controlnet.ae2federation.storage.provenance.FederationManagedStorageProvider;

/**
 * Hands the outputs a network's machines owe other networks' CPUs back to those networks. Mounted on the executing
 * network at the top priority and preferred only for keys the {@link CraftingReturnLedger} says it owes, so a
 * returning output reaches it before any drive, as AE2's own crafting storage does for the network's own jobs. It
 * hands a consumer no more than the ledger owes and its CPUs still wait for; the rest stays on the executing network.
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
            for (var owed : ledger.consumers(executing, what).entrySet()) {
                if (handed >= amount) break;
                var consumer = consumerGrids.apply(owed.getKey());
                if (consumer == null || consumer == executingGrid) continue;
                long offer = Math.min(amount - handed, waiting(consumer, what, owed.getValue()));
                if (offer <= 0) continue;
                // The consumer's own crafting storage takes it for the waiting CPU, as for any insert there.
                long accepted = consumer.getStorageService().getInventory().insert(what, offer, mode, source);
                if (mode == Actionable.MODULATE) ledger.take(executing, owed.getKey(), what, accepted);
                handed += accepted;
            }
            return handed;
        }

        @Override
        public Component getDescription() {
            return Component.translatable("ae2federation.storage.crafting_returns");
        }
    }
}
