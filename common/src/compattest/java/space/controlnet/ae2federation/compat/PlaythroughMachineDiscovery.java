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

/** Lets an adapter recover item outputs hidden from Phase 0's generic Recipe.getResultItem view. */
final class PlaythroughMachineDiscovery {
    static void discover(GameTestHelper helper, QuestCoverageCompatGameTests.Probe probe, Map<String, String> rejected) {
        var known = new java.util.HashSet<String>();
        probe.producers().values().stream().flatMap(List::stream).forEach(recipe -> known.add(recipe.id()));
        boolean changed = false;
        for (var holder : helper.getLevel().getRecipeManager().getRecipes()) {
            if (known.contains(holder.id().toString())) continue;
            var type = holder.value().getType();
            var registered = BuiltInRegistries.RECIPE_TYPE.getKey(type);
            String label = registered == null ? String.valueOf(type) : registered.toString();
            if (registered == null && ResourceLocation.tryParse(label) == null)
                label = "unregistered:" + (type == null ? "null" : type.getClass().getName());
            String recipeClass = label;
            var adapter = PlaythroughMachineAdapters.ENABLED.stream().filter(a -> a.recipeClass().equals(recipeClass)).findFirst().orElse(null);
            if (adapter == null) continue;
            try {
                var recipe = adapter.read(helper, holder, ingredient -> concrete(ingredient, probe.providedItems()));
                recipe.pattern(helper);
                var ingredients = ingredients(recipe);
                for (var output : recipe.outputs()) if (output.what() instanceof AEItemKey item) {
                    probe.addMachineRecipe(new QuestCoverageCompatGameTests.Recipe(holder.id().toString(), label, label,
                            BuiltInRegistries.ITEM.getKey(item.getItem()).toString(), ingredients, true));
                    changed = true;
                }
            } catch (RuntimeException exception) { rejected.put(holder.id().toString(), exception.toString()); }
        }
        // Newly discovered producers must not retain a provisional no-producer resource exemption.
        if (changed) probe.readResources(helper);
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
