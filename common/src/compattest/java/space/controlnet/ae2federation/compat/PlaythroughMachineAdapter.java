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

/** One independently registered recipe family. All transfers after placement use world automation. */
interface PlaythroughMachineAdapter {
    String id();
    String recipeClass();
    boolean accepts(QuestCoverageCompatGameTests.Recipe recipe);

    /** Reject unsupported recipes with a descriptive IllegalArgumentException; they remain frontier leaves. */
    Recipe read(GameTestHelper helper, RecipeHolder<?> holder, Function<Ingredient, ItemStack> choose);

    /** Non-item raw resources explicitly justified by this adapter's pack resource policy. */
    default java.util.Set<appeng.api.stacks.AEKey> providedInputs(GameTestHelper helper, Recipe recipe) { return java.util.Set.of(); }

    /** Extra physical requester cells. Chemical adapters return their pack's cell item ID here. */
    default java.util.Set<String> storageCells(Recipe recipe) {
        return recipe.outputs().stream().anyMatch(stack -> stack.what() instanceof appeng.api.stacks.AEFluidKey)
                ? java.util.Set.of("ae2:fluid_storage_cell_256k") : java.util.Set.of();
    }

    /** Fluid/chemical adapters instead place their own real return pipe to Site.returnPort(). */
    default boolean itemCollector() { return true; }

    default net.minecraft.core.Direction inputFace() { return net.minecraft.core.Direction.UP; }

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
            var pattern = PatternDetailsHelper.encodeProcessingPattern(inputs, outputs);
            var decoded = PatternDetailsHelper.decodePattern(pattern, helper.getLevel());
            if (decoded == null) throw new IllegalArgumentException("AE2 rejected processing recipe: " + id);
            var expected = new java.util.HashMap<appeng.api.stacks.AEKey, Long>();
            var actual = new java.util.HashMap<appeng.api.stacks.AEKey, Long>();
            inputs.forEach(stack -> expected.merge(stack.what(), stack.amount(), Math::addExact));
            for (var input : decoded.getInputs()) {
                var stack = input.getPossibleInputs()[0];
                actual.merge(stack.what(), Math.multiplyExact(stack.amount(), input.getMultiplier()), Math::addExact);
            }
            if (!expected.equals(actual)) throw new IllegalArgumentException("AE2 changed processing inputs: " + id);
            expected.clear(); actual.clear();
            outputs.forEach(stack -> expected.merge(stack.what(), stack.amount(), Math::addExact));
            decoded.getOutputs().forEach(stack -> actual.merge(stack.what(), stack.amount(), Math::addExact));
            if (!expected.equals(actual)) throw new IllegalArgumentException("AE2 changed processing outputs: " + id);
            return pattern;
        }
    }
}
