package space.controlnet.ae2federation.compat;

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
 * ExtendedAE's Crystal Assembler: up to nine item stacks and an optional fluid, powered by AE energy from the input
 * subnet it joins. The Storage Bus above inserts items into its input slots and fluid into its tank.
 */
final class PlaythroughCrystalAssemblerAdapter implements PlaythroughMachineAdapter {
    private static final String RECIPE = "com.glodblock.github.extendedae.recipe.CrystalAssemblerRecipe";
    /** TileCrystalAssembler.MAX_PROGRESS, advanced 50 per tick with four Acceleration Cards. */
    private static final int TICKS = 200 / 50;

    public String id() { return "crystal-assembler"; }
    public String recipeType() { return "extendedae:crystal_assembler"; }

    public Recipe read(GameTestHelper helper, RecipeHolder<?> holder, Function<Ingredient, ItemStack> choose,
                       Function<Ingredient, ItemStack> equipment) {
        var recipe = holder.value();
        if (!recipe.getClass().getName().equals(RECIPE)) throw new IllegalArgumentException("Not an ExtendedAE crystal assembler recipe");
        var inputs = new ArrayList<GenericStack>();
        for (var input : (List<?>) PlaythroughAddonRecipes.call(recipe, "getInputs"))
            if (!(boolean) PlaythroughAddonRecipes.call(input, "isEmpty")) inputs.add(PlaythroughAddonRecipes.item(input, choose));
        var fluid = PlaythroughAddonRecipes.call(recipe, "getFluid");
        if (fluid != null && !(boolean) PlaythroughAddonRecipes.call(fluid, "isEmpty")) inputs.add(PlaythroughAddonRecipes.fluid(fluid));
        var merged = PlaythroughAddonRecipes.merged(inputs);
        var output = recipe.getResultItem(helper.getLevel().registryAccess());
        if (output.isEmpty()) throw new IllegalArgumentException("Crystal assembler recipe has no item output");
        var selected = select(helper, merged);
        if (selected == null || !selected.id().equals(holder.id()))
            throw new IllegalArgumentException("Crystal assembler selects another recipe for the concrete inputs: "
                    + (selected == null ? null : selected.id()));
        return new Recipe(id(), holder.id().toString(), merged, List.of(GenericStack.fromItemStack(output)), Map.of(), TICKS);
    }

    /** The assembler's own search: the first recipe of the type, in RecipeManager order, that one run's contents satisfy. */
    private static RecipeHolder<?> select(GameTestHelper helper, List<GenericStack> inputs) {
        var type = net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.get(
                net.minecraft.resources.ResourceLocation.parse("extendedae:crystal_assembler"));
        for (var candidate : helper.getLevel().getRecipeManager().getAllRecipesFor(cast(type))) {
            var contents = PlaythroughAddonRecipes.contents(inputs);
            boolean satisfied = true;
            for (var tester : (List<?>) PlaythroughAddonRecipes.call(candidate.value(), "getSample")) {
                for (var stack : contents) {
                    if ((boolean) PlaythroughAddonRecipes.call(tester, "checkType", stack)) PlaythroughAddonRecipes.call(tester, "consume", stack);
                    if ((boolean) PlaythroughAddonRecipes.call(tester, "isEmpty")) break;
                }
                if (!(boolean) PlaythroughAddonRecipes.call(tester, "isEmpty")) { satisfied = false; break; }
            }
            if (satisfied) return candidate;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T extends net.minecraft.world.item.crafting.Recipe<net.minecraft.world.item.crafting.RecipeInput>>
            net.minecraft.world.item.crafting.RecipeType<T> cast(net.minecraft.world.item.crafting.RecipeType<?> type) {
        return (net.minecraft.world.item.crafting.RecipeType<T>) type;
    }

    public Set<AEKey> providedInputs(GameTestHelper helper, Recipe recipe) { return PlaythroughAddonRecipes.WORLD_FLUIDS; }

    public void place(PlaythroughMachines.Site site, Recipe recipe) {
        site.block(site.machine(), AddonCraftingScene.block("extendedae:crystal_assembler"));
        var machine = (IUpgradeableObject) site.helper().<BlockEntity>getBlockEntity(site.machine());
        if (!machine.getUpgrades().addItems(AEItems.SPEED_CARD.stack(4)).isEmpty())
            throw new IllegalStateException("Crystal Assembler refused its four acceleration cards");
    }

    public String leftovers(GameTestHelper helper, BlockPos position, Recipe recipe) {
        var machine = helper.<BlockEntity>getBlockEntity(position);
        var inventory = (appeng.api.inventories.InternalInventory) PlaythroughAddonRecipes.call(machine, "getInternalInventory");
        for (int slot = 0; slot < inventory.size(); slot++)
            if (!inventory.getStackInSlot(slot).isEmpty()) return "Crystal Assembler slot " + slot + ": " + inventory.getStackInSlot(slot);
        var tank = (appeng.helpers.externalstorage.GenericStackInv) PlaythroughAddonRecipes.call(machine, "getTank");
        if (!tank.isEmpty()) return "Crystal Assembler tank: " + tank.getStack(0);
        return (boolean) PlaythroughAddonRecipes.call(machine, "isWorking") ? "Crystal Assembler working" : "";
    }
}
