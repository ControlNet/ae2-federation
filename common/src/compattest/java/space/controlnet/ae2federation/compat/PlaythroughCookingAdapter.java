package space.controlnet.ae2federation.compat;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/** Fuel is a pattern input, inserted by a second Storage Bus on a horizontal furnace face. */
record PlaythroughCookingAdapter(String id, String type, String block) implements PlaythroughMachineAdapter {
    public String recipeClass() { return type; }
    public boolean accepts(QuestCoverageCompatGameTests.Recipe recipe) { return recipe.type().equals(type); }
    public Recipe read(GameTestHelper helper, RecipeHolder<?> holder, Function<Ingredient, ItemStack> choose) {
        if (!(holder.value() instanceof AbstractCookingRecipe recipe)) throw new IllegalArgumentException("Not a vanilla cooking recipe");
        int time = recipe.getCookingTime();
        if (time <= 0 || time % (id.equals("furnace") ? 100 : 50) != 0) throw new IllegalArgumentException("Cooking time cannot use an exact number of 100-tick sticks: " + time);
        return new Recipe(id, holder.id().toString(), List.of(GenericStack.fromItemStack(choose.apply(recipe.getIngredients().getFirst())),
                new GenericStack(AEItemKey.of(Items.STICK), time / (id.equals("furnace") ? 100 : 50))),
                List.of(GenericStack.fromItemStack(recipe.getResultItem(helper.getLevel().registryAccess()))), Map.of(), time);
    }
    public void place(PlaythroughMachines.Site site, Recipe recipe) {
        site.block(site.machine(), AddonCraftingScene.block(block));
        site.storageBus(site.machine().east(), Direction.WEST).getConfig().setStack(0, new GenericStack(AEItemKey.of(Items.STICK), 1));
        site.storageBus(site.machine().above(), Direction.DOWN).getConfig().setStack(0, new GenericStack(recipe.inputs().getFirst().what(), 1));
    }
    public String leftovers(GameTestHelper helper, BlockPos machine, Recipe recipe) {
        var furnace = helper.<AbstractFurnaceBlockEntity>getBlockEntity(machine);
        return furnace.isEmpty() ? "" : "Cooking input, fuel or output remains";
    }
}
