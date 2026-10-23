package space.controlnet.ae2federation.compat;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;

/** Executable crafting proofs over Phase 0's quest, producer and provided-resource view. */
final class PlaythroughRecipes {
    // Phase 3 replaces these frontier leaves with actual machine adapters, one recipe class at a time.
    static final Set<String> MACHINE_BACKED_CLASSES = Set.of();
    static final class NotBootstrappable extends IllegalArgumentException {
        NotBootstrappable(String message) { super(message); }
    }
    record Proof(Map<String, ItemStack> patterns, Set<AEItemKey> supplies, Map<String, List<String>> standIns) { }
    private final GameTestHelper helper;
    private final QuestCoverageCompatGameTests.Probe probe;
    private final boolean frontier;
    private final Map<String, Proof> cache = new java.util.HashMap<>();
    private int attempts;
    private String target;
    private Map<String, QuestCoverageCompatGameTests.Recipe> reachable;

    PlaythroughRecipes(GameTestHelper helper, QuestCoverageCompatGameTests.Probe probe, boolean frontier) {
        this.helper = helper;
        this.probe = probe;
        this.frontier = frontier;
    }

    Proof target(String item) {
        attempts = 0;
        target = item;
        cache.clear();
        var supplied = new HashSet<>(probe.providedItems());
        if (frontier) probe.producers().forEach((output, recipes) -> {
            if (machineLeaf(output)) {
                supplied.add(output);
            }
        });
        // AE2 ignores existing stock of the requested output while planning an autocraft.
        supplied.remove(item);
        reachable = QuestCoverageAnalysis.productionProofs(probe.producers(), Set.of("crafting"), supplied, 64);
        if (!reachable.containsKey(item)) {
            var seeded = new HashSet<>(supplied);
            seeded.add(item);
            var withSeed = QuestCoverageAnalysis.productionProofs(probe.producers(), Set.of("crafting"), seeded, 64);
            if (withSeed.containsKey(item)) throw new NotBootstrappable(item
                    + ": no finite crafting proof without a target seed; adding only that seed enables producer "
                    + withSeed.get(item).id());
        }
        var proof = resolve(item, true, new HashSet<>());
        var supplies = new HashSet<>(proof.supplies());
        probe.providedItems().stream().map(PlaythroughRecipes::key).forEach(supplies::add);
        return new Proof(proof.patterns(), supplies, proof.standIns());
    }

    boolean hasCraftingRecipe(String item) {
        return probe.producers().getOrDefault(item, List.of()).stream().anyMatch(r -> r.recipeClass().equals("crafting"));
    }

    private Proof resolve(String item, boolean root, Set<String> visiting) {
        if (++attempts > 20000) throw new IllegalArgumentException("recipe selection exceeded 20000 candidates");
        var recipes = probe.producers().getOrDefault(item, List.of());
        if (!root && item.equals(target)) throw new IllegalArgumentException("target cannot be supplied as its own ingredient");
        if (!root && (probe.providedItems().contains(item) || !probe.producers().containsKey(item))) {
            return new Proof(Map.of(), Set.of(key(item)), Map.of());
        }
        if (!root && cache.containsKey(item)) return cache.get(item);
        if (!reachable.containsKey(item) || visiting.contains(item)) {
            if (!root && machineLeaf(item)) return standIn(item);
            throw new IllegalArgumentException(item + ": no finite crafting chain in the "
                    + (frontier ? "crafting-frontier" : "crafting-only") + " provided view");
        }
        if (visiting.size() >= 64) throw new IllegalArgumentException("crafting depth exceeded at " + item);
        visiting.add(item);
        var reasons = new ArrayList<String>();
        try {
            for (var recipe : recipes.stream().filter(r -> r.recipeClass().equals("crafting"))
                    .sorted(java.util.Comparator.<QuestCoverageCompatGameTests.Recipe>comparingInt(r ->
                            r.id().equals(reachable.get(item).id()) ? 0 : 1)
                            .thenComparing(QuestCoverageCompatGameTests.Recipe::id)).toList()) {
                try {
                    var result = encode(recipe, visiting);
                    if (result.supplies().contains(key(item))) {
                        reasons.add(recipe.id() + ": conversion needs a supplied seed of " + item);
                        continue;
                    }
                    if (!root && result.standIns().keySet().stream().noneMatch(this::hasCraftingRecipe)) cache.put(item, result);
                    return result;
                } catch (IllegalArgumentException exception) {
                    reasons.add(recipe.id() + ": " + exception.getMessage());
                }
            }
            if (!root && machineLeaf(item)) return standIn(item);
            throw new IllegalArgumentException(item + ": " + (reasons.isEmpty() ? "no supported crafting producer" : reasons));
        } finally {
            visiting.remove(item);
        }
    }

