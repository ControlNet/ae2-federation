package space.controlnet.ae2federation.compat;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Bounded recipe proofs and evidence-based priorities for missing processing support. */
final class QuestCoverageAnalysis {
    record OpaqueRecipe(String id, String type, String unreadable, Set<String> outputIds,
                        String evidence, Map<String, Set<String>> outputFields) { }
    private record Plan(String item, QuestCoverageCompatGameTests.Recipe recipe, List<Plan> inputs,
                        BigInteger cost, BigInteger steps, boolean opaque) { }
    private final Map<String, List<QuestCoverageCompatGameTests.Recipe>> producers;
    private final Set<String> targets;
    private final Set<String> supplied;
    private final Set<String> independentlyProvided;
    private final List<OpaqueRecipe> opaque;
    private final int limit;

    QuestCoverageAnalysis(Map<String, List<QuestCoverageCompatGameTests.Recipe>> producers, Set<String> targets,
                          Set<String> supplied, Set<String> independentlyProvided, List<OpaqueRecipe> opaque, int limit) {
        this.producers = producers;
        this.targets = targets;
        this.supplied = supplied;
        this.independentlyProvided = independentlyProvided;
        this.opaque = opaque;
        this.limit = limit;
    }

    static Set<String> produced(Map<String, List<QuestCoverageCompatGameTests.Recipe>> producers,
                                Set<String> classes, Set<String> supplied, int limit) {
        return new HashSet<>(productionProofs(producers, classes, supplied, limit).keySet());
    }

    /** First finite producer per output, preserving the same breadth-first resource policy as coverage. */
    static Map<String, QuestCoverageCompatGameTests.Recipe> productionProofs(
            Map<String, List<QuestCoverageCompatGameTests.Recipe>> producers,
            Set<String> classes, Set<String> supplied, int limit) {
        var produced = new LinkedHashMap<String, QuestCoverageCompatGameTests.Recipe>();
        var available = new HashSet<>(supplied);
        for (int depth = 0; depth < limit; depth++) {
            var next = new LinkedHashMap<String, QuestCoverageCompatGameTests.Recipe>();
            for (var entry : producers.entrySet()) {
                if (produced.containsKey(entry.getKey())) continue;
                for (var recipe : entry.getValue()) {
                    if (!recipe.ingredientsReadable() || !classes.contains(recipe.recipeClass())) continue;
                    boolean complete = true;
                    for (var slot : recipe.ingredients()) {
                        boolean satisfied = false;
                        for (var item : slot) if (!producers.containsKey(item) || available.contains(item)) {
                            satisfied = true;
                            break;
                        }
                        if (!satisfied) {
                            complete = false;
                            break;
                        }
                    }
                    if (complete) {
                        next.put(entry.getKey(), recipe);
                        break;
                    }
                }
            }
            if (next.isEmpty()) break;
            produced.putAll(next);
            available.addAll(next.keySet());
        }
        return produced;
    }

