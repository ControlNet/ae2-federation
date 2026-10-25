package space.controlnet.ae2federation.compat;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;

/**
 * Smithing-table patterns, which AE2 encodes and its Molecular Assembler runs like crafting patterns. Template, base
 * and addition are all consumed, as at the vanilla smithing table; AE2's assembler returns no smithing remainders.
 */
final class PlaythroughSmithingAdapter implements PlaythroughMachineAdapter {
    public String id() { return "smithing"; }
    public String recipeType() { return "minecraft:smithing"; }
    public boolean assembler() { return true; }

    public Recipe read(GameTestHelper helper, RecipeHolder<?> holder, Function<Ingredient, ItemStack> choose,
                       Function<Ingredient, ItemStack> equipment) {
        // AE2's smithing pattern reads these fields; trims keep the base item and have no fixed output.
        if (!(holder.value() instanceof SmithingTransformRecipe recipe))
            throw new IllegalArgumentException("Only smithing-transform recipes have a fixed output; trims keep the base item: " + holder.value().getClass().getName());
        var slots = List.of(ingredient(recipe, "template"), ingredient(recipe, "base"), ingredient(recipe, "addition"));
        var stacks = slots.stream().map(choose).toList();
        var level = helper.getLevel();
        var input = new SmithingRecipeInput(stacks.get(0), stacks.get(1), stacks.get(2));
        if (!recipe.matches(input, level)) throw new IllegalArgumentException("Smithing recipe rejects its concrete inputs");
        var output = recipe.assemble(input, level.registryAccess());
        if (output.isEmpty()) throw new IllegalArgumentException("Smithing recipe has no output for its concrete inputs");
        if (recipe.getRemainingItems(input).stream().anyMatch(stack -> !stack.isEmpty()))
            throw new IllegalArgumentException("Smithing recipe declares remainders, which AE2's assembler does not return");
        return new Recipe(id(), holder.id().toString(), stacks.stream().map(GenericStack::fromItemStack).toList(),
                List.of(GenericStack.fromItemStack(output)), Map.of(), 0);
    }

    @SuppressWarnings("unchecked")
    public ItemStack encode(GameTestHelper helper, Recipe recipe) {
        var holder = (RecipeHolder<SmithingRecipe>) helper.getLevel().getRecipeManager()
                .byKey(ResourceLocation.parse(recipe.id())).orElseThrow();
        var inputs = recipe.inputs().stream().map(stack -> (AEItemKey) stack.what()).toList();
        var pattern = recipe.verify(helper, PatternDetailsHelper.encodeSmithingTablePattern(holder, inputs.get(0), inputs.get(1),
                inputs.get(2), (AEItemKey) recipe.outputs().getFirst().what(), false));
        if (!(PatternDetailsHelper.decodePattern(pattern, helper.getLevel()) instanceof IMolecularAssemblerSupportedPattern))
            throw new IllegalArgumentException("AE2 smithing pattern is not an assembler pattern: " + recipe.id());
        return pattern;
    }

    private static Ingredient ingredient(SmithingTransformRecipe recipe, String name) {
        try {
            var field = SmithingTransformRecipe.class.getDeclaredField(name);
            field.setAccessible(true);
            return (Ingredient) field.get(recipe);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Smithing recipe field unavailable: " + name, exception);
        }
    }

    public void place(PlaythroughMachines.Site site, Recipe recipe) {
        throw new IllegalStateException("Smithing patterns run in the factory's Molecular Assemblers: " + recipe.id());
    }
    public String leftovers(GameTestHelper helper, BlockPos machine, Recipe recipe) {
        throw new IllegalStateException("Smithing patterns have no shelf machine: " + recipe.id());
    }
}
