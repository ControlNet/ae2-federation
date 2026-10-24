package space.controlnet.ae2federation.compat;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.core.definitions.AEItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Advanced AE's Reaction Chamber: item stacks and one fluid, run on AE energy drawn from the input subnet it joins.
 * Its recipe type has no registry key; Phase 0 records the type's own name.
 */
final class PlaythroughReactionChamberAdapter implements PlaythroughMachineAdapter {
    private static final String RECIPE = "net.pedroksl.advanced_ae.recipes.ReactionChamberRecipe";
    private static final String RECIPES = "net.pedroksl.advanced_ae.recipes.ReactionChamberRecipes";
    /** ReactionChamberEntity's 200 processing steps, advanced 50 per tick with four Acceleration Cards. */
    private static final int TICKS = 200 / 50;

    public String id() { return "reaction-chamber"; }
    public String recipeType() { return "advanced_ae:reaction"; }

    public Recipe read(GameTestHelper helper, RecipeHolder<?> holder, Function<Ingredient, ItemStack> choose,
                       Function<Ingredient, ItemStack> equipment) {
        var recipe = holder.value();
        if (!recipe.getClass().getName().equals(RECIPE)) throw new IllegalArgumentException("Not an Advanced AE reaction recipe");
        var output = (GenericStack) PlaythroughAddonRecipes.field(recipe, "output");
        // The hopper collects items; a fluid product would need its own return pipe.
        if (!(output.what() instanceof AEItemKey) || output.amount() <= 0)
            throw new IllegalArgumentException("Reaction output is not an item: " + output);
        var inputs = new ArrayList<GenericStack>();
        for (var input : (List<?>) PlaythroughAddonRecipes.call(recipe, "getInputs"))
            if (!(boolean) PlaythroughAddonRecipes.call(input, "isEmpty")) inputs.add(PlaythroughAddonRecipes.item(input, choose));
        var fluid = PlaythroughAddonRecipes.call(recipe, "getFluid");
        GenericStack fluidInput = null;
        if (fluid != null && !(boolean) PlaythroughAddonRecipes.call(fluid, "isEmpty")) {
            fluidInput = PlaythroughAddonRecipes.fluid(fluid);
            inputs.add(fluidInput);
        }
        var merged = PlaythroughAddonRecipes.merged(inputs);
        var items = new ArrayList<ItemStack>();
        for (var stack : merged) if (stack.what() instanceof AEItemKey item) items.add(item.toStack((int) stack.amount()));
        // The chamber's own lookup: the first recipe whose every input one run's contents cover.
        Object selected;
        try {
            selected = Class.forName(RECIPES, true, recipe.getClass().getClassLoader())
                    .getMethod("findRecipe", net.minecraft.world.level.Level.class, List.class, GenericStack.class)
                    .invoke(null, helper.getLevel(), items, fluidInput);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Advanced AE's reaction recipe lookup is unavailable", exception);
        }
        if (selected != recipe) throw new IllegalArgumentException("Reaction Chamber selects another recipe for the concrete inputs");
        return new Recipe(id(), holder.id().toString(), merged, List.of(output), Map.of(), TICKS);
    }

    public Set<AEKey> providedInputs(GameTestHelper helper, Recipe recipe) { return PlaythroughAddonRecipes.WORLD_FLUIDS; }

    public void place(PlaythroughMachines.Site site, Recipe recipe) {
        site.block(site.machine(), AddonCraftingScene.block("advanced_ae:reaction_chamber"));
        var machine = (IUpgradeableObject) site.helper().<BlockEntity>getBlockEntity(site.machine());
        if (!machine.getUpgrades().addItems(AEItems.SPEED_CARD.stack(4)).isEmpty())
            throw new IllegalStateException("Reaction Chamber refused its four acceleration cards");
    }

    public String leftovers(GameTestHelper helper, BlockPos position, Recipe recipe) {
        var machine = helper.<BlockEntity>getBlockEntity(position);
        var inventory = (appeng.api.inventories.InternalInventory) PlaythroughAddonRecipes.call(machine, "getInternalInventory");
        for (int slot = 0; slot < inventory.size(); slot++)
            if (!inventory.getStackInSlot(slot).isEmpty()) return "Reaction Chamber slot " + slot + ": " + inventory.getStackInSlot(slot);
        var tank = (appeng.helpers.externalstorage.GenericStackInv) PlaythroughAddonRecipes.call(machine, "getTank");
        for (int slot = 0; slot < tank.size(); slot++)
            if (tank.getStack(slot) != null) return "Reaction Chamber tank " + slot + ": " + tank.getStack(slot);
        return (boolean) PlaythroughAddonRecipes.call(machine, "isWorking") ? "Reaction Chamber working" : "";
    }

}
