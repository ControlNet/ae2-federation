package space.controlnet.ae2federation.compat;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.item.Items;

/** Resource assumptions and their evidence from the loaded pack. */
final class QuestCoverageResources {
    // Geological materials bypass mining and initial refining, even when the pack also offers processing recipes.
    private static final List<String> MINERALS = List.of("c:ores", "c:raw_materials", "c:gems", "c:ingots",
            "c:nuggets", "c:dusts", "c:storage_blocks", "c:stones", "c:cobblestones", "c:sands", "c:gravels", "c:obsidians");
    // Gathered wood and plants bypass harvesting and farming.
    private static final List<String> GATHERED = List.of("minecraft:logs", "c:crops", "c:seeds", "c:rods/wooden");
    // Blocks without an item or a known producer are world resources, so their possible drops bypass gathering.
    private static final String NATURAL_BLOCKS = "loot:natural_blocks";
    // Retain only ore/crystal drops missed by the general natural-block rule.
    private static final String ORE_BLOCK_TAG = "c:ores";
    private static final List<String> CRYSTAL_BLOCK_NAMES = List.of("budding", "cluster");
    // Mob loot is gathered stock, just like mined ore, without requiring combat or mob farms.
    private static final String ENTITY_LOOT_PREFIX = "entities/";
    // Closed conversion cycles need an initial stock; supplying that stock avoids requiring mining.
    private static final String CLOSED_CYCLES = "closed_recipe_cycles";

    private final Map<String, Set<String>> rules = new LinkedHashMap<>();
    private final Map<String, String> tagStatus = new LinkedHashMap<>();
    private final Map<String, Set<String>> lootDrops = new TreeMap<>();
    private final Set<String> naturalBlocks = new TreeSet<>();
    private final Map<String, Set<String>> naturalDropsByMod = new TreeMap<>();
    private final Map<String, Set<String>> supplementalBlocks = new TreeMap<>();
    private final Map<String, Set<String>> smeltedItems = new TreeMap<>();
    private final Map<String, Set<String>> entityLootDrops = new TreeMap<>();
    private final List<Map<String, Object>> cycleComponents = new ArrayList<>();
    private final Map<String, String> lootProblems = new TreeMap<>();
    private final List<Set<String>> closedCycles = new ArrayList<>();
    private final MinecraftServer server;

    QuestCoverageResources(MinecraftServer server) {
        this.server = server;
    }

