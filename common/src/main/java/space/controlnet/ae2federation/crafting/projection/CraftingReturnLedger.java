package space.controlnet.ae2federation.crafting.projection;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import space.controlnet.ae2federation.identity.NetworkId;

/**
 * What a network's machines still owe other networks' CPUs: each accepted push through a projection adds the pattern's
 * outputs and container items under (the network whose provider ran it, the consumer whose CPU pushed it, key). The
 * executing network's return router hands that much back to the consumer as it arrives. Saved with the world, so
 * outputs that arrive after a reload still reach the job waiting for them.
 *
 * <p>An output that arrives while its consumer cannot take it, because the networks are not linked, the consumer is
 * not loaded or its CPU is out of sight, stays in the executing network's storage and is counted as held for that
 * consumer, at most what is owed. Once the consumer can take it again, that much is handed back from the executing
 * network's own storage.
 *
 * <p>Two more things are kept with the debts. The consumer's jobs seen at each push ({@link Job}): AE2's own record of
 * where their CPU is and whether the job finished or was cancelled, so a CPU that is merely unloaded is never taken for
 * a cancelled job. And the executing network's transit stock: what was taken out of its storage to hand back but was
 * accepted neither by the consumer nor by the storage it came from. It is real stock, not a number, and stays with that
 * network until its storage or the consumer takes it.
 */
public final class CraftingReturnLedger extends SavedData {
    public static final String DATA_NAME = "ae2federation_crafting_returns";
    private static final Factory<CraftingReturnLedger> FACTORY =
            new Factory<>(CraftingReturnLedger::new, CraftingReturnLedger::load);

    /** {@code held} of the {@code amount} owed arrived while the consumer could not take it. */
    public record Owed(NetworkId executing, NetworkId consumer, AEKey key, long amount, long held) {
    }

    private static final class Debt {
        private long owed;
        private long held;
    }

    /** How a watched job stands: still running (or not seen ending), or ended as AE2 recorded it. */
    public enum JobEnd {
        RUNNING, DONE, CANCELLED
    }

    /**
     * A job a consumer's CPU ran when one of its pushes went through a projection: AE2's crafting id of the job, and the
     * CPU's dimension and lowest corner, where it is looked for while the consumer is owed anything.
     */
    public record Job(UUID craftingId, String dimension, BlockPos cpu, JobEnd end) {
    }

    /** Executing network, then key, then consumer, in insertion order so returns are handed out deterministically. */
    private final Map<NetworkId, Map<AEKey, Map<NetworkId, Debt>>> owed = new LinkedHashMap<>();
    /** By consumer, its jobs seen at a push, by crafting id. */
    private final Map<NetworkId, Map<UUID, Job>> jobs = new LinkedHashMap<>();
    /** By executing network, the stock taken out to hand back that nothing took yet. */
    private final Map<NetworkId, KeyCounter> transit = new LinkedHashMap<>();

