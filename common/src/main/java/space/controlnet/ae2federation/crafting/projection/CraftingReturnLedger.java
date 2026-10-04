package space.controlnet.ae2federation.crafting.projection;

import appeng.api.stacks.AEKey;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 */
public final class CraftingReturnLedger extends SavedData {
    private static final String DATA_NAME = "ae2federation_crafting_returns";
    private static final Factory<CraftingReturnLedger> FACTORY =
            new Factory<>(CraftingReturnLedger::new, CraftingReturnLedger::load);

    public record Owed(NetworkId executing, NetworkId consumer, AEKey key, long amount) {
    }

    /** Executing network, then key, then consumer, in insertion order so returns are handed out deterministically. */
    private final Map<NetworkId, Map<AEKey, Map<NetworkId, Long>>> owed = new LinkedHashMap<>();

    public static CraftingReturnLedger get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    void add(NetworkId executing, NetworkId consumer, AEKey key, long amount) {
        if (amount <= 0) return;
        owed.computeIfAbsent(executing, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(key, ignored -> new LinkedHashMap<>()).merge(consumer, amount, Long::sum);
        setDirty();
    }

    /** Whether {@code executing}'s machines owe anyone {@code key}. */
    boolean owes(NetworkId executing, AEKey key) {
        var keys = owed.get(executing);
        return keys != null && keys.containsKey(key);
    }

    /** How much of {@code key} {@code executing}'s machines still owe {@code consumer}, for tests and diagnostics. */
    public long owed(NetworkId executing, NetworkId consumer, AEKey key) {
        return consumers(executing, key).getOrDefault(consumer, 0L);
    }

    boolean owesAnything(NetworkId executing) {
        return owed.containsKey(executing);
    }

    /** The consumers owed {@code key} by {@code executing} and how much each, in the order they pushed. */
    Map<NetworkId, Long> consumers(NetworkId executing, AEKey key) {
        var keys = owed.get(executing);
        var consumers = keys == null ? null : keys.get(key);
        return consumers == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(consumers));
    }

    /** Lowers what {@code executing} owes {@code consumer} of {@code key}, by what was just handed back. */
    void take(NetworkId executing, NetworkId consumer, AEKey key, long amount) {
        if (amount <= 0) return;
        var keys = owed.get(executing);
        var consumers = keys == null ? null : keys.get(key);
        var current = consumers == null ? null : consumers.get(consumer);
        if (current == null) return;
        if (current <= amount) {
            drop(executing, consumer, key);
        } else {
            consumers.put(consumer, current - amount);
            setDirty();
        }
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

    List<Owed> entries() {
        var entries = new java.util.ArrayList<Owed>();
        owed.forEach((executing, keys) -> keys.forEach((key, consumers) -> consumers.forEach((consumer, amount) ->
                entries.add(new Owed(executing, consumer, key, amount)))));
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
            list.add(row);
        }
        tag.put("owed", list);
        return tag;
    }

    private static CraftingReturnLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        var ledger = new CraftingReturnLedger();
        for (var element : tag.getList("owed", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) element;
            var key = AEKey.fromTagGeneric(registries, row.getCompound("key"));
            // A key whose item or fluid no longer exists (a removed mod) is owed to nobody.
            if (key == null) continue;
            ledger.add(new NetworkId(NbtUtils.loadUUID(row.get("executing"))),
                    new NetworkId(NbtUtils.loadUUID(row.get("consumer"))), key, row.getLong("amount"));
        }
        ledger.setDirty(false);
        return ledger;
    }
}
