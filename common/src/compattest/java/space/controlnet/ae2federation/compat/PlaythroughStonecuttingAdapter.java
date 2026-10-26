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
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;

/** Stonecutting patterns, which AE2 encodes by recipe ID and its Molecular Assembler runs like crafting patterns. */
final class PlaythroughStonecuttingAdapter implements PlaythroughMachineAdapter {
    public String id() { return "stonecutting"; }
    public String recipeType() { return "minecraft:stonecutting"; }
    public boolean assembler() { return true; }

    public Recipe read(GameTestHelper helper, RecipeHolder<?> holder, Function<Ingredient, ItemStack> choose,
                       Function<Ingredient, ItemStack> equipment) {
        if (!(holder.value() instanceof StonecutterRecipe recipe)) throw new IllegalArgumentException("Not a vanilla stonecutting recipe");
        var stack = choose.apply(recipe.getIngredients().getFirst());
        var level = helper.getLevel();
        var input = new SingleRecipeInput(stack);
        if (!recipe.matches(input, level)) throw new IllegalArgumentException("Stonecutting recipe rejects its concrete input");
        var output = recipe.assemble(input, level.registryAccess());
        if (output.isEmpty()) throw new IllegalArgumentException("Stonecutting recipe has no output for its concrete input");
        if (recipe.getRemainingItems(input).stream().anyMatch(remainder -> !remainder.isEmpty()))
            throw new IllegalArgumentException("Stonecutting recipe declares remainders, which AE2's assembler does not return");
        return new Recipe(id(), holder.id().toString(), List.of(GenericStack.fromItemStack(stack)),
                List.of(GenericStack.fromItemStack(output)), Map.of(), 0);
    }

    @SuppressWarnings("unchecked")
    public ItemStack encode(GameTestHelper helper, Recipe recipe) {
        var holder = (RecipeHolder<StonecutterRecipe>) helper.getLevel().getRecipeManager()
                .byKey(ResourceLocation.parse(recipe.id())).orElseThrow();
        var pattern = recipe.verify(helper, PatternDetailsHelper.encodeStonecuttingPattern(holder,
                (AEItemKey) recipe.inputs().getFirst().what(), (AEItemKey) recipe.outputs().getFirst().what(), false));
        if (!(PatternDetailsHelper.decodePattern(pattern, helper.getLevel()) instanceof IMolecularAssemblerSupportedPattern))
            throw new IllegalArgumentException("AE2 stonecutting pattern is not an assembler pattern: " + recipe.id());
        return pattern;
    }

    public void place(PlaythroughMachines.Site site, Recipe recipe) {
        throw new IllegalStateException("Stonecutting patterns run in the factory's Molecular Assemblers: " + recipe.id());
    }
    public String leftovers(GameTestHelper helper, BlockPos machine, Recipe recipe) {
        throw new IllegalStateException("Stonecutting patterns have no shelf machine: " + recipe.id());
    }
}