    Set<String> read(Map<String, List<QuestCoverageCompatGameTests.Recipe>> producers, Set<String> targets) {
        producers.values().forEach(recipes -> recipes.stream()
                .filter(recipe -> recipe.type().equals("minecraft:smelting") && recipe.ingredientsReadable())
                .forEach(recipe -> recipe.ingredients().forEach(slot -> slot.forEach(item ->
                        smeltedItems.computeIfAbsent(item, ignored -> new TreeSet<>()).add(recipe.output())))));
        // Preserve the strict assumption for items with no readable output producer.
        var noProducer = new TreeSet<>(targets);
        producers.values().forEach(recipes -> recipes.forEach(recipe -> recipe.ingredients().forEach(noProducer::addAll)));
        noProducer.removeAll(producers.keySet());
        rules.put("no_readable_producer", noProducer);
        for (var tags : List.of(MINERALS, GATHERED)) {
            for (var name : tags) {
                var key = TagKey.create(Registries.ITEM, ResourceLocation.parse(name));
                var holders = BuiltInRegistries.ITEM.getTag(key);
                var items = new TreeSet<String>();
                holders.ifPresent(values -> values.forEach(value -> items.add(BuiltInRegistries.ITEM.getKey(value.value()).toString())));
                rules.put("tag:" + name, items);
                tagStatus.put(name, holders.isEmpty() ? "missing" : items.isEmpty() ? "empty" : "present");
            }
        }
        var oreTag = TagKey.create(Registries.BLOCK, ResourceLocation.parse(ORE_BLOCK_TAG));
        var oreDrops = new TreeSet<String>();
        var crystalDrops = new TreeSet<String>();
        var naturalDrops = new TreeSet<String>();
        for (var block : BuiltInRegistries.BLOCK) {
            var id = BuiltInRegistries.BLOCK.getKey(block);
            boolean ore = block.builtInRegistryHolder().is(oreTag);
            boolean crystal = CRYSTAL_BLOCK_NAMES.stream().anyMatch(id.getPath()::contains);
            for (Class<?> type = block.getClass(); type != null && !crystal; type = type.getSuperclass()) {
                var name = type.getSimpleName().toLowerCase(java.util.Locale.ROOT);
                crystal = CRYSTAL_BLOCK_NAMES.stream().anyMatch(name::contains);
            }
            boolean natural = block.asItem() == Items.AIR
                    || !producers.containsKey(BuiltInRegistries.ITEM.getKey(block.asItem()).toString());
            if (!natural && !ore && !crystal) continue;
            var drops = new TreeSet<String>();
            collectTable(block.getLootTable(), drops, new HashSet<>());
            lootDrops.put(id.toString(), drops);
            if (natural) {
                naturalBlocks.add(id.toString());
                naturalDrops.addAll(drops);
                naturalDropsByMod.computeIfAbsent(id.getNamespace(), ignored -> new TreeSet<>()).addAll(drops);
            } else {
                supplementalBlocks.put(id.toString(), new TreeSet<>(drops));
            }
            if (ore) oreDrops.addAll(drops);
            if (crystal) crystalDrops.addAll(drops);
        }
        tagStatus.put("block:" + ORE_BLOCK_TAG, BuiltInRegistries.BLOCK.getTag(oreTag)
                .map(tag -> tag.size() == 0 ? "empty" : "present").orElse("missing"));
        rules.put(NATURAL_BLOCKS, naturalDrops);
        oreDrops.removeAll(naturalDrops);
        crystalDrops.removeAll(naturalDrops);
        supplementalBlocks.values().forEach(items -> items.removeAll(naturalDrops));
        supplementalBlocks.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        if (!oreDrops.isEmpty()) rules.put("loot:ore_blocks_supplement", oreDrops);
        if (!crystalDrops.isEmpty()) rules.put("loot:budding_or_cluster_blocks_supplement", crystalDrops);
        var entityTables = new TreeSet<ResourceLocation>();
        var loadedTables = new HashSet<>(server.reloadableRegistries().getKeys(Registries.LOOT_TABLE));
        loadedTables.stream().filter(id -> id.getPath().startsWith(ENTITY_LOOT_PREFIX)).forEach(entityTables::add);
        for (var type : BuiltInRegistries.ENTITY_TYPE) {
            var key = type.getDefaultLootTable().location();
            if (loadedTables.contains(key)) entityTables.add(key);
        }
        var entityDrops = new TreeSet<String>();
        for (var id : entityTables) {
            var drops = new TreeSet<String>();
            collectTable(ResourceKey.create(Registries.LOOT_TABLE, id), drops, new HashSet<>());
            entityLootDrops.put(id.toString(), drops);
            entityDrops.addAll(drops);
        }
        rules.put("loot:entities", entityDrops);
        findClosedCycles(producers);
        var cycleItems = new TreeSet<String>();
        closedCycles.forEach(cycleItems::addAll);
        rules.put(CLOSED_CYCLES, cycleItems);
        var supplied = new TreeSet<String>();
        rules.values().forEach(supplied::addAll);
        return supplied;
    }

    private void collectTable(ResourceKey<LootTable> key, Set<String> items, Set<ResourceKey<LootTable>> visited) {
        if (!visited.add(key)) return;
        try {
            var registries = server.reloadableRegistries();
            var table = registries.get().registryOrThrow(Registries.LOOT_TABLE).get(key);
            if (table == null) {
                lootProblems.put(key.location().toString(), "missing table");
                return;
            }
            var encoded = LootTable.DIRECT_CODEC.encodeStart(registries.get().createSerializationContext(JsonOps.INSTANCE), table);
            var json = encoded.result();
            if (json.isPresent()) collectLoot(json.get(), items, visited, false);
            else lootProblems.put(key.location().toString(), "table codec could not encode");
        } catch (RuntimeException exception) {
            lootProblems.put(key.location().toString(), exception.getClass().getSimpleName());
        } finally {
            visited.remove(key);
        }
    }