    Map<String, Object> report(Set<String> classes) {
        var available = produced(producers, classes, supplied, limit);
        available.addAll(supplied);
        var universe = new TreeSet<>(targets);
        producers.forEach((item, recipes) -> {
            universe.add(item);
            recipes.forEach(recipe -> recipe.ingredients().forEach(universe::addAll));
        });
        universe.stream().filter(item -> !producers.containsKey(item)).forEach(available::add);
        var plans = cheapestPlans(universe, available);
        var chains = new TreeMap<String, Object>();
        var blockedBy = new TreeMap<String, Set<String>>();
        var neededClasses = new TreeMap<String, Set<String>>();
        for (var target : targets) {
            if (available.contains(target)) continue;
            var root = plans.get(target);
            var blockers = new TreeSet<String>();
            var recipeClasses = new TreeSet<String>();
            var recipeIds = new TreeSet<String>();
            var opaqueItems = new TreeSet<String>();
            if (root == null) {
                blockers.add(target);
                opaqueItems.add(target);
            } else {
                var stack = new ArrayDeque<Plan>();
                var seen = new HashSet<String>();
                stack.push(root);
                while (!stack.isEmpty()) {
                    var plan = stack.pop();
                    if (!seen.add(plan.item()) || available.contains(plan.item())) continue;
                    blockers.add(plan.item());
                    if (plan.opaque()) opaqueItems.add(plan.item());
                    if (plan.recipe() != null) {
                        recipeClasses.add(plan.recipe().recipeClass());
                        recipeIds.add(plan.recipe().id());
                    }
                    stack.addAll(plan.inputs());
                }
            }
            blockers.forEach(item -> blockedBy.computeIfAbsent(item, ignored -> new TreeSet<>()).add(target));
            recipeClasses.forEach(type -> neededClasses.computeIfAbsent(type, ignored -> new TreeSet<>()).add(target));
            var row = new LinkedHashMap<String, Object>();
            row.put("unreachableItems", blockers);
            row.put("recipeClasses", recipeClasses);
            row.put("recipeIds", recipeIds);
            row.put("opaqueOrCyclicItems", opaqueItems);
            row.put("cost", root == null ? -1 : root.cost());
            row.put("status", root == null ? "no finite chain within depth limit" : "selected cheapest chain");
            chains.put(target, row);
        }
        var blockerRows = new ArrayList<Map<String, Object>>();
        blockedBy.forEach((item, blockedTargets) -> {
            var row = rankRow("item", item, blockedTargets);
            var types = new TreeSet<String>();
            producers.getOrDefault(item, List.of()).forEach(recipe -> types.add(recipe.recipeClass()));
            row.put("producerClasses", types);
            blockerRows.add(row);
        });
        sortRows(blockerRows, "item", "targetCount");
        var classRows = new ArrayList<Map<String, Object>>();
        neededClasses.forEach((type, blockedTargets) -> {
            var row = rankRow("recipeClass", type, blockedTargets);
            row.put("alreadyAllowed", classes.contains(type));
            classRows.add(row);
        });
        sortRows(classRows, "recipeClass", "targetCount");
        var report = new LinkedHashMap<String, Object>();
        report.put("method", "For targets neither produced nor supplied at crafting + AE2 + cooking, choose a chain minimizing "
                + "unreachable item occurrences, then recipe steps, then recipe ID; ingredient alternatives tie by item ID. "
                + "Already available items cost zero. An unreadable ingredient list is an optimistic opaque leaf costing one, "
                + "not a production proof. Cycles without a finite chain are reported separately. Class demand counts each target "
                + "once on its selected chain, not universal necessity across all possible recipes; combinations can have zero greedy gain.");
        report.put("unreachableTargetCount", chains.size());
        report.put("targetChains", chains);
        report.put("blockers", blockerRows);
        report.put("recipeClassDemand", classRows);
        report.put("unreadablePriorityMethod", "Optimistic counterfactual: all readable classes enabled; credit known or explicitly serialized opaque outputs "
                + "as producible, without assuming their inputs are readable. All comparisons use a producer universe including opaque hints, excluding their no-producer supply assumption but retaining other supply rules. Rank by targets lost when one opaque type is withheld while all other types "
                + "remain repaired. Standalone gain compares only that type repaired with the readable baseline. Unknown outputs remain unattributed; "
                + "this is candidate adapter impact, not an executable proof or a claim of true exclusivity.");
        report.put("unreadablePriority", unreadablePriority(blockedBy));
        var checks = new TreeMap<String, Object>();
        for (var item : List.of("minecraft:iron_ingot", "ae2:silicon", "ae2:fluix_crystal")) {
            checks.put(item, Map.of("provided", supplied.contains(item), "available", available.contains(item)));
        }
        report.put("resourceSanityChecks", checks);
        return report;
    }

    private Map<String, Plan> cheapestPlans(Set<String> universe, Set<String> available) {
        Map<String, Plan> plans = new HashMap<>();
        for (var item : universe) if (available.contains(item)) plans.put(item, new Plan(item, null, List.of(), BigInteger.ZERO, BigInteger.ZERO, false));
        for (int depth = 0; depth < limit; depth++) {
            var next = new HashMap<>(plans);
            boolean changed = false;
            for (var entry : producers.entrySet()) {
                if (available.contains(entry.getKey())) continue;
                var best = plans.get(entry.getKey());
                for (var recipe : entry.getValue()) {
                    var inputs = new ArrayList<Plan>();
                    var cost = BigInteger.ONE;
                    var steps = BigInteger.ONE;
                    boolean complete = true;
                    if (recipe.ingredientsReadable()) {
                        for (var slot : recipe.ingredients()) {
                            Plan input = null;
                            for (var item : slot) {
                                var candidate = plans.get(item);
                                if (candidate != null && (input == null || compare(candidate, input) < 0)) input = candidate;
                            }
                            if (input == null) {
                                complete = false;
                                break;
                            }
                            inputs.add(input);
                            cost = cost.add(input.cost());
                            steps = steps.add(input.steps());
                        }
                    }
                    if (!complete) continue;
                    var candidate = new Plan(entry.getKey(), recipe, inputs, cost, steps, !recipe.ingredientsReadable());
                    if (best == null || compare(candidate, best) < 0) best = candidate;
                }
                if (best != null && best != plans.get(entry.getKey())) {
                    next.put(entry.getKey(), best);
                    changed = true;
                }
            }
            plans = next;
            if (!changed) break;
        }
        return plans;
    }

