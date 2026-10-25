package space.controlnet.ae2federation.compat;

import appeng.api.stacks.AEItemKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Lets an adapter recover item outputs hidden from Phase 0's generic Recipe.getResultItem view. */
final class PlaythroughMachineDiscovery {
    /** Registers only recipes accepted under the resource view their registration produces. */
    static void discover(GameTestHelper helper, QuestCoverageCompatGameTests.Probe probe, Map<String, String> rejected) {
        var known = new java.util.HashSet<String>();
        probe.producers().values().stream().flatMap(List::stream).forEach(recipe -> known.add(recipe.id()));
        var candidates = new java.util.LinkedHashMap<RecipeHolder<?>, PlaythroughMachineAdapter>();
        for (var holder : helper.getLevel().getRecipeManager().getRecipes()) {
            if (known.contains(holder.id().toString())) continue;
            var type = holder.value().getType();
            var registered = BuiltInRegistries.RECIPE_TYPE.getKey(type);
            String label = registered == null ? String.valueOf(type) : registered.toString();
            if (registered == null && ResourceLocation.tryParse(label) == null)
                label = "unregistered:" + (type == null ? "null" : type.getClass().getName());
            String recipeType = label;
            PlaythroughMachineAdapters.ENABLED.stream().filter(a -> a.recipeType().equals(recipeType)).findFirst()
                    .ifPresent(adapter -> candidates.put(holder, adapter));
        }
        var provided = probe.providedItems();
        var accepted = new ArrayList<QuestCoverageCompatGameTests.Recipe>();
        while (true) {
            accepted.clear();
            var view = provided;
            for (var iterator = candidates.entrySet().iterator(); iterator.hasNext(); ) {
                var entry = iterator.next();
                var holder = entry.getKey();
                var adapter = entry.getValue();
                String label = adapter.recipeType();
                try {
                    var recipe = adapter.read(helper, holder, ingredient -> concrete(ingredient, view), ingredient -> concrete(ingredient, view));
                    validate(helper, adapter, recipe, view);
                    var ingredients = ingredients(recipe);
                    for (var output : recipe.outputs()) if (output.what() instanceof AEItemKey item)
                        accepted.add(new QuestCoverageCompatGameTests.Recipe(holder.id().toString(), label, label,
                                BuiltInRegistries.ITEM.getKey(item.getItem()).toString(), ingredients, true));
                } catch (RuntimeException exception) {
                    rejected.put(holder.id().toString(), exception.toString());
                    iterator.remove();
                }
            }
            if (accepted.isEmpty()) break;
            // New producers remove provisional no-producer exemptions; recheck the survivors under that view.
            var next = probe.providedWith(helper, accepted);
            if (next.equals(provided)) break;
            provided = next;
        }
        accepted.forEach(probe::addMachineRecipe);
        if (!accepted.isEmpty()) probe.readResources(helper);
    }

    /** Shared acceptance: justified non-item inputs, provided fixed catalysts and an exact AE2 round trip. */
    static ItemStack validate(GameTestHelper helper, PlaythroughMachineAdapter adapter, PlaythroughMachineAdapter.Recipe machine,
                              Set<String> provided) {
        for (var input : machine.inputs()) if (!(input.what() instanceof AEItemKey)
                && !adapter.providedInputs(helper, machine).contains(input.what()))
            throw new IllegalArgumentException("Adapter has no provided-resource proof for " + input.what());
        for (var catalyst : machine.catalysts().values())
            if (!provided.contains(BuiltInRegistries.ITEM.getKey(catalyst.getItem()).toString()))
                throw new IllegalArgumentException("Fixed catalyst is not in Phase 0's provided resources: " + catalyst);
        return adapter.encode(helper, machine);
    }

    static ItemStack concrete(Ingredient ingredient, Set<String> provided) {
        return java.util.Arrays.stream(ingredient.getItems()).filter(stack -> !stack.isEmpty())
                .sorted(java.util.Comparator.<ItemStack>comparingInt(stack -> provided.contains(
                        BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()) ? 0 : 1)
                        .thenComparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("No concrete ingredient")).copyWithCount(1);
    }

    static List<List<String>> ingredients(PlaythroughMachineAdapter.Recipe recipe) {
        var result = new ArrayList<List<String>>();
        for (var input : recipe.inputs()) if (input.what() instanceof AEItemKey item)
            result.add(List.of(BuiltInRegistries.ITEM.getKey(item.getItem()).toString()));
        return result;
    }
}
