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
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.BlastFurnaceBlock;
import net.minecraft.world.level.block.SmokerBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/** Fuel is a pattern input, inserted by a second Storage Bus on a horizontal furnace face. */
record PlaythroughCookingAdapter(String id, String type, String block) implements PlaythroughMachineAdapter {
    public String recipeType() { return type; }
    @SuppressWarnings("unchecked")
    public Recipe read(GameTestHelper helper, RecipeHolder<?> holder, Function<Ingredient, ItemStack> choose,
                       Function<Ingredient, ItemStack> equipment) {
        if (!(holder.value() instanceof AbstractCookingRecipe recipe)) throw new IllegalArgumentException("Not a vanilla cooking recipe");
        var input = choose.apply(recipe.getIngredients().getFirst());
        // Both Storage Bus filters must stay disjoint: the side bus accepts only the stick fuel.
        if (input.is(Items.STICK)) throw new IllegalArgumentException("Cooking ingredient is the stick fuel");
        var level = helper.getLevel();
        var selected = level.getRecipeManager().getRecipeFor((RecipeType<AbstractCookingRecipe>) recipe.getType(),
                new SingleRecipeInput(input), level);
        if (selected.isEmpty() || !selected.get().id().equals(holder.id()))
            throw new IllegalArgumentException("Furnace selects another recipe for the concrete input: " + selected.map(RecipeHolder::id).orElse(null));
        int time = recipe.getCookingTime();
        int burn = fuelTicks(recipe.getType());
        if (time <= 0 || burn <= 0 || time % burn != 0)
            throw new IllegalArgumentException("Cooking time cannot use an exact number of " + burn + "-tick sticks: " + time);
        return new Recipe(id, holder.id().toString(), List.of(GenericStack.fromItemStack(input),
                new GenericStack(AEItemKey.of(Items.STICK), time / burn)),
                List.of(GenericStack.fromItemStack(recipe.getResultItem(level.registryAccess()))), Map.of(), time);
    }

    /** Burn time from NeoForge's furnace fuel data map; smokers and blast furnaces halve it in getBurnDuration. */
    private int fuelTicks(RecipeType<?> recipeType) {
        int burn = new ItemStack(Items.STICK).getBurnTime(recipeType);
        var machine = AddonCraftingScene.block(block);
        return machine instanceof SmokerBlock || machine instanceof BlastFurnaceBlock ? burn / 2 : burn;
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
