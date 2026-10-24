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
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Mystical Agriculture's Seed Reprocessor, without upgrades. It burns fuel into its own FE buffer and spends a fixed
 * amount per operation, so each run's processing pattern carries the exact number of sticks: the input enters from the
 * top and the fuel through the north face, the only faces that accept them.
 */
final class PlaythroughReprocessorAdapter implements PlaythroughMachineAdapter {
    private static final String RECIPE = "com.blakebr0.mysticalagriculture.crafting.recipe.ReprocessorRecipe";
    /** ReprocessorTileEntity without a machine upgrade: 200 ticks at 20 FE per tick; fuel gives 20 FE per burn tick. */
    private static final int OPERATION_TICKS = 200;
    private static final int FE_PER_OPERATION = 20 * OPERATION_TICKS;
    private static final int FE_PER_BURN_TICK = 20;

    public String id() { return "reprocessor"; }
    public String recipeType() { return "mysticalagriculture:reprocessor"; }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Recipe read(GameTestHelper helper, RecipeHolder<?> holder, Function<Ingredient, ItemStack> choose,
                       Function<Ingredient, ItemStack> equipment) {
        var recipe = holder.value();
        if (!recipe.getClass().getName().equals(RECIPE)) throw new IllegalArgumentException("Not a Seed Reprocessor recipe");
        var input = choose.apply(recipe.getIngredients().getFirst());
        // Both Storage Bus filters must stay disjoint: the north bus accepts only the stick fuel.
        if (input.is(Items.STICK)) throw new IllegalArgumentException("Reprocessor ingredient is the stick fuel");
        var level = helper.getLevel();
        var selected = level.getRecipeManager().getRecipeFor((RecipeType) recipe.getType(), CraftingInput.of(1, 1, List.of(input)), level);
        if (selected.isEmpty() || !((RecipeHolder<?>) selected.get()).id().equals(holder.id()))
            throw new IllegalArgumentException("Reprocessor selects another recipe for the concrete input");
        var output = recipe.getResultItem(level.registryAccess()).copy();
        if (output.isEmpty()) throw new IllegalArgumentException("Reprocessor recipe has no output");
        int stick = new ItemStack(Items.STICK).getBurnTime(null) * FE_PER_BURN_TICK;
        if (stick <= 0 || FE_PER_OPERATION % stick != 0)
            throw new IllegalArgumentException("Reprocessor operation cannot use an exact number of " + stick + " FE sticks");
        return new Recipe(id(), holder.id().toString(), List.of(GenericStack.fromItemStack(input),
                new GenericStack(AEItemKey.of(Items.STICK), FE_PER_OPERATION / stick)),
                List.of(GenericStack.fromItemStack(output)), Map.of(), OPERATION_TICKS);
    }

    public Direction inputFace() { return Direction.NORTH; }

    public void place(PlaythroughMachines.Site site, Recipe recipe) {
        site.block(site.machine(), AddonCraftingScene.block("mysticalagriculture:seed_reprocessor"));
        site.storageBus(site.machine().north(), Direction.SOUTH).getConfig().setStack(0, new GenericStack(AEItemKey.of(Items.STICK), 1));
        site.storageBus(site.machine().above(), Direction.DOWN).getConfig().setStack(0, new GenericStack(recipe.inputs().getFirst().what(), 1));
    }

    public String leftovers(GameTestHelper helper, BlockPos position, Recipe recipe) {
        var machine = helper.getBlockEntity(position);
        var inventory = (IItemHandler) call(machine, "getInventory");
        for (int slot = 0; slot < inventory.getSlots(); slot++)
            if (!inventory.getStackInSlot(slot).isEmpty()) return "Reprocessor slot " + slot + ": " + inventory.getStackInSlot(slot);
        var energy = (net.neoforged.neoforge.energy.IEnergyStorage) call(machine, "getEnergy");
        if ((Integer) call(machine, "getFuelLeft") != 0 || energy.getEnergyStored() != 0)
            return "Reprocessor fuel left: " + call(machine, "getFuelLeft") + " burning, " + energy.getEnergyStored() + " FE stored";
        return (Integer) call(machine, "getProgress") == 0 ? "" : "Reprocessor in progress";
    }

    private static Object call(Object target, String method) {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Reprocessor method unavailable: " + method, exception);
        }
    }
}
