package space.controlnet.ae2federation.compat;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Recipe reachability of the pack's quest items, without building a factory. */
@PrefixGameTestTemplate(false)
public final class QuestCoverageCompatGameTests {
    private static final int DEPTH_LIMIT = 64;
    private static final Set<String> COOKING = Set.of("minecraft:smelting", "minecraft:blasting",
            "minecraft:smoking", "minecraft:campfire_cooking");
    private static final Pattern TOKENS = Pattern.compile(
            "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|//[^\\r\\n]*|[{}\\[\\],:]|[^\\s{}\\[\\],:]+");

    private QuestCoverageCompatGameTests() {
    }

    /** Reports strict and supplied-resource quest coverage using tags, natural block/mob loot and closed cycles. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 1200)
    public static void questCoverage(GameTestHelper helper) throws Exception {
        var probe = new Probe();
        probe.readQuests(Path.of("config/ftbquests/quests"));
        probe.readRecipes(helper);
        probe.readResources(helper);
        var report = probe.report();
        var directory = Path.of(System.getProperty("ae2federation.compat.report", "compat-report.json"))
                .toAbsolutePath().getParent();
        Files.createDirectories(directory);
        var gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        Files.writeString(directory.resolve("quest-coverage.json"), gson.toJson(canonical(gson.toJsonTree(report))));
        Files.writeString(directory.resolve("quest-coverage.md"), probe.markdown(report));
        helper.succeed();
    }

    private static JsonElement canonical(JsonElement value) {
        if (value.isJsonObject()) {
            var result = new JsonObject();
            value.getAsJsonObject().keySet().stream().sorted()
                    .forEach(key -> result.add(key, canonical(value.getAsJsonObject().get(key))));
            return result;
        }
        if (value.isJsonArray()) {
            var result = new JsonArray();
            value.getAsJsonArray().forEach(entry -> result.add(canonical(entry)));
            return result;
        }
        return value;
    }

    // Normalize FTB's optional commas and empty keys before vanilla parses the values.
    private static CompoundTag read(Path path) throws Exception {
        var source = Files.readString(path);
        var emptyKey = "__ae2f_empty_key__";
        while (source.contains(emptyKey)) emptyKey += "_";
        var matcher = TOKENS.matcher(source);
        var tokens = new ArrayList<String>();
        while (matcher.find()) {
            if (!matcher.group().startsWith("//")) tokens.add(matcher.group());
        }
        var normalized = new StringBuilder();
        String previous = "";
        for (int index = 0; index < tokens.size(); index++) {
            var token = tokens.get(index);
            if ((token.equals("\"\"") || token.equals("''")) && index + 1 < tokens.size()
                    && tokens.get(index + 1).equals(":")) token = "\"" + emptyKey + "\"";
            if (!previous.isEmpty() && !Set.of("{", "[", ",", ":", "I;", "B;", "L;").contains(previous)
                    && !Set.of("}", "]", ",", ":").contains(token)) normalized.append(',');
            normalized.append(token);
            previous = token;
        }
        var result = TagParser.parseTag(normalized.toString());
        restoreEmptyKeys(result, emptyKey);
        return result;
    }

    // TagParser rejects empty compound keys used by some item components; restore them after parsing.
    private static void restoreEmptyKeys(Tag tag, String escapedKey) {
        if (tag instanceof CompoundTag compound) {
            if (compound.contains(escapedKey)) {
                var value = compound.get(escapedKey);
                compound.remove(escapedKey);
                compound.put("", value);
            }
            for (var key : compound.getAllKeys()) restoreEmptyKeys(compound.get(key), escapedKey);
        } else if (tag instanceof ListTag list) {
            for (var entry : list) restoreEmptyKeys(entry, escapedKey);
        }
    }

    private record Task(String id, String type, String classification, String item, String snbt,
                        boolean hasComponents) { }
    private record Quest(String id, String title, List<String> dependencies, List<Task> tasks,
                         Map<String, Integer> rewards) { }
    private record Chapter(String id, String title, String titleSource, String group, List<Quest> quests) { }
    record Recipe(String id, String type, String recipeClass, String output,
                          List<List<String>> ingredients, boolean ingredientsReadable) { }
    private record Ranking(String recipeClass, int unlocked, int totalReachable, List<String> examples) { }

    private static final class Probe {
        private final List<Chapter> chapters = new ArrayList<>();
        private final Map<String, String> groups = new LinkedHashMap<>();
        private final Map<String, List<Recipe>> producers = new TreeMap<>();
        private final Map<String, Integer> recipeTypes = new TreeMap<>();
        private final Map<String, Integer> unregisteredTypes = new TreeMap<>();
        private final Map<String, Integer> unreadableOutputs = new TreeMap<>();
        private final Map<String, Integer> unreadableIngredients = new TreeMap<>();
        private final Map<String, Integer> taskTypes = new TreeMap<>();
        private final Map<String, Integer> rewardTypes = new TreeMap<>();
        private final Set<String> targets = new TreeSet<>();
        private final Set<String> bases = new TreeSet<>();
        private final Set<String> cycles = new TreeSet<>();
        private final Set<String> depthLimited = new TreeSet<>();
        private final Map<String, Integer> expanded = new HashMap<>();
        private final Map<String, Set<String>> levels = new LinkedHashMap<>();
        private final Map<String, Set<String>> strictLevels = new LinkedHashMap<>();
        private Set<String> providedItems = Set.of();
        private Map<String, Object> resourceReport;
        private Set<String> independentlyProvided = Set.of();
        private String questDataStatus = "available";
        private final List<String> missingQuestMetadata = new ArrayList<>();
        private final List<QuestCoverageAnalysis.OpaqueRecipe> opaqueRecipes = new ArrayList<>();
        private Map<String, Object> blockerReport;
        private final List<Ranking> ranking = new ArrayList<>();
        private final List<String> remainingClasses = new ArrayList<>();

        void readQuests(Path directory) throws Exception {
            if (!Files.isDirectory(directory) || !Files.isDirectory(directory.resolve("chapters"))) {
                questDataStatus = Files.isDirectory(directory) ? "chapters directory unavailable" : "quest root unavailable";
                return;
            }
            var english = optionalMetadata(directory, "lang/en_us.snbt");
            var fallback = optionalMetadata(directory, "lang/zh_cn.snbt");
            for (var entry : optionalMetadata(directory, "chapter_groups.snbt").getList("chapter_groups", Tag.TAG_COMPOUND)) {
                var group = (CompoundTag) entry;
                groups.put(group.getString("id"), title(group, "chapter_group", english, fallback));
            }
            try (var paths = Files.list(directory.resolve("chapters"))) {
                for (var path : paths.filter(p -> p.toString().endsWith(".snbt")).sorted().toList()) {
                    var chapter = read(path);
                    var quests = new ArrayList<Quest>();
                    for (var entry : chapter.getList("quests", Tag.TAG_COMPOUND)) {
                        var quest = (CompoundTag) entry;
                        var tasks = new ArrayList<Task>();
                        for (var taskEntry : quest.getList("tasks", Tag.TAG_COMPOUND)) {
                            var task = (CompoundTag) taskEntry;
                            var type = task.getString("type");
                            taskTypes.merge(type, 1, Integer::sum);
                            var itemTag = task.get("item");
                            var item = itemTag instanceof StringTag string ? string.getAsString()
                                    : task.getCompound("item").getString("id");
                            var classification = !type.equals("item") ? "non-item"
                                    : isFilter(item) ? "filter-or-tag" : validItem(item) ? "plain-item" : "unresolved-item";
                            if (classification.equals("plain-item")) targets.add(item);
                            tasks.add(new Task(task.getString("id"), type, classification, item,
                                    task.toString(), task.getCompound("item").contains("components")));
                        }
                        var rewards = new TreeMap<String, Integer>();
                        for (var reward : quest.getList("rewards", Tag.TAG_COMPOUND)) {
                            var type = ((CompoundTag) reward).getString("type");
                            rewards.merge(type, 1, Integer::sum);
                            rewardTypes.merge(type, 1, Integer::sum);
                        }
                        var dependencies = new ArrayList<String>();
                        for (var dependency : quest.getList("dependencies", Tag.TAG_STRING)) {
                            dependencies.add(dependency.getAsString());
                        }
                        quests.add(new Quest(quest.getString("id"), title(quest, "quest", english, fallback),
                                dependencies, tasks, rewards));
                    }
                    var key = "chapter." + chapter.getString("id") + ".title";
                    chapters.add(new Chapter(chapter.getString("id"), title(chapter, "chapter", english, fallback),
                            english.contains(key) ? "en_us" : chapter.contains("title") ? "inline"
                                    : fallback.contains(key) ? "zh_cn fallback" : "id fallback",
                            chapter.getString("group"), quests));
                }
            }
        }

        private CompoundTag optionalMetadata(Path directory, String name) throws Exception {
            var path = directory.resolve(name);
            if (Files.exists(path)) return read(path);
            missingQuestMetadata.add(name);
            return new CompoundTag();
        }

        private static boolean validItem(String item) {
            var id = ResourceLocation.tryParse(item);
            return id != null && !id.equals(ResourceLocation.withDefaultNamespace("air")) && !item.equals("ftbquests:missing_item")
                    && BuiltInRegistries.ITEM.containsKey(id);
        }

        private static boolean isFilter(String item) {
            return item.startsWith("#") || item.startsWith("ftbfilters:") || item.startsWith("itemfilters:")
                    || item.equals("ftbquests:tag") || item.equals("ftbquests:filter");
        }

        private static String title(CompoundTag tag, String kind, CompoundTag english, CompoundTag fallback) {
            var key = kind + "." + tag.getString("id") + ".title";
            if (english.contains(key)) return english.getString(key);
            if (!tag.getString("title").isEmpty()) return tag.getString("title");
            return fallback.contains(key) ? fallback.getString(key) : tag.getString("id");
        }

        void readRecipes(GameTestHelper helper) {
            for (var holder : helper.getLevel().getRecipeManager().getRecipes()) {
                var recipe = holder.value();
                var registeredType = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
                var type = registeredType == null ? String.valueOf(recipe.getType()) : registeredType.toString();
                if (registeredType == null) {
                    if (ResourceLocation.tryParse(type) == null) {
                        type = "unregistered:" + (recipe.getType() == null ? "null" : recipe.getType().getClass().getName());
                    }
                    unregisteredTypes.merge(type, 1, Integer::sum);
                }
                recipeTypes.merge(type, 1, Integer::sum);
                String output;
                try {
                    var stack = recipe.getResultItem(helper.getLevel().registryAccess());
                    if (stack.isEmpty()) {
                        unreadableOutputs.merge(type, 1, Integer::sum);
                        recordOpaque(helper, holder, type, "output", Set.of());
                        continue;
                    }
                    output = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                } catch (RuntimeException exception) {
                    unreadableOutputs.merge(type, 1, Integer::sum);
                    recordOpaque(helper, holder, type, "output", Set.of());
                    continue;
                }
                var recipeClass = recipe instanceof CraftingRecipe ? "crafting" : COOKING.contains(type) ? "cooking" : type;
                var ingredients = new ArrayList<List<String>>();
                boolean readable = true;
                try {
                    for (var ingredient : recipe.getIngredients()) {
                        if (ingredient == Ingredient.EMPTY) continue;
                        var alternatives = Arrays.stream(ingredient.getItems()).filter(stack -> !stack.isEmpty())
                                .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).distinct().sorted().toList();
                        ingredients.add(alternatives);
                        if (alternatives.isEmpty()) readable = false;
                    }
                    // An empty generic ingredient list does not prove a mod's recipe has no inputs.
                    if (ingredients.isEmpty()) readable = false;
                } catch (RuntimeException exception) {
                    readable = false;
                }
                if (!readable) {
                    unreadableIngredients.merge(type, 1, Integer::sum);
                    recordOpaque(helper, holder, type, "ingredients", Set.of(output));
                }
                producers.computeIfAbsent(output, ignored -> new ArrayList<>()).add(new Recipe(holder.id().toString(),
                        type, recipeClass, output, ingredients, readable));
            }
            producers.values().forEach(recipes -> recipes.sort(java.util.Comparator.comparing(Recipe::id)));
            opaqueRecipes.sort(java.util.Comparator.comparing(QuestCoverageAnalysis.OpaqueRecipe::id)
                    .thenComparing(QuestCoverageAnalysis.OpaqueRecipe::type)
                    .thenComparing(QuestCoverageAnalysis.OpaqueRecipe::unreadable));
            for (var target : targets) expand(target, 0, new HashSet<>());
        }

        private void expand(String item, int depth, Set<String> path) {
            if (!producers.containsKey(item)) {
                bases.add(item);
                return;
            }
            if (path.contains(item)) {
                cycles.add(item);
                return;
            }
            if (depth >= DEPTH_LIMIT) {
                depthLimited.add(item);
                return;
            }
            if (expanded.getOrDefault(item, Integer.MAX_VALUE) <= depth) return;
            expanded.put(item, depth);
            path.add(item);
            for (var recipe : producers.get(item)) {
                for (var ingredient : recipe.ingredients()) {
                    for (var alternative : ingredient) expand(alternative, depth + 1, path);
                }
            }
            path.remove(item);
        }

        private void recordOpaque(GameTestHelper helper, net.minecraft.world.item.crafting.RecipeHolder<?> holder,
                                  String type, String reason, Set<String> knownOutput) {
            var outputs = new TreeSet<>(knownOutput);
            var evidence = new TreeMap<String, Set<String>>();
            String status = "getResultItem";
            if (knownOutput.isEmpty()) {
                try {
                    var encoded = net.minecraft.world.item.crafting.Recipe.CODEC.encodeStart(
                            helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE), holder.value()).result();
                    if (encoded.isPresent() && encoded.get().isJsonObject()) {
                        var json = encoded.get().getAsJsonObject();
                        for (var field : List.of("output", "outputs", "result", "results", "item_output", "item_outputs")) {
                            if (!json.has(field)) continue;
                            var ids = new TreeSet<String>();
                            collectOutputIds(json.get(field), ids);
                            if (!ids.isEmpty()) evidence.put(field, ids);
                            outputs.addAll(ids);
                        }
                    }
                    status = outputs.isEmpty() ? "unattributed" : "serialized output fields (candidate evidence only)";
                } catch (RuntimeException exception) {
                    status = "codec unavailable: " + exception.getClass().getSimpleName();
                }
            }
            opaqueRecipes.add(new QuestCoverageAnalysis.OpaqueRecipe(holder.id().toString(), type, reason, outputs, status, evidence));
        }

        private static void collectOutputIds(JsonElement value, Set<String> outputs) {
            if (value.isJsonPrimitive()) QuestCoverageResources.addItem(value, outputs);
            else if (value.isJsonArray()) value.getAsJsonArray().forEach(entry -> collectOutputIds(entry, outputs));
            else if (value.isJsonObject()) {
                var object = value.getAsJsonObject();
                for (var key : List.of("id", "item", "output", "outputs", "result", "results", "stack")) {
                    if (object.has(key)) collectOutputIds(object.get(key), outputs);
                }
            }
        }

        void readResources(GameTestHelper helper) {
            var resources = new QuestCoverageResources(helper.getLevel().getServer());
            providedItems = resources.read(producers, targets);
            resourceReport = resources.report(targets);
            independentlyProvided = resources.independentlyProvided();
        }

        private Set<String> reachable(Set<String> classes, Set<String> supplied) {
            var produced = QuestCoverageAnalysis.produced(producers, classes, supplied, DEPTH_LIMIT);
            produced.retainAll(targets);
            return new TreeSet<>(produced);
        }

        Map<String, Object> report() {
            var classes = new TreeSet<>(Set.of("crafting"));
            strictLevels.put("crafting", reachable(classes, Set.of()));
            levels.put("crafting", reachable(classes, providedItems));
            classes.addAll(Set.of("ae2:inscriber", "ae2:charger"));
            strictLevels.put("crafting_ae2", reachable(classes, Set.of()));
            levels.put("crafting_ae2", reachable(classes, providedItems));
            classes.add("cooking");
            strictLevels.put("crafting_ae2_cooking", reachable(classes, Set.of()));
            levels.put("crafting_ae2_cooking", reachable(classes, providedItems));
            blockerReport = new QuestCoverageAnalysis(producers, targets, providedItems, independentlyProvided, opaqueRecipes, DEPTH_LIMIT).report(classes);
            addChapterBlockers();
            var candidates = new TreeSet<String>();
            producers.values().forEach(recipes -> recipes.forEach(recipe -> candidates.add(recipe.recipeClass())));
            candidates.removeAll(classes);
            var current = levels.get("crafting_ae2_cooking");
            while (!candidates.isEmpty()) {
                String best = null;
                Set<String> bestReachable = current;
                for (var candidate : candidates) {
                    var trial = new TreeSet<>(classes);
                    trial.add(candidate);
                    var result = reachable(trial, providedItems);
                    if (result.size() > bestReachable.size()) {
                        best = candidate;
                        bestReachable = result;
                    }
                }
                if (best == null) break;
                var examples = new TreeSet<>(bestReachable);
                examples.removeAll(current);
                ranking.add(new Ranking(best, examples.size(), bestReachable.size(), examples.stream().limit(3).toList()));
                candidates.remove(best);
                classes.add(best);
                current = bestReachable;
            }
            remainingClasses.addAll(candidates);
            var report = new LinkedHashMap<String, Object>();
            report.put("schemaVersion", 2);
            report.put("depthLimit", DEPTH_LIMIT);
            report.put("scope", "Item-ID recipe graph after server scripts; not an autocrafting or quest-completion test. "
                    + "Components, amounts, fluids, chemicals, energy, catalysts and machine conditions are not verified. "
                    + "Strict bases have no readable producer. Provided bases additionally use pack tags, natural block and entity loot, and closed cycles; unreadable outputs can hide producers. "
                    + "Supplied targets are listed separately; only a recipe proof counts as production, and headings can overlap. Empty generic ingredient lists are excluded. "
                    + "Dependencies are retained, not enforced. Greedy ranking stops at zero gain; combinations may unlock more.");
            report.put("questDataStatus", questDataStatus);
            report.put("missingQuestMetadata", missingQuestMetadata);
            report.put("defaultView", "provided");
            report.put("providedResources", resourceReport);
            var views = new LinkedHashMap<String, Object>();
            views.put("strict", view(strictLevels, bases));
            views.put("provided", view(levels, providedItems));
            report.put("views", views);
            report.put("blockerAnalysis", blockerReport);
            report.put("opaqueRecipeEvidence", opaqueRecipes);
            report.put("chapterGroups", groups);
            report.put("chapters", chapters);
            report.put("taskTypes", taskTypes);
            report.put("rewardTypes", rewardTypes);
            report.put("overall", counts(chapters.stream().flatMap(chapter -> chapter.quests().stream()).toList()));
            report.put("chapterCoverage", chapters.stream().map(chapter -> {
                var row = counts(chapter.quests());
                row.put("id", chapter.id());
                row.put("title", chapter.title());
                return row;
            }).toList());
            report.put("distinctTargets", targets);
            report.put("reachableTargets", levels);
            report.put("greedyRanking", ranking);
            report.put("zeroGainClassesAtStop", remainingClasses);
            report.put("recipeTypes", recipeTypes);
            report.put("unregisteredRecipeTypes", unregisteredTypes);
            report.put("outputUnreadable", unreadableOutputs);
            report.put("ingredientsUnreadable", unreadableIngredients);
            report.put("recipesByOutput", producers);
            report.put("baseItems", bases);
            report.put("targetsWithoutRecipe", targets.stream().filter(item -> !producers.containsKey(item)).toList());
            report.put("cycleItems", cycles);
            report.put("expansionDepthLimitedItems", depthLimited);
            return report;
        }

        @SuppressWarnings("unchecked")
        private void addChapterBlockers() {
            var global = (List<Map<String, Object>>) blockerReport.get("blockers");
            var chains = (Map<String, Object>) blockerReport.get("targetChains");
            var rows = new ArrayList<Map<String, Object>>();
            for (var chapter : chapters) {
                var items = new TreeSet<String>();
                chapter.quests().forEach(quest -> quest.tasks().stream()
                        .filter(task -> task.classification().equals("plain-item")).forEach(task -> items.add(task.item())));
                var blockers = new ArrayList<Map<String, Object>>();
                for (var blocker : global) {
                    var affected = new TreeSet<>((Set<String>) blocker.get("targets"));
                    affected.retainAll(items);
                    if (affected.isEmpty()) continue;
                    var row = new LinkedHashMap<String, Object>();
                    row.put("item", blocker.get("item"));
                    row.put("targetCount", affected.size());
                    row.put("targets", affected);
                    row.put("producerClasses", blocker.get("producerClasses"));
                    blockers.add(row);
                }
                blockers.sort(java.util.Comparator.<Map<String, Object>>comparingInt(row -> (int) row.get("targetCount"))
                        .reversed().thenComparing(row -> (String) row.get("item")));
                rows.add(Map.of("id", chapter.id(), "title", chapter.title(), "unreachableTargets",
                        items.stream().filter(chains::containsKey).count(), "topBlockers", blockers.stream().limit(5).toList()));
            }
            blockerReport.put("chapterBlockers", rows);
        }

        private Map<String, Object> counts(List<Quest> quests) {
            return counts(quests, levels);
        }

        private Map<String, Object> view(Map<String, Set<String>> coverage, Set<String> supplies) {
            var providedTargets = new TreeSet<>(supplies);
            providedTargets.retainAll(targets);
            var result = new LinkedHashMap<String, Object>();
            result.put("overall", counts(chapters.stream().flatMap(chapter -> chapter.quests().stream()).toList(), coverage));
            result.put("reachableTargets", coverage);
            result.put("providedTargets", providedTargets);
            result.put("chapterCoverage", chapters.stream().map(chapter -> {
                var row = counts(chapter.quests(), coverage);
                row.put("id", chapter.id());
                row.put("title", chapter.title());
                row.put("providedTasks", chapter.quests().stream().flatMap(quest -> quest.tasks().stream())
                        .filter(task -> task.classification().equals("plain-item") && supplies.contains(task.item())).count());
                return row;
            }).toList());
            return result;
        }

        private Map<String, Object> counts(List<Quest> quests, Map<String, Set<String>> coverage) {
            var tasks = quests.stream().flatMap(quest -> quest.tasks().stream()).toList();
            var items = tasks.stream().filter(task -> task.classification().equals("plain-item")).toList();
            var counts = new LinkedHashMap<String, Object>();
            counts.put("quests", quests.size());
            counts.put("itemTasks", tasks.stream().filter(task -> task.type().equals("item")).count());
            counts.put("plainItemTasks", items.size());
            counts.put("filterOrTagTasks", tasks.stream().filter(task -> task.classification().equals("filter-or-tag")).count());
            counts.put("unresolvedItemTasks", tasks.stream().filter(task -> task.classification().equals("unresolved-item")).count());
            counts.put("nonItemTasks", tasks.stream().filter(task -> task.classification().equals("non-item")).count());
            counts.put("componentItemTasks", items.stream().filter(Task::hasComponents).count());
            counts.put("distinctTargets", items.stream().map(Task::item).distinct().count());
            coverage.forEach((name, reachable) -> {
                counts.put(name + "Tasks", items.stream().filter(task -> reachable.contains(task.item())).count());
                counts.put(name + "Targets", items.stream().map(Task::item).distinct().filter(reachable::contains).count());
            });
            return counts;
        }

        String markdown(Map<String, Object> report) {
            var text = new StringBuilder("# Quest coverage\n\n").append(report.get("scope")).append("\n\n")
                    .append("Quest data: ").append(questDataStatus).append(". Missing optional metadata: ")
                    .append(missingQuestMetadata).append(".\n\n")
                    .append("Maximum recipe depth: ").append(DEPTH_LIMIT).append(". Titles prefer en_us, then inline, then zh_cn, then ID.\n\n")
                    .append("## Provided view (headline)\n\nTag targets may appear as both provided and producible; production requires a recipe proof. Do not add these overlapping counts.\n\n| Metric | Count |\n|---|---:|\n");
            counts(chapters.stream().flatMap(chapter -> chapter.quests().stream()).toList())
                    .forEach((key, value) -> text.append('|').append(key).append('|').append(value).append("|\n"));
            text.append("\n## Strict comparison\n\n| Level | Strict targets | Provided-view produced targets |\n|---|---:|---:|\n");
            levels.forEach((name, items) -> text.append('|').append(name).append('|').append(strictLevels.get(name).size())
                    .append('|').append(items.size()).append("|\n"));
            text.append("\nBase items in expanded chains: ").append(bases.size()).append(". Targets without a readable recipe: ")
                    .append(targets.stream().filter(item -> !producers.containsKey(item)).count()).append(".\n\n")
                    .append("## Chapters\n\nReachability columns count plain item task occurrences, including repeated targets.\n\n")
                    .append("| Chapter | Quests | Item tasks | Crafting | + AE2 | + Cooking |\n|---|---:|---:|---:|---:|---:|\n");
            for (var chapter : chapters) {
                var counts = counts(chapter.quests());
                text.append('|').append(chapter.title().replace("|", "\\|")).append('|').append(chapter.quests().size())
                        .append('|').append(counts.get("itemTasks"));
                levels.keySet().forEach(level -> text.append('|').append(counts.get(level + "Tasks")));
                text.append("|\n");
            }
            text.append("\n## Strict chapter comparison\n\n| Chapter | Crafting tasks | + AE2 | + Cooking |\n|---|---:|---:|---:|\n");
            for (var chapter : chapters) {
                var strict = counts(chapter.quests(), strictLevels);
                text.append('|').append(chapter.title().replace("|", "\\|"));
                strictLevels.keySet().forEach(level -> text.append('|').append(strict.get(level + "Tasks")));
                text.append("|\n");
            }
            text.append("\n## Provided resources\n\n").append(resourceReport.get("policy")).append("\n\n");
            appendResourceTables(text);
            QuestCoverageAnalysis.markdown(text, blockerReport);
            text.append("\n## Greedy additions after crafting + AE2 + cooking (provided view)\n\n")
                    .append("Counts are distinct target item IDs. Ties use recipe class ID order.\n\n")
                    .append("| Class | Newly reachable | Total | Examples (up to three) |\n|---|---:|---:|---|\n");
            for (var row : ranking) text.append('|').append(row.recipeClass()).append('|').append(row.unlocked())
                    .append('|').append(row.totalReachable()).append('|').append(String.join(", ", row.examples())).append("|\n");
            text.append("\nZero marginal gain at stop: ").append(String.join(", ", remainingClasses)).append(".\n");
            table(text, "Unregistered recipe types (self-reported ID or implementation class)", unregisteredTypes);
            table(text, "Output unreadable by recipe type", unreadableOutputs);
            table(text, "Ingredients unreadable or empty by recipe type", unreadableIngredients);
            table(text, "Task types", taskTypes);
            table(text, "Reward types (not targets)", rewardTypes);
            text.append("\n## Base items\n\n");
            bases.forEach(item -> text.append("- `").append(item).append("`\n"));
            text.append("\n## Targets without a readable recipe\n\n");
            targets.stream().filter(item -> !producers.containsKey(item)).forEach(item -> text.append("- `").append(item).append("`\n"));
            text.append("\n## Skipped item tasks\n\n");
            chapters.forEach(chapter -> chapter.quests().forEach(quest -> quest.tasks().stream()
                    .filter(task -> task.type().equals("item") && !task.classification().equals("plain-item"))
                    .forEach(task -> text.append("- ").append(quest.id()).append('/').append(task.id()).append(": ")
                            .append(task.classification()).append(" `").append(task.item()).append("`\n"))));
            text.append("\nCycle items encountered: ").append(cycles.size()).append(". Expansion depth limit encounters: ")
                    .append(depthLimited.size()).append(". Details, task SNBT, dependencies and recipe alternatives are in the JSON.\n");
            return text.toString();
        }

        @SuppressWarnings("unchecked")
        private void appendResourceTables(StringBuilder text) {
            text.append("| Rule | Items | Targets made base (overlap allowed) |\n|---|---:|---:|\n");
            var rules = (Map<String, Map<String, Object>>) resourceReport.get("rules");
            rules.forEach((rule, row) -> text.append('|').append(rule).append('|').append(row.get("itemCount"))
                    .append('|').append(row.get("targetCount")).append("|\n"));
            text.append("\nNatural blocks: ").append(resourceReport.get("naturalBlockCount"))
                    .append(". Mod contributions count distinct drops per block namespace; overlaps are possible.\n\n")
                    .append("| Mod | Natural blocks | Distinct drops |\n|---|---:|---:|\n");
            var mods = (List<Map<String, Object>>) resourceReport.get("naturalBlockMods");
            mods.stream().limit(10).forEach(row -> text.append('|').append(row.get("mod")).append('|')
                    .append(row.get("blockCount")).append('|').append(row.get("itemCount")).append("|\n"));
            text.append("\nOre/crystal supplements (only drops absent from natural blocks): ")
                    .append(resourceReport.get("supplementalOreCrystalBlocks")).append("\n");
            text.append("\nTag status: ").append(resourceReport.get("tagStatus")).append("\n\nLoot enumeration problems: ")
                    .append(resourceReport.get("lootProblems")).append("\n\n### Provided quest targets\n\n");
            targets.stream().filter(providedItems::contains).forEach(item -> text.append("- `").append(item).append("`\n"));
        }

        private static void table(StringBuilder text, String title, Map<String, Integer> counts) {
            text.append("\n## ").append(title).append("\n\n| Type | Count |\n|---|---:|\n");
            counts.forEach((type, count) -> text.append('|').append(type).append('|').append(count).append("|\n"));
        }
    }
}