    private static int compare(Plan a, Plan b) {
        int comparison = a.cost().compareTo(b.cost());
        if (comparison == 0) comparison = a.steps().compareTo(b.steps());
        if (comparison == 0) comparison = a.item().compareTo(b.item());
        if (comparison == 0) comparison = (a.recipe() == null ? "" : a.recipe().id()).compareTo(b.recipe() == null ? "" : b.recipe().id());
        return comparison;
    }

    private List<Map<String, Object>> unreadablePriority(Map<String, Set<String>> blockedBy) {
        var candidates = new TreeMap<String, Set<String>>();
        var counts = new TreeMap<String, Integer>();
        var unattributed = new TreeMap<String, Integer>();
        for (var recipe : opaque) {
            counts.merge(recipe.type(), 1, Integer::sum);
            if (recipe.outputIds().isEmpty()) unattributed.merge(recipe.type(), 1, Integer::sum);
            for (var item : recipe.outputIds()) candidates.computeIfAbsent(item, ignored -> new TreeSet<>()).add(recipe.type());
        }
        var allClasses = new TreeSet<String>();
        producers.values().forEach(recipes -> recipes.forEach(recipe -> allClasses.add(recipe.recipeClass())));
        // Hinted outputs are known producers here, even when no executable recipe can yet be inspected.
        var universe = new TreeMap<>(producers);
        candidates.keySet().forEach(item -> universe.putIfAbsent(item, List.of()));
        var baseline = hypothetical(universe, allClasses, Set.of());
        var allHints = new TreeSet<>(candidates.keySet());
        var allRepaired = hypothetical(universe, allClasses, allHints);
        var rows = new ArrayList<Map<String, Object>>();
        for (var type : counts.keySet()) {
            var soleTargets = new TreeSet<String>();
            var sharedTargets = new TreeSet<String>();
            var downstream = new TreeSet<String>();
            for (var entry : candidates.entrySet()) {
                if (!entry.getValue().contains(type)) continue;
                boolean readableProducer = producers.getOrDefault(entry.getKey(), List.of()).stream()
                        .anyMatch(QuestCoverageCompatGameTests.Recipe::ingredientsReadable);
                if (!readableProducer) {
                    if (targets.contains(entry.getKey())) {
                        sharedTargets.add(entry.getKey());
                        if (entry.getValue().size() == 1) soleTargets.add(entry.getKey());
                    }
                    downstream.addAll(blockedBy.getOrDefault(entry.getKey(), Set.of()));
                }
            }
            var onlyTypeHints = new TreeSet<String>();
            var otherTypeHints = new TreeSet<String>();
            candidates.forEach((item, types) -> {
                if (types.contains(type)) onlyTypeHints.add(item);
                if (types.stream().anyMatch(other -> !other.equals(type))) otherTypeHints.add(item);
            });
            var exclusive = new TreeSet<>(allRepaired);
            exclusive.removeAll(hypothetical(universe, allClasses, otherTypeHints));
            exclusive.retainAll(targets);
            var standalone = hypothetical(universe, allClasses, onlyTypeHints);
            standalone.removeAll(baseline);
            standalone.retainAll(targets);
            var row = rankRow("type", type, exclusive);
            row.put("soleObservedProducerTargets", soleTargets);
            row.put("soleObservedProducerTargetCount", soleTargets.size());
            row.put("optimisticStandaloneUnlocks", standalone);
            row.put("optimisticStandaloneUnlockCount", standalone.size());
            row.put("candidateOnlyWayTargets", sharedTargets);
            row.put("candidateOnlyWayTargetCount", sharedTargets.size());
            row.put("downstreamBlockedTargets", downstream);
            row.put("downstreamBlockedTargetCount", downstream.size());
            row.put("unreadableRecipes", counts.get(type));
            row.put("unattributedOutputRecipes", unattributed.getOrDefault(type, 0));
            rows.add(row);
        }
        sortRows(rows, "type", "targetCount");
        return rows;
    }

