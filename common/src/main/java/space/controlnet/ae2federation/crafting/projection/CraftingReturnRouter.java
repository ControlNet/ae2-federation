package space.controlnet.ae2federation.crafting.projection;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import java.util.function.Function;
import java.util.function.Predicate;
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
 * once the consumer can take it. What a consumer is owed after all its jobs ended, such as the output of a job it
 * cancelled, goes straight into its storage, so it needs no storage on the executing network to wait in.
 *
 * <p>What a provider gives back beyond any debt, such as a byproduct no pattern names, goes where it would go if the
 * provider were on the network that pushed to it: into the storage of the consumer whose CPU pushed to that provider
 * last, read from the source AE2's pattern provider gives its returns. So a network of pattern providers with no storage
 * of its own does not clog. Only a network with no crafting CPU does this: one with a CPU may be running its own job
 * through the same provider, and AE2 ends such a job when its output arrives but lets the output go on into storage,
 * so by the time it reaches this router nothing tells it from a byproduct. Those networks keep what is not owed.
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
    private final Predicate<NetworkId> jobsEnded;
    private final Function<IActionSource, @Nullable NetworkId> pusher;
    private final space.controlnet.ae2federation.observability.statistics.FederationStatistics statistics;
    private final Storage storage = new Storage();

    CraftingReturnRouter(NetworkId executing, IGrid executingGrid, CraftingReturnLedger ledger,
            Function<NetworkId, @Nullable IGrid> consumerGrids, Predicate<NetworkId> jobsEnded,
            Function<IActionSource, @Nullable NetworkId> pusher,
            space.controlnet.ae2federation.observability.statistics.FederationStatistics statistics) {
        this.executing = executing;
        this.executingGrid = executingGrid;
        this.ledger = ledger;
        this.consumerGrids = consumerGrids;
        this.jobsEnded = jobsEnded;
        this.pusher = pusher;
        this.statistics = statistics;
    }

    IGrid grid() {
        return executingGrid;
    }

    NetworkId executing() {
        return executing;
    }

    /**
     * How much of {@code what} the consumer's CPUs still wait for, at most {@code owed}, counted over the CPUs AE2
     * lists as active. In the tick a CPU leaves the Grid (its chunk unloads), AE2's requested amount and requested keys
     * still count it until the end of the tick, although its crafting storage no longer feeds it: what was handed to
     * the consumer then would land in its plain storage, where the job never finds it.
     *
     * <p>Optional lifecycle adapters expose exact addon waiting amounts. Without one, an independently implemented
     * busy CPU that AE2 lists as requesting the key is conservatively capped by the outstanding debt.
     */
    static long waiting(IGrid consumer, AEKey what, long owed) {
        var crafting = consumer.getCraftingService();
        if (crafting.getRequestedAmount(what) <= 0 && !crafting.isRequesting(what)) return 0;
        long requested = 0;
        boolean addonBusy = false;
        for (var cpu : crafting.getCpus()) {
            long amount = CraftingCpuLifecycle.waiting(cpu, what);
            if (amount >= 0) {
                requested += amount;
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
            long handed = 0;
            long left = amount;
            long stored = 0;
            // What is owed and has not arrived yet; only what arrives beyond it is free for a byproduct's pusher.
            long unarrived = 0;
            if (ledger.owes(executing, what)) {
                for (var owed : ledger.consumers(executing, what).entrySet()) {
                    unarrived += Math.max(0, owed.getValue() - ledger.held(executing, owed.getKey(), what));
                }
                // First to the consumers whose CPUs take it now, oldest push first.
                for (var owed : ledger.consumers(executing, what).entrySet()) {
                    if (left <= 0) break;
                    var consumer = consumerGrids.apply(owed.getKey());
                    if (consumer == null || consumer == executingGrid) continue;
                    long offer = Math.min(left, waiting(consumer, what, owed.getValue()));
                    if (offer <= 0) continue;
                    // The consumer's own crafting storage takes it for the waiting CPU, as for any insert there.
                    long accepted = consumer.getStorageService().getInventory().insert(what, offer, mode, source);
                    take(owed.getKey(), what, accepted, mode);
                    handed += accepted;
                    left -= accepted;
                }
                // Then into the storage of the consumers whose jobs all ended: no CPU will wait for it any more.
                for (var owed : ledger.consumers(executing, what).entrySet()) {
                    if (left <= 0) break;
                    var consumer = consumerGrids.apply(owed.getKey());
                    if (consumer == null || consumer == executingGrid || !jobsEnded.test(owed.getKey())) continue;
                    long offer = Math.min(left, owed.getValue() - ledger.held(executing, owed.getKey(), what));
                    if (offer <= 0) continue;
                    long accepted = consumer.getStorageService().getInventory().insert(what, offer, mode, source);
                    take(owed.getKey(), what, accepted, mode);
                    handed += accepted;
                    left -= accepted;
                }
                // The enclosing native NetworkStorage will simulate its own mounts after this router. Claiming their
                // capacity here too would count the same free space twice when the insert exceeds the routed amount.
                // MODULATE must intercept actual local acceptance so only real arrivals become held.
                if (mode == Actionable.MODULATE) stored = hold(what, left, source);
                left -= stored;
            }
            long beyondDebt = left - Math.max(0, unarrived - handed - stored);
            if (beyondDebt > 0) handed += toPusher(what, beyondDebt, mode, source);
            return handed + stored;
        }

        private void take(NetworkId consumer, AEKey what, long accepted, Actionable mode) {
            if (mode != Actionable.MODULATE) return;
            ledger.take(executing, consumer, what, accepted, 0);
            statistics.meter(executing, consumer).add(what, accepted);
        }

        /**
         * Claims only stock actually accepted by the executing Grid's native storage. A rejected insert remains in the
         * machine and can be retried indefinitely; attempts must never inflate held arrival counts.
         */
        private long hold(AEKey what, long left, IActionSource source) {
            long eligible = 0;
            for (var owed : ledger.consumers(executing, what).entrySet()) {
                if (consumerGrids.apply(owed.getKey()) != executingGrid) {
                    eligible += Math.max(0, owed.getValue() - ledger.held(executing, owed.getKey(), what));
                }
            }
            long stored = HeldReturnTransfer.ownStorage(executingGrid).insert(what, Math.min(left, eligible),
                    Actionable.MODULATE, source);
            long assign = stored;
            for (var owed : ledger.consumers(executing, what).entrySet()) {
                if (assign <= 0) break;
                if (consumerGrids.apply(owed.getKey()) == executingGrid) continue;
                assign -= ledger.hold(executing, owed.getKey(), what, assign);
            }
            return stored;
        }

        /**
         * Hands {@code amount}, which no one is owed, to the storage of the consumer that pushed last to the provider
         * {@code source} names, when the executing network has no CPU of its own; returns how much it took.
         */
        private long toPusher(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (!executingGrid.getCraftingService().getCpus().isEmpty()) return 0;
            var consumerId = pusher.apply(source);
            if (consumerId == null || consumerId.equals(executing)) return 0;
            var consumer = consumerGrids.apply(consumerId);
            if (consumer == null || consumer == executingGrid) return 0;
            long accepted = consumer.getStorageService().getInventory().insert(what, amount, mode, source);
            if (mode == Actionable.MODULATE) statistics.meter(executing, consumerId).add(what, accepted);
            return accepted;
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