    public static CraftingReturnLedger get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    void add(NetworkId executing, NetworkId consumer, AEKey key, long amount) {
        if (amount <= 0) return;
        owed.computeIfAbsent(executing, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(key, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(consumer, ignored -> new Debt()).owed += amount;
        setDirty();
    }

    /** Whether {@code executing}'s machines owe anyone {@code key}. */
    boolean owes(NetworkId executing, AEKey key) {
        var keys = owed.get(executing);
        return keys != null && keys.containsKey(key);
    }

    /** How much of {@code key} {@code executing}'s machines still owe {@code consumer}, for tests and diagnostics. */
    public long owed(NetworkId executing, NetworkId consumer, AEKey key) {
        var debt = debt(executing, consumer, key);
        return debt == null ? 0 : debt.owed;
    }

    /** How much of what {@code consumer} is owed waits in {@code executing}'s storage, for tests and diagnostics. */
    public long held(NetworkId executing, NetworkId consumer, AEKey key) {
        var debt = debt(executing, consumer, key);
        return debt == null ? 0 : debt.held;
    }

    private Debt debt(NetworkId executing, NetworkId consumer, AEKey key) {
        var keys = owed.get(executing);
        var consumers = keys == null ? null : keys.get(key);
        return consumers == null ? null : consumers.get(consumer);
    }

    boolean owesAnything(NetworkId executing) {
        return owed.containsKey(executing);
    }

    /** The consumers owed {@code key} by {@code executing} and how much each, in the order they pushed. */
    Map<NetworkId, Long> consumers(NetworkId executing, AEKey key) {
        var keys = owed.get(executing);
        var consumers = keys == null ? null : keys.get(key);
        if (consumers == null) return Map.of();
        var amounts = new LinkedHashMap<NetworkId, Long>();
        consumers.forEach((consumer, debt) -> amounts.put(consumer, debt.owed));
        return amounts;
    }

    /**
     * Lowers what {@code executing} owes {@code consumer} of {@code key}, by what was just handed back: by
     * {@code held} of what waited in the executing network's storage and the rest as it arrived.
     */
    void take(NetworkId executing, NetworkId consumer, AEKey key, long amount, long held) {
        if (amount <= 0) return;
        var debt = debt(executing, consumer, key);
        if (debt == null) return;
        if (debt.owed <= amount) {
            drop(executing, consumer, key);
            return;
        }
        debt.owed -= amount;
        debt.held = Math.min(debt.owed, Math.max(0, debt.held - held));
        setDirty();
    }

    /**
     * Counts {@code amount} of {@code key} as left in {@code executing}'s storage for {@code consumer}, which could not
     * take it, at most what is owed.
     *
     * @return how much was counted
     */
    long hold(NetworkId executing, NetworkId consumer, AEKey key, long amount) {
        var debt = debt(executing, consumer, key);
        if (debt == null || amount <= 0) return 0;
        long counted = Math.min(amount, debt.owed - debt.held);
        if (counted > 0) {
            debt.held += counted;
            setDirty();
        }
        return Math.max(0, counted);
    }

    /** Forgets a debt: the consumer's job no longer waits for the key (it finished or was cancelled). */
    void drop(NetworkId executing, NetworkId consumer, AEKey key) {
        var keys = owed.get(executing);
        var consumers = keys == null ? null : keys.get(key);
        if (consumers == null || consumers.remove(consumer) == null) return;
        if (consumers.isEmpty()) keys.remove(key);
        if (keys.isEmpty()) owed.remove(executing);
        setDirty();
    }

    /** Watches a consumer job seen at a push, unless it is watched already. */
    void watch(NetworkId consumer, UUID craftingId, String dimension, BlockPos cpu) {
        var watched = jobs.computeIfAbsent(consumer, ignored -> new LinkedHashMap<>());
        var current = watched.get(craftingId);
        if (current != null && current.dimension().equals(dimension) && current.cpu().equals(cpu)) return;
        watched.put(craftingId, new Job(craftingId, dimension, cpu.immutable(),
                current == null ? JobEnd.RUNNING : current.end()));
        setDirty();
    }

    /** The jobs watched for {@code consumer}, in the order they were first seen. */
    public List<Job> jobs(NetworkId consumer) {
        var watched = jobs.get(consumer);
        return watched == null ? List.of() : List.copyOf(watched.values());
    }

    /** Records how a watched job ended. */
    void end(NetworkId consumer, UUID craftingId, JobEnd end) {
        var watched = jobs.get(consumer);
        var job = watched == null ? null : watched.get(craftingId);
        if (job == null || job.end() == end) return;
        watched.put(craftingId, new Job(craftingId, job.dimension(), job.cpu(), end));
        setDirty();
    }

    /** Forgets the jobs of every consumer that is owed nothing any more; returns their crafting ids. */
    List<UUID> forgetJobsOfSettledConsumers() {
        var owedConsumers = new java.util.HashSet<NetworkId>();
        owed.values().forEach(keys -> keys.values().forEach(consumers -> owedConsumers.addAll(consumers.keySet())));
        var forgotten = new java.util.ArrayList<UUID>();
        var iterator = jobs.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (owedConsumers.contains(entry.getKey())) continue;
            forgotten.addAll(entry.getValue().keySet());
            iterator.remove();
        }
        if (!forgotten.isEmpty()) setDirty();
        return forgotten;
    }

    /** Adds stock taken out of {@code executing}'s storage that nothing took back. */
    void addTransit(NetworkId executing, AEKey key, long amount) {
        if (amount <= 0) return;
        transit.computeIfAbsent(executing, ignored -> new KeyCounter()).add(key, amount);
        setDirty();
    }

    /** Takes up to {@code amount} of {@code key} out of {@code executing}'s transit stock; returns how much. */
    long takeTransit(NetworkId executing, AEKey key, long amount, Actionable mode) {
        var stock = transit.get(executing);
        if (stock == null || amount <= 0) return 0;
        long taken = Math.min(amount, stock.get(key));
        if (taken <= 0 || mode == Actionable.SIMULATE) return Math.max(0, taken);
        stock.remove(key, taken);
        stock.removeZeros();
        if (stock.isEmpty()) transit.remove(executing);
        setDirty();
        return taken;
    }

    /** What {@code executing}'s transit stock holds of {@code key}. */
    public long transit(NetworkId executing, AEKey key) {
        var stock = transit.get(executing);
        return stock == null ? 0 : stock.get(key);
    }

    /** {@code executing}'s whole transit stock, a copy. */
    KeyCounter transit(NetworkId executing) {
        var copy = new KeyCounter();
        var stock = transit.get(executing);
        if (stock != null) copy.addAll(stock);
        return copy;
    }

    /** The networks with transit stock. */
    java.util.Set<NetworkId> transitNetworks() {
        return java.util.Set.copyOf(transit.keySet());
    }

    List<Owed> entries() {
        var entries = new java.util.ArrayList<Owed>();
        owed.forEach((executing, keys) -> keys.forEach((key, consumers) -> consumers.forEach((consumer, debt) ->
                entries.add(new Owed(executing, consumer, key, debt.owed, debt.held)))));
        return List.copyOf(entries);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var entry : entries()) {
            var row = new CompoundTag();
            row.put("executing", NbtUtils.createUUID(entry.executing().value()));
            row.put("consumer", NbtUtils.createUUID(entry.consumer().value()));
            row.put("key", entry.key().toTagGeneric(registries));
            row.putLong("amount", entry.amount());
            row.putLong("held", entry.held());
            list.add(row);
        }
        tag.put("owed", list);
        var jobList = new ListTag();
        jobs.forEach((consumer, watched) -> watched.values().forEach(job -> {
            var row = new CompoundTag();
            row.put("consumer", NbtUtils.createUUID(consumer.value()));
            row.put("craftingId", NbtUtils.createUUID(job.craftingId()));
            row.putString("dimension", job.dimension());
            row.putLong("cpu", job.cpu().asLong());
            row.putString("end", job.end().name());
            jobList.add(row);
        }));
        tag.put("jobs", jobList);
        var transitList = new ListTag();
        transit.forEach((executing, stock) -> {
            for (var entry : stock) {
                var row = new CompoundTag();
                row.put("executing", NbtUtils.createUUID(executing.value()));
                row.put("key", entry.getKey().toTagGeneric(registries));
                row.putLong("amount", entry.getLongValue());
                transitList.add(row);
            }
        });
        tag.put("transit", transitList);
        return tag;
    }

