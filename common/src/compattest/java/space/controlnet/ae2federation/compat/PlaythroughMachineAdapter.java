package space.controlnet.ae2federation.compat;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * One independently registered recipe family. All transfers after placement use world automation.
 *
 * <p>Geometry: the machine stands at {@link PlaythroughMachines.Site#machine()}, two blocks above the provider. Its
 * DOWN face is the output face: a hopper below it collects item output into {@link PlaythroughMachines.Site#returnPort()}
 * (a non-item adapter replaces the hopper with its own return there), so DOWN cannot be an input face. The powered
 * input subnet always reaches {@code machine.above()} and {@code machine.east()}, and also
 * {@code machine.relative(inputFace())}, where a Storage Bus faces the machine.
 */
interface PlaythroughMachineAdapter {
    String id();

    /** Registered recipe type ID, as Phase 0 records it in {@code Recipe.type()}; discovery and selection both use it. */
    String recipeType();

    default boolean accepts(QuestCoverageCompatGameTests.Recipe recipe) { return recipe.type().equals(recipeType()); }

    /**
     * Reject unsupported recipes with a descriptive IllegalArgumentException; they remain frontier leaves.
     * {@code choose} selects a consumed ingredient; {@code equipment} selects fixed equipment such as a press,
     * preferring Phase 0 provided items.
     */
    Recipe read(GameTestHelper helper, RecipeHolder<?> holder, Function<Ingredient, ItemStack> choose,
                Function<Ingredient, ItemStack> equipment);

    /** Non-item raw resources explicitly justified by this adapter's pack resource policy. */
    default java.util.Set<appeng.api.stacks.AEKey> providedInputs(GameTestHelper helper, Recipe recipe) { return java.util.Set.of(); }

    /** Extra physical requester cells. Chemical adapters return their pack's cell item ID here. */
    default java.util.Set<String> storageCells(Recipe recipe) {
        return recipe.outputs().stream().anyMatch(stack -> stack.what() instanceof appeng.api.stacks.AEFluidKey)
                ? java.util.Set.of("ae2:fluid_storage_cell_256k") : java.util.Set.of();
    }

    /** Fluid/chemical adapters instead place their own real return pipe to Site.returnPort(). */
    default boolean itemCollector() { return true; }

    /** Any face except DOWN, which is reserved for the output collector. */
    default net.minecraft.core.Direction inputFace() { return net.minecraft.core.Direction.UP; }

    /**
     * True for AE2 pattern kinds that the factory's Molecular Assemblers run beside crafting patterns, such as
     * smithing and stonecutting: they get no shelf site, and {@link #place}/{@link #leftovers} are never called.
     */
    default boolean assembler() { return false; }

    /** The installed AE2 pattern; shelf machines use the processing pattern of the recipe's exact amounts. */
    default ItemStack encode(GameTestHelper helper, Recipe recipe) { return recipe.pattern(helper); }

    /** Placement receives tracked block/part operations and a powered subnet, shared by every machine path. */
    void place(PlaythroughMachines.Site site, Recipe recipe);

    /** Empty means idle, with no consumable inputs or output; installed catalysts are checked separately. */
    String leftovers(GameTestHelper helper, BlockPos machine, Recipe recipe);

    record Recipe(String adapter, String id, List<GenericStack> inputs, List<GenericStack> outputs,
                  Map<Integer, ItemStack> catalysts, int ticks) {
        ItemStack pattern(GameTestHelper helper) {
            if (inputs.isEmpty() || outputs.isEmpty() || inputs.stream().anyMatch(s -> s.amount() <= 0)
                    || outputs.stream().anyMatch(s -> s.amount() <= 0))
                throw new IllegalArgumentException("Empty or nonpositive processing recipe: " + id);
            return verify(helper, PatternDetailsHelper.encodeProcessingPattern(inputs, outputs));
        }

        /** AE2 must decode {@code pattern} with exactly this recipe's inputs and outputs. */
        ItemStack verify(GameTestHelper helper, ItemStack pattern) {
            var decoded = PatternDetailsHelper.decodePattern(pattern, helper.getLevel());
            if (decoded == null) throw new IllegalArgumentException("AE2 rejected pattern: " + id);
            var expected = new java.util.HashMap<appeng.api.stacks.AEKey, Long>();
            var actual = new java.util.HashMap<appeng.api.stacks.AEKey, Long>();
            inputs.forEach(stack -> expected.merge(stack.what(), stack.amount(), Math::addExact));
            for (var input : decoded.getInputs()) {
                var stack = input.getPossibleInputs()[0];
                actual.merge(stack.what(), Math.multiplyExact(stack.amount(), input.getMultiplier()), Math::addExact);
            }
            if (!expected.equals(actual)) throw new IllegalArgumentException("AE2 changed pattern inputs: " + id);
            expected.clear(); actual.clear();
            outputs.forEach(stack -> expected.merge(stack.what(), stack.amount(), Math::addExact));
            decoded.getOutputs().forEach(stack -> actual.merge(stack.what(), stack.amount(), Math::addExact));
            if (!expected.equals(actual)) throw new IllegalArgumentException("AE2 changed pattern outputs: " + id);
            return pattern;
        }
    }
}