    private Set<String> hypothetical(Map<String, List<QuestCoverageCompatGameTests.Recipe>> universe,
                                     Set<String> classes, Set<String> outputs) {
        var inputs = new HashSet<>(independentlyProvided);
        inputs.addAll(outputs);
        var result = produced(universe, classes, inputs, limit);
        result.addAll(outputs);
        return result;
    }

    private static Map<String, Object> rankRow(String key, String name, Set<String> targets) {
        var row = new LinkedHashMap<String, Object>();
        row.put(key, name);
        row.put("targetCount", targets.size());
        row.put("targets", targets);
        row.put("examples", targets.stream().limit(3).toList());
        return row;
    }

    private static void sortRows(List<Map<String, Object>> rows, String key, String count) {
        rows.sort(Comparator.<Map<String, Object>>comparingInt(row -> (int) row.get(count)).reversed()
                .thenComparing(row -> (String) row.get(key)));
    }

    @SuppressWarnings("unchecked")
    static void markdown(StringBuilder text, Map<String, Object> report) {
        text.append("\n## Blockers in the provided view\n\n").append(report.get("method"))
                .append("\n\nUnreachable and not provided targets: ").append(report.get("unreachableTargetCount"))
                .append(". Full chains and complete rankings are in JSON.\n\n")
                .append("| Blocker (top 30) | Targets | Producer classes |\n|---|---:|---|\n");
        var blockers = (List<Map<String, Object>>) report.get("blockers");
        blockers.stream().limit(30).forEach(row -> text.append('|').append(row.get("item")).append('|')
                .append(row.get("targetCount")).append('|').append(row.get("producerClasses")).append("|\n"));
        text.append("\n### Top five blockers per chapter\n\nCounts are distinct targets in that chapter on selected cheapest chains, at crafting + AE2 + cooking.\n\n")
                .append("| Chapter | Blocker | Targets | Producer classes |\n|---|---|---:|---|\n");
        for (var chapter : (List<Map<String, Object>>) report.get("chapterBlockers")) {
            var title = chapter.get("title").toString().replace("|", "\\|");
            var rows = (List<Map<String, Object>>) chapter.get("topBlockers");
            if (rows.isEmpty()) text.append('|').append(title).append("|No blocked targets|0|—|\n");
            for (var row : rows) text.append('|').append(title).append('|').append(row.get("item"))
                    .append('|').append(row.get("targetCount")).append('|').append(row.get("producerClasses")).append("|\n");
        }
        text.append("\n### Recipe class demand on selected chains\n\n| Class | Targets needing class at least once | Already allowed |\n|---|---:|---|\n");
        ((List<Map<String, Object>>) report.get("recipeClassDemand")).forEach(row -> text.append('|').append(row.get("recipeClass"))
                .append('|').append(row.get("targetCount")).append('|').append(row.get("alreadyAllowed")).append("|\n"));
        text.append("\n## Unreadable recipe adapter priority\n\n")
                .append("Exclusive counts compare all known/hinted opaque outputs hypothetically producible against the same set with one type omitted, ")
                .append("with all readable recipe classes enabled. Standalone gain enables only that type's outputs. These are optimistic adapter-impact estimates: ")
                .append("unknown inputs are assumed satisfiable, and unknown outputs may hide alternatives. This handles compression-cycle alternatives without claiming ")
                .append("that adapters can execute these recipes. Output hints use serialized output fields, never recipe names or mod ownership. ")
                .append("Unattributed recipes are unrankable, not evidence of zero impact. JSON also retains direct sole-producer and downstream blocker counts.\n\n")
                .append("| Type | Exclusive optimistic targets | Standalone optimistic gain | Direct sole-producer targets | Unattributed outputs |\n|---|---:|---:|---:|---:|\n");
        ((List<Map<String, Object>>) report.get("unreadablePriority")).forEach(row -> text.append('|').append(row.get("type"))
                .append('|').append(row.get("targetCount")).append('|').append(row.get("optimisticStandaloneUnlockCount"))
                .append('|').append(row.get("soleObservedProducerTargetCount")).append('|').append(row.get("unattributedOutputRecipes")).append("|\n"));
    }
}
