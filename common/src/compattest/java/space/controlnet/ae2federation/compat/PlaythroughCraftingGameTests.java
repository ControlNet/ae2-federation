package space.controlnet.ae2federation.compat;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import appeng.api.stacks.AEItemKey;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Serial quest crafting against plain AE2 and the guide's materials/factory/requester federation. */
@PrefixGameTestTemplate(false)
public final class PlaythroughCraftingGameTests {
    private PlaythroughCraftingGameTests() { }

    /** Records target outcomes without turning ordinary crafting failures into harness failures. */
    @RunsAlone
    @GameTest(templateNamespace = "ae2federation_test", template = "scale_36_empty", timeoutTicks = 1000000)
    public static void playthroughCrafting(GameTestHelper helper) throws Exception {
        new Run(helper).start();
    }

    private record Target(String chapter, String chapterId, String quest, String questId, String taskId,
                          String item, long count, AEItemKey key) { }

    private static final class Run {
        private final GameTestHelper helper;
        private final List<Target> targets = new ArrayList<>();
        private final List<Map<String, Object>> skipped = new ArrayList<>();
        private final List<Map<String, Object>> rows = new ArrayList<>();
        private final List<Map<String, String>> deferred = new ArrayList<>();
        private final String chapter = System.getProperty("ae2federation.playthrough.chapter", "ae2");
        private final String mode = System.getProperty("ae2federation.playthrough.mode", "crafting-frontier");
        private final Set<String> items = java.util.Arrays.stream(System.getProperty("ae2federation.playthrough.items", "").split(","))
                .map(String::trim).filter(item -> !item.isEmpty()).collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        private final int maximum = Integer.getInteger("ae2federation.playthrough.maxTargets", 16);
        private final int skip = Integer.getInteger("ae2federation.playthrough.skipTargets", 0);
        private final int budget = Integer.getInteger("ae2federation.playthrough.targetTicks", 1200);
        private final long started = System.nanoTime();
        private final Path directory = Path.of(System.getProperty("ae2federation.compat.report", "compat-report.json"))
                .toAbsolutePath().getParent();
        private PlaythroughRecipes recipes;
        private PlaythroughFactory factory;
        private PlaythroughRecipes.Proof proof;
        private PlaythroughFactory.Result baseline;
        private int index;
        private String harnessError = "";
        private boolean completed;

        Run(GameTestHelper helper) { this.helper = helper; }

        void start() throws Exception {
            if (!Set.of("crafting-only", "crafting-frontier").contains(mode) || maximum < 0 || skip < 0 || budget < 1) {
                throw new IllegalArgumentException("Invalid playthrough mode/limit/tick budget");
            }
            var probe = new QuestCoverageCompatGameTests.Probe();
            probe.readQuests(Path.of("config/ftbquests/quests"));
            if (!probe.questDataStatus().equals("available")) throw new IllegalStateException(probe.questDataStatus());
            probe.readRecipes(helper); probe.readResources(helper);
            recipes = new PlaythroughRecipes(helper, probe, mode.equals("crafting-frontier"));
            var chapters = new HashMap<String, QuestCoverageCompatGameTests.Chapter>();
            var quests = new LinkedHashMap<String, QuestCoverageCompatGameTests.Quest>();
            for (var ch : probe.chapters()) for (var quest : ch.quests()) { quests.put(quest.id(), quest); chapters.put(quest.id(), ch); }
            var ordered = new ArrayList<QuestCoverageCompatGameTests.Quest>();
            var seen = new HashSet<String>();
            for (var quest : quests.values()) visit(quest, quests, seen, new HashSet<>(), ordered);
            int eligible = 0;
            for (var quest : ordered) {
                var ch = chapters.get(quest.id());
                boolean selected = chapter.isEmpty() || ch.id().equalsIgnoreCase(chapter) || ch.title().contains(chapter)
                        || chapter.equals("ae2") && ch.title().contains("应用能源2");
                if (!selected) continue;
                for (var task : quest.tasks()) {
                    if (!task.classification().equals("plain-item") || !items.isEmpty() && !items.contains(task.item())) continue;
                    var tag = TagParser.parseTag(task.snbt());
                    long count = tag.contains("count") ? tag.getLong("count") : 1;
                    if (count <= 0) throw new IllegalStateException("Invalid quest count: " + task.id());
                    var stack = task.hasComponents() ? ItemStack.parseOptional(helper.getLevel().registryAccess(), tag.getCompound("item"))
                            : PlaythroughRecipes.key(task.item()).toStack();
                    if (stack.isEmpty()) throw new IllegalStateException("Cannot decode quest stack: " + task.id());
                    var target = new Target(ch.title(), ch.id(), quest.title(), quest.id(), task.id(), task.item(), count, AEItemKey.of(stack));
                    if (!recipes.hasCraftingRecipe(task.item())) {
                        var row = metadata(target); row.put("reason", "own recipe is not crafting (or has no readable producer); Phase 3");
                        skipped.add(row);
                    } else if (eligible++ < skip) {
                        deferred.add(Map.of("task", task.id(), "item", task.item(), "reason", "skipTargets filter"));
                    } else if (maximum == 0 || targets.size() < maximum) targets.add(target);
                    else deferred.add(Map.of("task", task.id(), "item", task.item(), "reason", "maxTargets filter"));
                }
            }
            if (targets.isEmpty()) throw new IllegalStateException("Chapter filter selected no crafting targets: " + chapter);
            write();
            helper.runAfterDelay(1, this::tick);
        }

