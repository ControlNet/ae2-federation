package space.controlnet.ae2federation.compat;

import appeng.api.stacks.GenericStack;
import appeng.blockentity.misc.ChargerBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.recipes.handlers.ChargerRecipe;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

final class PlaythroughChargerAdapter implements PlaythroughMachineAdapter {
    public String id() { return "charger"; }
    public String recipeClass() { return "ae2:charger"; }
    public boolean accepts(QuestCoverageCompatGameTests.Recipe recipe) { return recipe.type().equals("ae2:charger"); }
    public Recipe read(GameTestHelper helper, RecipeHolder<?> holder, Function<Ingredient, ItemStack> choose) {
        if (!(holder.value() instanceof ChargerRecipe recipe)) throw new IllegalArgumentException("Not an AE2 charger recipe");
        var input = choose.apply(recipe.getIngredient());
        var selected = appeng.blockentity.misc.ChargerRecipes.findRecipe(helper.getLevel(), input);
        if (selected == null || !ItemStack.matches(selected.getResultItem(), recipe.getResultItem()))
            throw new IllegalArgumentException("Charger selects another recipe for the concrete input");
        if (!appeng.blockentity.misc.ChargerRecipes.allowExtract(helper.getLevel(), recipe.getResultItem()))
            throw new IllegalArgumentException("Charger output is another charging input and cannot be extracted by automation");
        return new Recipe(id(), holder.id().toString(), List.of(GenericStack.fromItemStack(input)),
                List.of(GenericStack.fromItemStack(recipe.getResultItem())), Map.of(), 200);
    }
    public net.minecraft.core.Direction inputFace() { return net.minecraft.core.Direction.EAST; }
    public void place(PlaythroughMachines.Site site, Recipe recipe) { site.block(site.machine(), AEBlocks.CHARGER.block()); }
    public String leftovers(GameTestHelper helper, BlockPos machine, Recipe recipe) {
        var charger = helper.<ChargerBlockEntity>getBlockEntity(machine);
        return charger.getInternalInventory().isEmpty() && !charger.isWorking() ? "" : "Charger working or occupied";
    }
}
