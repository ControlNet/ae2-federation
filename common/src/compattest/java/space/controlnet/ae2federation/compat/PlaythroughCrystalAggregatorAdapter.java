package space.controlnet.ae2federation.compat;

import appeng.api.stacks.GenericStack;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.core.definitions.AEItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * AE2 Crystal Science's Crystal Aggregator: an ME-powered three-slot machine that spends a fixed AE energy cost per
 * craft. It joins the input subnet through the cable east of it and draws the layout's creative AE power from there.
 * Its slots accept any item, so the provider runs in blocking mode: a second batch arriving mid-craft could land in an
 * emptied slot beside a partial stack and leave no free slot for the third ingredient.
 */
final class PlaythroughCrystalAggregatorAdapter implements PlaythroughMachineAdapter {
    private static final String RECIPE = "io.github.lounode.ae2cs.common.recipe.crystal_aggregator.CrystalAggregatorRecipe";
    private static final String INPUT = "io.github.lounode.ae2cs.common.recipe.input.ThreeItemStackRecipeInput";
    /** CrystalAggregatorBlockEntity: 200 AE per tick, doubled by each of up to four acceleration cards. */
    private static final int AE_PER_TICK = 200 << 4;

    public String id() { return "crystal-aggregator"; }
    public String recipeType() { return "ae2cs:crystal_aggregator_recipe"; }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Recipe read(GameTestHelper helper, RecipeHolder<?> holder, Function<Ingredient, ItemStack> choose,
                       Function<Ingredient, ItemStack> equipment) {
        var recipe = holder.value();
        if (!recipe.getClass().getName().equals(RECIPE)) throw new IllegalArgumentException("Not a Crystal Aggregator recipe");
        var required = (List<SizedIngredient>) call(recipe, "required");
        if (required.isEmpty() || required.size() > 3) throw new IllegalArgumentException("Aggregator recipe needs 1 to 3 inputs: " + required.size());
        var inputs = new ArrayList<GenericStack>();
        var stacks = new ArrayList<ItemStack>();
        for (var ingredient : required) {
            var stack = choose.apply(ingredient.ingredient()).copyWithCount(ingredient.count());
            // The machine matches each ingredient in a distinct slot; one item would share a single slot.
            if (stacks.stream().anyMatch(other -> ItemStack.isSameItemSameComponents(other, stack)))
                throw new IllegalArgumentException("Two aggregator ingredients resolve to one item: " + stack);
            stacks.add(stack); inputs.add(GenericStack.fromItemStack(stack));
        }
        var output = ((ItemStack) call(recipe, "result")).copy();
        if (output.isEmpty()) throw new IllegalArgumentException("Aggregator recipe has no output");
        // A hopper below may report a simulated input as extractable; an output equal to an input could then grow it.
        if (stacks.stream().anyMatch(stack -> ItemStack.isSameItemSameComponents(stack, output)))
            throw new IllegalArgumentException("Aggregator output is also an input: " + output);
        while (stacks.size() < 3) stacks.add(ItemStack.EMPTY);
        RecipeInput input;
        try {
            input = (RecipeInput) Class.forName(INPUT).getMethod("of", ItemStack.class, ItemStack.class, ItemStack.class)
                    .invoke(null, stacks.get(0), stacks.get(1), stacks.get(2));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Crystal Aggregator recipe input unavailable", exception);
        }
        var level = helper.getLevel();
        var selected = level.getRecipeManager().getRecipeFor((RecipeType) recipe.getType(), input, level);
        if (selected.isEmpty() || !((RecipeHolder<?>) selected.get()).id().equals(holder.id()))
            throw new IllegalArgumentException("Aggregator selects another recipe for the concrete inputs");
        int energy = (Integer) call(recipe, "energyCost");
        if (energy <= 0) throw new IllegalArgumentException("Aggregator recipe has no energy cost: " + energy);
        return new Recipe(id(), holder.id().toString(), inputs, List.of(GenericStack.fromItemStack(output)), Map.of(),
                Math.ceilDiv(energy, AE_PER_TICK) + 1);
    }

    public void place(PlaythroughMachines.Site site, Recipe recipe) {
        site.block(site.machine(), AddonCraftingScene.block("ae2cs:crystal_aggregator"));
        var machine = (IUpgradeableObject) site.helper().getBlockEntity(site.machine());
        if (!machine.getUpgrades().addItems(AEItems.SPEED_CARD.stack(4)).isEmpty())
            throw new IllegalStateException("Crystal Aggregator refused its four acceleration cards");
        site.blocking();
    }

    public String leftovers(GameTestHelper helper, BlockPos position, Recipe recipe) {
        var machine = helper.getBlockEntity(position);
        var input = (appeng.api.inventories.InternalInventory) call(machine, "getInputInv");
        var output = (appeng.api.inventories.InternalInventory) call(machine, "getOutputInv");
        if (!input.isEmpty() || !output.isEmpty()) return "Aggregator holds input or output";
        return (Integer) call(machine, "getRecipeProgress") == 0 ? "" : "Aggregator in progress";
    }

    private static Object call(Object target, String method) {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Crystal Aggregator method unavailable: " + method, exception);
        }
    }
}