        private static void visit(QuestCoverageCompatGameTests.Quest quest,
                                  Map<String, QuestCoverageCompatGameTests.Quest> quests, Set<String> seen,
                                  Set<String> active, List<QuestCoverageCompatGameTests.Quest> ordered) {
            if (seen.contains(quest.id())) return;
            if (!active.add(quest.id())) throw new IllegalStateException("Quest dependency cycle: " + quest.id());
            for (var dependency : quest.dependencies()) if (quests.containsKey(dependency)) visit(quests.get(dependency), quests, seen, active, ordered);
            active.remove(quest.id()); seen.add(quest.id()); ordered.add(quest);
        }

        private void tick() {
            try {
                if (index == targets.size() || factory == null && baseline == null && (System.nanoTime() - started) / 1_000_000_000L > 1400) {
                    for (int i = index; i < targets.size(); i++) deferred.add(Map.of("task", targets.get(i).taskId(),
                            "item", targets.get(i).item(), "reason", "1400 second harness runtime limit"));
                    completed = true; write(); helper.succeed(); return;
                }
                var target = targets.get(index);
                if (factory == null) {
                    if (proof == null) {
                        try {
                            proof = recipes.target(target.item());
                        } catch (PlaythroughRecipes.NotBootstrappable exception) {
                            var row = metadata(target);
                            row.put("classification", "not-bootstrappable"); row.put("reason", exception.getMessage());
                            row.put("standIns", Map.of()); row.put("recipes", List.of()); rows.add(row);
                            index++; write(); helper.runAfterDelay(1, this::tick); return;
                        } catch (IllegalArgumentException exception) {
                            var failure = new PlaythroughFactory.Result(false, 0, "pattern-encoding", exception.getMessage(), Map.of(), Map.of(), 0,
                                    Map.of("execution", "NOT_CHECKED: pattern encoding failed"), Map.of(), 0, 0, 0, 0, 0, 0);
                            add(target, failure, failure, Map.of());
                            index++; proof = null; write(); helper.runAfterDelay(1, this::tick); return;
                        }
                    }
                    com.mojang.logging.LogUtils.getLogger().info("AE2F_PLAYTHROUGH_ORDER target={}/{} item={} count={} layout={}",
                            index + 1, targets.size(), target.item(), target.count(), baseline == null ? "baseline" : "federated");
                    factory = new PlaythroughFactory(helper, baseline != null, proof, target.key(), target.count(), budget);
                }
                var result = factory.tick();
                if (result != null) {
                    factory.close(); factory = null;
                    if (baseline == null) baseline = result;
                    else {
                        add(target, baseline, result, proof.standIns()); index++; baseline = null; proof = null; write();
                    }
                    helper.runAfterDelay(10, this::tick); return;
                }
                helper.runAfterDelay(1, this::tick);
            } catch (Exception exception) {
                harnessError = exception.toString();
                if (factory != null) { factory.close(); factory = null; }
                try { write(); } catch (Exception writing) { exception.addSuppressed(writing); }
                throw new IllegalStateException("Playthrough harness failed at target " + index, exception);
            }
        }

        private void add(Target target, PlaythroughFactory.Result base, PlaythroughFactory.Result fed, Map<String, List<String>> standIns) {
            var row = metadata(target);
            row.put("baseline", base); row.put("federated", fed); row.put("standIns", standIns);
            row.put("classification", base.pass() ? fed.pass() ? "both-pass" : "baseline-only" : fed.pass() ? "federated-only" : "both-fail");
            row.put("recipes", proof == null ? List.of() : proof.patterns().keySet());
            rows.add(row);
            com.mojang.logging.LogUtils.getLogger().info("AE2F_PLAYTHROUGH_RESULT item={} class={} baseline={} federated={}",
                    target.item(), row.get("classification"), base.invariant(), fed.invariant());
        }

