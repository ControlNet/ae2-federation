package space.controlnet.ae2federation.compat;

import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Powah's Energizing Orb with one Nitro Energizing Rod, powered by a Creative Energy Cell on the rod's mounting face.
 * The orb takes one item per input slot and only while its output slot is empty, so the provider runs in blocking
 * mode; a second batch would otherwise join the first and match no recipe.
 */
final class PlaythroughEnergizingAdapter implements PlaythroughMachineAdapter {
    private static final String RECIPE = "owmii.powah.block.energizing.EnergizingRecipe";
    /** Orb slots 1 to 6 hold inputs; slot 0 holds the output. */
    private static final int INPUT_SLOTS = 6;
    /** Nitro rod transfer per tick in the pack's Powah config, after the rod's 20-tick warm-up. */
    private static final long ROD_TRANSFER = 200_000;

    public String id() { return "energizing"; }
    public String recipeType() { return "powah:energizing"; }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Recipe read(GameTestHelper helper, RecipeHolder<?> holder, Function<Ingredient, ItemStack> choose,
                       Function<Ingredient, ItemStack> equipment) {
        var recipe = holder.value();
        if (!recipe.getClass().getName().equals(RECIPE)) throw new IllegalArgumentException("Not a Powah energizing recipe");
        var ingredients = recipe.getIngredients();
        if (ingredients.isEmpty() || ingredients.size() > INPUT_SLOTS)
            throw new IllegalArgumentException("Energizing recipe needs 1 to 6 inputs: " + ingredients.size());
        var stacks = new ArrayList<ItemStack>();
        stacks.add(ItemStack.EMPTY);
        for (var ingredient : ingredients) stacks.add(choose.apply(ingredient).copyWithCount(1));
        var input = new RecipeInput() {
            @Override public ItemStack getItem(int index) { return stacks.get(index); }
            @Override public int size() { return stacks.size(); }
        };
        var level = helper.getLevel();
        var selected = level.getRecipeManager().getRecipeFor((RecipeType) recipe.getType(), input, level);
        if (selected.isEmpty() || !((RecipeHolder<?>) selected.get()).id().equals(holder.id()))
            throw new IllegalArgumentException("Energizing Orb selects another recipe for the concrete inputs");
        var output = recipe.getResultItem(level.registryAccess()).copy();
        if (output.isEmpty()) throw new IllegalArgumentException("Energizing recipe has no output");
        long energy = (Long) call(recipe, "getScaledEnergy");
        var inputs = stacks.subList(1, stacks.size()).stream().map(GenericStack::fromItemStack).toList();
        return new Recipe(id(), holder.id().toString(), inputs, List.of(GenericStack.fromItemStack(output)), Map.of(),
                Math.toIntExact(20 + Math.ceilDiv(energy, ROD_TRANSFER)));
    }

    public void place(PlaythroughMachines.Site site, Recipe recipe) {
        site.block(site.machine(), AddonCraftingScene.block("powah:energizing_orb"));
        var rod = site.machine().north();
        site.block(rod.above(), AddonCraftingScene.block("powah:energy_cell_creative"));
        // A rod receives energy only through its mounting face; a wrench link binds it to this orb explicitly.
        site.block(rod, AddonCraftingScene.block("powah:energizing_rod_nitro").defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.UP));
        var tile = site.helper().getBlockEntity(rod);
        try {
            tile.getClass().getMethod("setOrbPos", BlockPos.class).invoke(tile, site.helper().absolutePos(site.machine()));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Energizing rod link unavailable", exception);
        }
        site.blocking();
    }

    /** The orb and its Nitro Energizing Rod; the creative cell beside the rod is the site's power. */
    @Override public List<ItemStack> equipment(GameTestHelper helper, BlockPos machine, Recipe recipe) {
        var result = new ArrayList<>(PlaythroughMachineAdapter.super.equipment(helper, machine, recipe));
        result.add(new ItemStack(helper.getBlockState(machine.north()).getBlock().asItem()));
        return result;
    }

    public String leftovers(GameTestHelper helper, BlockPos position, Recipe recipe) {
        var orb = helper.getBlockEntity(position);
        var inventory = (IItemHandler) call(orb, "getInventory");
        for (int slot = 0; slot < inventory.getSlots(); slot++)
            if (!inventory.getStackInSlot(slot).isEmpty()) return "Energizing Orb slot " + slot + ": " + inventory.getStackInSlot(slot);
        return (Boolean) call(orb, "containRecipe") ? "Energizing Orb holds a recipe" : "";
    }

    private static Object call(Object target, String method) {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Powah method unavailable: " + method, exception);
        }
    }
}
