package space.controlnet.ae2federation.crafting.projection;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.me.cluster.implementations.CraftingCPUCluster;
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
 * What a consumer is owed but does not take now, because the networks are not linked, the consumer is not loaded or
 * its CPU is out of sight, also stays there and is counted as held, to be handed back by {@link HeldReturnTransfer}
 * once the consumer can take it.
 *
 * <p>The only stock it lists is the executing network's transit stock: what was taken out to hand back but went
 * nowhere (see {@link CraftingReturnLedger}). It is listed and can be taken out like any stored item, so it is never
 * hidden from the network that holds it. Usually it is empty, and a terminal or storage watcher that walks the mounts
 * every tick pays only for an empty counter. It is Federation-managed, so storage rules never export it.
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
     * How much of {@code what} the consumer's CPUs still wait for, at most {@code owed}, counted over the CPUs AE2
     * lists as active. In the tick a CPU leaves the Grid (its chunk unloads), AE2's requested amount and requested keys
     * still count it until the end of the tick, although its crafting storage no longer feeds it: what was handed to
     * the consumer then would land in its plain storage, where the job never finds it.
     *
     * <p>AE2 counts only its own CPU clusters; an addon's CPU that AE2 lists as requesting the key, such as Neo ECO's
     * computation system, waits for an amount AE2 cannot tell, so it is taken to wait for all it is owed.
     */
    static long waiting(IGrid consumer, AEKey what, long owed) {
        var crafting = consumer.getCraftingService();
        if (crafting.getRequestedAmount(what) <= 0 && !crafting.isRequesting(what)) return 0;
        long requested = 0;
        boolean addonBusy = false;
        for (var cpu : crafting.getCpus()) {
            if (cpu instanceof CraftingCPUCluster cluster) {
                requested += cluster.craftingLogic.getWaitingFor(what);
            } else if (cpu.isBusy()) {
                addonBusy = true;
            }
        }
        if (requested > 0 || !addonBusy || !crafting.isRequesting(what)) return Math.min(requested, owed);
        return owed;
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
            // First to the consumers whose CPUs take it now, oldest push first.
            for (var owed : ledger.consumers(executing, what).entrySet()) {
                if (left <= 0) break;
                var consumer = consumerGrids.apply(owed.getKey());
                if (consumer == null || consumer == executingGrid) continue;
                long offer = Math.min(left, waiting(consumer, what, owed.getValue()));
                if (offer <= 0) continue;
                // The consumer's own crafting storage takes it for the waiting CPU, as for any insert there.
                long accepted = consumer.getStorageService().getInventory().insert(what, offer, mode, source);
                if (mode == Actionable.MODULATE) ledger.take(executing, owed.getKey(), what, accepted, 0);
                handed += accepted;
                left -= accepted;
            }
            if (mode == Actionable.MODULATE) {
                // The rest stays on this network, held for the consumers it is still owed to: they get it once they
                // can take it again (their network links again, or their CPU's chunk loads again).
                for (var owed : ledger.consumers(executing, what).entrySet()) {
                    if (left <= 0) break;
                    if (consumerGrids.apply(owed.getKey()) == executingGrid) continue;
                    left -= ledger.hold(executing, owed.getKey(), what, left);
                }
            }
            return handed;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            return ledger.takeTransit(executing, what, amount, mode);
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            out.addAll(ledger.transit(executing));
        }

        @Override
        public Component getDescription() {
            return Component.translatable("ae2federation.storage.crafting_returns");
        }
    }
}