        private static Map<String, Object> metadata(Target target) {
            var row = new LinkedHashMap<String, Object>();
            row.put("chapter", target.chapter()); row.put("chapterId", target.chapterId());
            row.put("quest", target.quest()); row.put("questId", target.questId()); row.put("taskId", target.taskId());
            row.put("item", target.item()); row.put("count", target.count());
            return row;
        }

        private static Map<String, Integer> counts() {
            var counts = new LinkedHashMap<String, Integer>();
            for (var name : List.of("both-pass", "baseline-only", "both-fail", "federated-only", "not-bootstrappable")) counts.put(name, 0);
            return counts;
        }

        private void write() throws Exception {
            var overall = counts();
            var chapters = new TreeMap<String, Map<String, Integer>>();
            for (var row : rows) {
                var name = row.get("classification").toString(); overall.merge(name, 1, Integer::sum);
                chapters.computeIfAbsent(row.get("chapter").toString(), ignored -> counts()).merge(name, 1, Integer::sum);
            }
            var report = new LinkedHashMap<String, Object>();
            report.put("itemFilter", items);
            report.put("materialSource", "Complete Phase 0 provided set plus selected stand-ins; AE2 excludes the requested output during planning.");
            report.put("schemaVersion", 2); report.put("mode", mode); report.put("chapterFilter", chapter);
            report.put("skipTargets", skip);
            report.put("ticksPerCraft", Integer.getInteger("ae2federation.playthrough.ticksPerCraft", 20));
            report.put("assemblerCopies", Integer.getInteger("ae2federation.playthrough.assemblerCopies", 28));
            report.put("maxTargets", maximum); report.put("targetTickBudget", budget); report.put("serial", true);
            report.put("machineBackedRecipeClasses", PlaythroughRecipes.MACHINE_BACKED_CLASSES);
            report.put("selectedTargetCount", targets.size());
            report.put("completedTargetCount", rows.size());
            report.put("completed", completed); report.put("harnessError", harnessError);
            report.put("exceptionMonitoring", "Harness and crafting-future exceptions fail the test; unrelated server log exceptions are not intercepted.");
            report.put("overall", overall); report.put("chapters", chapters); report.put("targets", rows);
            report.put("skippedNonCrafting", skipped); report.put("deferred", deferred);
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("playthrough-crafting.json"), new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report));
            var text = new StringBuilder("# Quest crafting playthrough\n\nMode: ").append(mode).append(". Serial, chapter filter: ")
                    .append(chapter).append(". Completed: ").append(completed).append(".\n\n")
                    .append("| Chapter | Both pass | Baseline only | Both fail | Federated only | Not bootstrappable |\n|---|---:|---:|---:|---:|---:|\n");
            var summary = new LinkedHashMap<>(chapters); summary.put("Overall", overall);
            summary.forEach((name, values) -> { text.append('|').append(escape(name)); values.values().forEach(v -> text.append('|').append(v)); text.append("|\n"); });
            text.append("\nBaseline only indicates a likely Federation bug; JSON retains used items and extraction counts.\n\n")
                    .append("| Chapter / quest | Item | Count | Class | Baseline ticks / invariant / reason | Federated ticks / invariant / reason | Stand-ins |\n|---|---|---:|---|---|---|---|\n");
            for (var row : rows) {
                text.append('|').append(escape(row.get("chapter") + " / " + row.get("quest"))).append('|').append(row.get("item"))
                        .append('|').append(row.get("count")).append('|').append(row.get("classification"));
                for (var layout : List.of("baseline", "federated")) {
                    var result = (PlaythroughFactory.Result) row.get(layout);
                    if (result == null) text.append('|').append(escape(row.get("reason").toString()));
                    else text.append('|').append(result.ticks()).append(" ticks; budget=").append(result.tickBudget())
                            .append("; crafts=").append(result.totalCrafts()).append("; bytes=").append(result.planBytes())
                            .append("; expected=").append(result.expectedAmount()).append(" / ")
                            .append(result.pass() ? "PASS" : escape(result.invariant() + ": " + result.reason()));
                }
                text.append('|').append(escape(row.get("standIns").toString())).append("|\n");
            }
            text.append("\n## Skipped own non-crafting recipes\n\n");
            for (var row : skipped) text.append("- ").append(row.get("chapter")).append(" / ").append(row.get("questId")).append(": `").append(row.get("item")).append("`\n");
            text.append("\nDeferred by filters/runtime: ").append(deferred.size()).append(". See JSON for task IDs and reasons.\n\nHarness error: ").append(harnessError).append('\n');
            Files.writeString(directory.resolve("playthrough-crafting.md"), text.toString());
        }

        private static String escape(String text) { return text.replace("|", "\\|").replace("\n", " "); }
    }
}