    /** Reads a ledger saved by {@link #save}, as the world does when it loads. */
    public static CraftingReturnLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        var ledger = new CraftingReturnLedger();
        for (var element : tag.getList("owed", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) element;
            var key = AEKey.fromTagGeneric(registries, row.getCompound("key"));
            // A key whose item or fluid no longer exists (a removed mod) is owed to nobody.
            if (key == null) continue;
            var executing = new NetworkId(NbtUtils.loadUUID(row.get("executing")));
            var consumer = new NetworkId(NbtUtils.loadUUID(row.get("consumer")));
            ledger.add(executing, consumer, key, row.getLong("amount"));
            ledger.hold(executing, consumer, key, row.getLong("held"));
        }
        for (var element : tag.getList("jobs", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) element;
            var consumer = new NetworkId(NbtUtils.loadUUID(row.get("consumer")));
            var craftingId = NbtUtils.loadUUID(row.get("craftingId"));
            ledger.watch(consumer, craftingId, row.getString("dimension"), BlockPos.of(row.getLong("cpu")));
            ledger.end(consumer, craftingId, JobEnd.valueOf(row.getString("end")));
        }
        for (var element : tag.getList("transit", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) element;
            var key = AEKey.fromTagGeneric(registries, row.getCompound("key"));
            // Stock of an item or fluid that no longer exists (a removed mod) is gone, as it is from a drive.
            if (key == null) continue;
            ledger.addTransit(new NetworkId(NbtUtils.loadUUID(row.get("executing"))), key, row.getLong("amount"));
        }
        ledger.setDirty(false);
        return ledger;
    }
}
