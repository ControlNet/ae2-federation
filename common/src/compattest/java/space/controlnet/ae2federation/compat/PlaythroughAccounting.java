package space.controlnet.ae2federation.compat;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import space.controlnet.ae2federation.crafting.projection.CraftingReturnLedger;
import space.controlnet.ae2federation.identity.NetworkId;

/** Exact item accounting and complete fixture-owned return-state snapshots. */
final class PlaythroughAccounting {
    record Signature(Map<String, Long> outputs, Map<String, Long> remainders) { }

    static String name(AEKey key, ServerLevel level) {
        if (key instanceof AEItemKey item && !item.toStack().getComponentsPatch().isEmpty()) {
            return item.toStack().save(level.registryAccess()).toString();
        }
        return key.toString();
    }

    static void add(Map<String, Long> stock, String key, long amount) {
        stock.merge(key, amount, Math::addExact);
        if (stock.get(key) == 0) stock.remove(key);
    }

    /** {@code after - before}, key by key, without zero entries. */
    static Map<String, Long> difference(Map<String, Long> before, Map<String, Long> after) {
        var result = new TreeMap<String, Long>(after);
        before.forEach((key, amount) -> add(result, key, -amount));
        return result;
    }

    static Signature signature(IPatternDetails details, ServerLevel level) {
        var outputs = new TreeMap<String, Long>();
        var remainders = new TreeMap<String, Long>();
        for (var output : details.getOutputs()) add(outputs, name(output.what(), level), output.amount());
        for (var input : details.getInputs()) {
            var stack = input.getPossibleInputs()[0];
            var remainder = input.getRemainingKey(stack.what());
            if (remainder != null) add(remainders, name(remainder, level), input.getMultiplier());
        }
        return new Signature(outputs, remainders);
    }

    static Signature signature(ItemStack output, List<ItemStack> remaining, ServerLevel level) {
        var outputs = new TreeMap<String, Long>();
        var remainders = new TreeMap<String, Long>();
        if (!output.isEmpty()) add(outputs, name(AEItemKey.of(output), level), output.getCount());
        for (var stack : remaining) if (!stack.isEmpty()) add(remainders, name(AEItemKey.of(stack), level), stack.getCount());
        return new Signature(outputs, remainders);
    }

    static Map<String, Long> expected(ICraftingPlan plan, ServerLevel level) {
        var stock = new TreeMap<String, Long>();
        for (var entry : plan.usedItems()) add(stock, name(entry.getKey(), level), entry.getLongValue());
        for (var entry : plan.patternTimes().entrySet()) {
            long times = entry.getValue();
            var signature = signature(entry.getKey(), level);
            signature.outputs().forEach((key, amount) -> add(stock, key, Math.multiplyExact(times, amount)));
            signature.remainders().forEach((key, amount) -> add(stock, key, Math.multiplyExact(times, amount)));
            for (var input : entry.getKey().getInputs()) {
                var stack = input.getPossibleInputs()[0];
                add(stock, name(stack.what(), level), -Math.multiplyExact(times, Math.multiplyExact(stack.amount(), input.getMultiplier())));
            }
        }
        if (stock.values().stream().anyMatch(amount -> amount < 0)) throw new IllegalStateException("Negative planned inventory: " + stock);
        return stock;
    }

    static Map<String, Long> contents(MEStorage storage, ServerLevel level) {
        var counter = new KeyCounter(); storage.getAvailableStacks(counter);
        var stock = new TreeMap<String, Long>();
        for (var entry : counter) if (entry.getLongValue() != 0) add(stock, name(entry.getKey(), level), entry.getLongValue());
        return stock;
    }

    static Map<String, List<String>> ledger(ServerLevel level, Set<NetworkId> networks) {
        var snapshot = CraftingReturnLedger.get(level).save(new CompoundTag(), level.registryAccess());
        var result = new TreeMap<String, List<String>>();
        for (var category : List.of("owed", "transit", "jobs")) {
            var entries = new java.util.ArrayList<String>();
            for (var element : snapshot.getList(category, Tag.TAG_COMPOUND)) {
                var row = (CompoundTag) element;
                boolean owned = false;
                for (var side : List.of("executing", "consumer")) if (row.contains(side)) {
                    owned |= networks.contains(new NetworkId(NbtUtils.loadUUID(row.get(side))));
                }
                if (owned) entries.add(row.toString());
            }
            result.put(category, entries.stream().sorted().toList());
        }
        var unknown = new java.util.ArrayList<String>();
        for (var entry : snapshot.getList("unknownJobs", Tag.TAG_INT_ARRAY)) {
            var network = new NetworkId(NbtUtils.loadUUID(entry));
            if (networks.contains(network)) unknown.add(network.toString());
        }
        result.put("unknownJobs", unknown.stream().sorted().toList());
        return result;
    }

    // Teardown discards only test-owned state after its complete snapshot has been recorded.
    @SuppressWarnings("unchecked")
    static void clearLedger(ServerLevel level, Set<NetworkId> networks) throws ReflectiveOperationException {
        var ledger = CraftingReturnLedger.get(level);
        var field = CraftingReturnLedger.class.getDeclaredField("owed"); field.setAccessible(true);
        var debts = (Map<NetworkId, Map<AEKey, Map<NetworkId, ?>>>) field.get(ledger);
        debts.keySet().removeAll(networks);
        debts.values().forEach(keys -> {
            keys.values().forEach(consumers -> consumers.keySet().removeAll(networks));
            keys.values().removeIf(Map::isEmpty);
        });
        debts.values().removeIf(Map::isEmpty);
        field = CraftingReturnLedger.class.getDeclaredField("transit"); field.setAccessible(true);
        ((Map<NetworkId, ?>) field.get(ledger)).keySet().removeAll(networks);
        // Leave watched jobs to the normal service sweep, which also releases its crafting-link cache.
        ledger.setDirty();
        var remaining = ledger(level, networks);
        if (!remaining.get("owed").isEmpty() || !remaining.get("transit").isEmpty()) {
            throw new IllegalStateException("Fixture return stock survived teardown");
        }
    }

    private PlaythroughAccounting() { }
}