    private void collectLoot(JsonElement value, Set<String> items, Set<ResourceKey<LootTable>> visited, boolean entryContext) {
        if (value.isJsonArray()) {
            value.getAsJsonArray().forEach(entry -> collectLoot(entry, items, visited, entryContext));
        } else if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            var local = new TreeSet<String>();
            var type = object.has("type") ? object.get("type").getAsString() : "";
            if (type.equals("minecraft:item") && object.has("name")) addItem(object.get("name"), local);
            if (type.equals("minecraft:tag") && object.has("name")) {
                var key = TagKey.create(Registries.ITEM, ResourceLocation.parse(object.get("name").getAsString()));
                BuiltInRegistries.ITEM.getTag(key).ifPresent(tag -> tag.forEach(item -> local.add(BuiltInRegistries.ITEM.getKey(item.value()).toString())));
            }
            if (type.equals("minecraft:loot_table") && object.has("value") && object.get("value").isJsonPrimitive()) {
                collectTable(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(object.get("value").getAsString())), local, visited);
            }
            if (object.has("function") && object.get("function").getAsString().equals("minecraft:set_item")) {
                addItem(object.get("item"), local);
            }
            if (entryContext && !type.isEmpty() && !Set.of("minecraft:item", "minecraft:tag", "minecraft:loot_table", "minecraft:empty",
                    "minecraft:alternatives", "minecraft:sequence", "minecraft:group", "minecraft:block").contains(type)) {
                lootProblems.put(type, "nonstandard loot entry; static drop enumeration may be incomplete");
            }
            object.entrySet().forEach(entry -> collectLoot(entry.getValue(), local, visited,
                    entry.getKey().equals("entries") || entry.getKey().equals("children")));
            // Burning mobs can drop smelted items; include function outputs without sampling loot conditions.
            if (object.has("functions") && object.get("functions").isJsonArray()) {
                for (var function : object.getAsJsonArray("functions")) {
                    if (!function.isJsonObject() || !function.getAsJsonObject().has("function")) continue;
                    var name = function.getAsJsonObject().get("function").getAsString();
                    if (name.equals("minecraft:furnace_smelt")) {
                        for (var item : new TreeSet<>(local)) local.addAll(smeltedItems.getOrDefault(item, Set.of()));
                    } else if ((name.equals("minecraft:enchant_randomly") || name.equals("minecraft:enchant_with_levels"))
                            && local.contains("minecraft:book")) {
                        local.add("minecraft:enchanted_book");
                    }
                }
            }
            items.addAll(local);
        }
    }

    static void addItem(JsonElement value, Set<String> items) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return;
        var id = ResourceLocation.tryParse(value.getAsString());
        if (id != null && BuiltInRegistries.ITEM.containsKey(id) && !id.toString().equals("minecraft:air")) items.add(id.toString());
    }

    private void findClosedCycles(Map<String, List<QuestCoverageCompatGameTests.Recipe>> producers) {
        var graph = new TreeMap<String, Set<String>>();
        var reverse = new TreeMap<String, Set<String>>();
        producers.forEach((output, recipes) -> {
            graph.computeIfAbsent(output, ignored -> new TreeSet<>());
            for (var recipe : recipes) if (recipe.ingredientsReadable()) {
                for (var alternatives : recipe.ingredients()) for (var item : alternatives) {
                    graph.get(output).add(item);
                    reverse.computeIfAbsent(item, ignored -> new TreeSet<>()).add(output);
                }
            }
        });
        var order = new ArrayList<String>();
        var seen = new HashSet<String>();
        record Frame(String item, Iterator<String> edges) { }
        for (var root : graph.keySet()) {
            if (!seen.add(root)) continue;
            var stack = new ArrayDeque<Frame>();
            stack.push(new Frame(root, graph.get(root).iterator()));
            while (!stack.isEmpty()) {
                var frame = stack.peek();
                if (frame.edges().hasNext()) {
                    var child = frame.edges().next();
                    if (seen.add(child)) stack.push(new Frame(child, graph.getOrDefault(child, Set.of()).iterator()));
                } else {
                    order.add(stack.pop().item());
                }
            }
        }
        seen.clear();
        for (int i = order.size() - 1; i >= 0; i--) {
            var root = order.get(i);
            if (!seen.add(root)) continue;
            var component = new TreeSet<String>();
            var stack = new ArrayDeque<String>();
            stack.push(root);
            while (!stack.isEmpty()) {
                var item = stack.pop();
                component.add(item);
                for (var child : reverse.getOrDefault(item, Set.of())) if (seen.add(child)) stack.push(child);
            }
            if (component.size() == 1 && !graph.getOrDefault(root, Set.of()).contains(root)) continue;
            var entering = component.stream().flatMap(item -> producers.getOrDefault(item, List.of()).stream())
                    .filter(recipe -> entersComponent(recipe, component)).map(QuestCoverageCompatGameTests.Recipe::id)
                    .sorted().toList();
            cycleComponents.add(Map.of("items", component, "closed", entering.isEmpty(), "enteringRecipes", entering));
            if (entering.isEmpty()) closedCycles.add(component);
        }
        closedCycles.sort(java.util.Comparator.comparing(component -> component.iterator().next()));
    }

    // A required internal slot keeps a copy recipe closed, even when its other slots consume outside materials.
    static boolean entersComponent(QuestCoverageCompatGameTests.Recipe recipe, Set<String> component) {
        return recipe.ingredientsReadable() && recipe.ingredients().stream()
                .allMatch(slot -> slot.stream().anyMatch(item -> !component.contains(item)));
    }

    Set<String> independentlyProvided() {
        var result = new TreeSet<String>();
        rules.forEach((rule, items) -> {
            if (!rule.equals("no_readable_producer")) result.addAll(items);
        });
        return result;
    }

    Map<String, Object> report(Set<String> targets) {
        var result = new LinkedHashMap<String, Object>();
        result.put("policy", "Tag and loot targets are listed under both provided and producible when a recipe proves production; never add those counts. "
                + "Block and entity loot enumerate possible item/tag entries and nested tables, including standard smelting/enchanting outputs, ignoring conditions and probabilities. "
                + "Natural blocks have no item or no readable output producer; hidden outputs make this provisional. Ore/crystal rules retain only drops missing from natural blocks. Closed SCCs have no readable recipe whose every slot has an alternative outside the SCC.");
        result.put("tagStatus", tagStatus);
        var counts = new LinkedHashMap<String, Object>();
        rules.forEach((name, items) -> {
            var matched = new TreeSet<>(items);
            matched.retainAll(targets);
            counts.put(name, Map.of("items", items, "itemCount", items.size(), "targets", matched, "targetCount", matched.size()));
        });
        result.put("rules", counts);
        result.put("naturalBlocks", naturalBlocks);
        result.put("naturalBlockCount", naturalBlocks.size());
        result.put("naturalBlockMods", naturalDropsByMod.entrySet().stream()
                .sorted(java.util.Comparator.<Map.Entry<String, Set<String>>>comparingInt(entry -> entry.getValue().size())
                        .reversed().thenComparing(Map.Entry::getKey))
                .map(entry -> Map.of("mod", entry.getKey(), "items", entry.getValue(), "itemCount", entry.getValue().size(),
                        "blockCount", naturalBlocks.stream().filter(id -> id.startsWith(entry.getKey() + ":")).count())).toList());
        result.put("supplementalOreCrystalBlocks", supplementalBlocks);
        result.put("blockLootDrops", lootDrops);
        result.put("entityLootDrops", entityLootDrops);
        result.put("cycleComponents", cycleComponents);
        result.put("lootProblems", lootProblems);
        result.put("closedCycles", closedCycles);
        return result;
    }
}