    @SuppressWarnings("unchecked")
    private Proof encode(QuestCoverageCompatGameTests.Recipe recipe, Set<String> visiting) {
        var raw = helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse(recipe.id())).orElseThrow();
        if (!(raw.value() instanceof CraftingRecipe)) throw new IllegalStateException("Phase 0 recipe class changed");
        var holder = (RecipeHolder<CraftingRecipe>) (RecipeHolder<?>) raw;
        var actual = holder.value();
        var ingredients = actual.getIngredients();
        int width = actual instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
        int height = actual instanceof ShapedRecipe shaped ? shaped.getHeight() : 3;
        if (width > 3 || height > 3 || ingredients.size() > 9 || !recipe.ingredientsReadable()) {
            throw new IllegalArgumentException("unreadable or larger than a 3x3 crafting grid");
        }
        var choices = new ArrayList<List<ItemStack>>();
        var proofs = new java.util.HashMap<AEItemKey, Proof>();
        for (var ingredient : ingredients) {
            if (ingredient.isEmpty()) { choices.add(List.of(ItemStack.EMPTY)); continue; }
            var alternatives = java.util.Arrays.stream(ingredient.getItems()).filter(stack -> !stack.isEmpty())
                    .sorted(java.util.Comparator.comparingInt(this::ingredientRank)
                            .thenComparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))
                    .map(stack -> stack.copyWithCount(1)).toList();
            if (alternatives.isEmpty()) throw new IllegalArgumentException("ingredient has no concrete alternatives");
            choices.add(alternatives);
        }
        var grid = new ArrayList<ItemStack>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        var failures = new ArrayList<String>();
        if (!choose(actual, choices, grid, 0, width, new int[] {4096}, visiting, proofs, failures)) {
            throw new IllegalArgumentException("no reachable recipe.matches-approved grid within 4096 combinations: " + failures);
        }
        var input = CraftingInput.of(3, 3, grid);
        var output = actual.assemble(input, helper.getLevel().registryAccess());
        if (output.isEmpty() || !BuiltInRegistries.ITEM.getKey(output.getItem()).toString().equals(recipe.output())) {
            throw new IllegalArgumentException("assembled output differs from Phase 0 output");
        }
        var pattern = PatternDetailsHelper.encodeCraftingPattern(holder, grid.toArray(ItemStack[]::new), output, false, false);
        if (PatternDetailsHelper.decodePattern(pattern, helper.getLevel()) == null) {
            throw new IllegalArgumentException("AE2 rejected encoded crafting pattern");
        }
        var patterns = new LinkedHashMap<String, ItemStack>();
        var supplies = new HashSet<AEItemKey>();
        var standIns = new TreeMap<String, List<String>>();
        for (var stack : grid) if (!stack.isEmpty()) {
            var proof = proofs.get(AEItemKey.of(stack));
            patterns.putAll(proof.patterns()); supplies.addAll(proof.supplies()); standIns.putAll(proof.standIns());
        }
        patterns.put(recipe.id(), pattern);
        return new Proof(patterns, supplies, standIns);
    }

    private boolean choose(CraftingRecipe recipe, List<List<ItemStack>> choices, List<ItemStack> grid,
                           int index, int width, int[] remaining, Set<String> visiting,
                           Map<AEItemKey, Proof> proofs, List<String> failures) {
        if (remaining[0] <= 0) return false;
        if (index == choices.size()) {
            remaining[0]--;
            return recipe.matches(CraftingInput.of(3, 3, grid), helper.getLevel());
        }
        int slot = recipe instanceof ShapedRecipe ? index / width * 3 + index % width : index;
        for (var stack : choices.get(index)) {
            if (!stack.isEmpty() && !proofs.containsKey(AEItemKey.of(stack))) {
                try {
                    var proof = resolve(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), false, visiting);
                    // Supplied component-bearing ingredients must expose precisely the key the pattern consumes.
                    if (proof.patterns().isEmpty()) proof = new Proof(Map.of(), Set.of(AEItemKey.of(stack)), proof.standIns());
                    proofs.put(AEItemKey.of(stack), proof);
                } catch (IllegalArgumentException exception) {
                    if (failures.size() < 3) failures.add(exception.getMessage());
                    continue;
                }
            }
            grid.set(slot, stack);
            if (choose(recipe, choices, grid, index + 1, width, remaining, visiting, proofs, failures)) return true;
        }
        grid.set(slot, ItemStack.EMPTY);
        return false;
    }

    private boolean machineLeaf(String item) {
        var recipes = probe.producers().getOrDefault(item, List.of());
        return frontier && recipes.stream().anyMatch(r -> !r.recipeClass().equals("crafting"))
                && recipes.stream().noneMatch(r -> MACHINE_BACKED_CLASSES.contains(r.recipeClass()));
    }

    private Proof standIn(String item) {
        return new Proof(Map.of(), Set.of(key(item)), Map.of(item, probe.producers().get(item).stream()
                .map(QuestCoverageCompatGameTests.Recipe::recipeClass).filter(type -> !type.equals("crafting"))
                .distinct().sorted().toList()));
    }

    private int ingredientRank(ItemStack stack) {
        var item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (probe.providedItems().contains(item) || !probe.producers().containsKey(item)) return 0;
        if (frontier && !hasCraftingRecipe(item)) return 1;
        return reachable.containsKey(item) ? 2 : 3;
    }

    static AEItemKey key(String item) {
        return AEItemKey.of(BuiltInRegistries.ITEM.get(ResourceLocation.parse(item)));
    }
}
