package space.controlnet.ae2federation.compat;

import static space.controlnet.ae2federation.compat.PlaythroughMekanismMachineAdapter.call;

import appeng.api.stacks.AEItemKey;
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

/**
 * Mekanism's Metallurgic Infuser: an item plus an infuse type. The infuse type arrives as items that the machine's
 * infusion slot converts through {@code mekanism:chemical_conversion} recipes, so the pattern holds the exact item
 * count that the recipe's chemical amount needs. A second Storage Bus on the machine's east side, filtered to that
 * item, feeds the infusion slot; the top bus is filtered to the main ingredient.
 */
final class PlaythroughMetallurgicInfuserAdapter implements PlaythroughMachineAdapter {
    private static final String CONVERSION = "mekanism:chemical_conversion";
    public String id() { return "metallurgic-infuser"; }
    public String recipeType() { return "mekanism:metallurgic_infusing"; }
    public Recipe read(GameTestHelper helper, RecipeHolder<?> holder, Function<Ingredient, ItemStack> choose,
                       Function<Ingredient, ItemStack> equipment) {
        var recipe = holder.value();
        // A per-tick chemical cost depends on the operation length and upgrades, not on a fixed item count.
        if ((boolean) call(recipe, "perTickUsage")) throw new IllegalArgumentException("Infusion uses its chemical per tick");
        var itemIngredient = call(recipe, "getItemInput");
        var chemicalIngredient = call(recipe, "getChemicalInput");
        long needed = (long) call(chemicalIngredient, "amount");
        var input = choose.apply(PlaythroughMekanismMachineAdapter.vanilla(itemIngredient));
        input = input.copyWithCount(Math.toIntExact((long) call(itemIngredient, "getNeededAmount", input)));
        // Items that the infusion slot converts, one at a time, into an accepted chemical dividing the requirement.
        var candidates = new ArrayList<ItemStack>();
        var rejected = new ArrayList<String>();
        for (var conversion : PlaythroughMekanismMachineAdapter.recipes(helper, CONVERSION))
            for (var stack : (List<?>) call(call(conversion.value(), "getInput"), "getRepresentations")) {
                var item = ((ItemStack) stack).copyWithCount(1);
                if (candidates.stream().anyMatch(known -> ItemStack.isSameItemSameComponents(known, item))) continue;
                try {
                    var chemical = conversion(helper, item);
                    if (!(boolean) call(chemicalIngredient, "testType", chemical)) continue;
                    long amount = (long) call(chemical, "getAmount");
                    if (needed % amount == 0) candidates.add(item);
                    else rejected.add(item.getItem() + " gives " + amount);
                } catch (IllegalArgumentException exception) {
                    rejected.add(exception.getMessage());
                }
            }
        if (candidates.isEmpty())
            throw new IllegalArgumentException("No conversion item supplies exactly " + needed + " of " + chemicalIngredient + ": " + rejected);
        var infuse = choose.apply(Ingredient.of(candidates.stream()));
        var chemical = conversion(helper, infuse);
        long amount = (long) call(chemical, "getAmount");
        // The Storage Bus filters stay disjoint: the side bus accepts only the infuse item.
        if (ItemStack.isSameItemSameComponents(input, infuse)) throw new IllegalArgumentException("Infusion ingredient is the infuse item");
        var supplied = call(chemical, "copyWithAmount", needed);
        // The machine runs the first recipe its input cache matches; another recipe for the same inputs could win.
        for (var other : PlaythroughMekanismMachineAdapter.recipes(helper, recipeType())) if (!other.id().equals(holder.id())
                && (boolean) call(call(other.value(), "getItemInput"), "testType", input)
                && (boolean) call(call(other.value(), "getChemicalInput"), "testType", supplied))
            throw new IllegalArgumentException("Another infusing recipe also accepts " + input + " with " + supplied + ": " + other.id());
        var output = (ItemStack) call(recipe, "getOutput", input, supplied);
        if (output.isEmpty()) throw new IllegalArgumentException("Infusion has no item output for " + input);
        return new Recipe(id(), holder.id().toString(), List.of(GenericStack.fromItemStack(input),
                new GenericStack(AEItemKey.of(infuse), needed / amount)), List.of(GenericStack.fromItemStack(output)), Map.of(),
                PlaythroughMekanismMachineAdapter.baseTicks(recipe, "mekanism.common.tile.machine.TileEntityMetallurgicInfuser"));
    }

    /**
     * What the infusion slot converts one item into, with a positive amount. An item that several conversions accept,
     * or that a conversion needs more than one of, is rejected.
     */
    private static Object conversion(GameTestHelper helper, ItemStack item) {
        Object result = null;
        for (var conversion : PlaythroughMekanismMachineAdapter.recipes(helper, CONVERSION)) {
            var input = call(conversion.value(), "getInput");
            if (!(boolean) call(input, "testType", item)) continue;
            if (result != null) throw new IllegalArgumentException("Several chemical conversions accept " + item);
            if ((long) call(input, "getNeededAmount", item) != 1) throw new IllegalArgumentException("Conversion needs several " + item);
            result = call(conversion.value(), "getOutput", item);
        }
        if (result == null || (long) call(result, "getAmount") <= 0) throw new IllegalArgumentException("No chemical conversion accepts " + item);
        return result;
    }

    public void place(PlaythroughMachines.Site site, Recipe recipe) {
        var helper = site.helper();
        site.block(site.machine(), AddonCraftingScene.block("mekanism:metallurgic_infuser"));
        PlaythroughMekanismMachineAdapter.power(site);
        MekanismSetup.configure(helper, site.machine(), "ITEM", "INPUT", Direction.UP, false);
        MekanismSetup.configure(helper, site.machine(), "ITEM", "EXTRA", Direction.EAST, false);
        MekanismSetup.configure(helper, site.machine(), "ITEM", "OUTPUT", Direction.DOWN, false);
        site.storageBus(site.machine().above(), Direction.DOWN).getConfig().setStack(0, new GenericStack(recipe.inputs().get(0).what(), 1));
        site.storageBus(site.machine().east(), Direction.WEST).getConfig().setStack(0, new GenericStack(recipe.inputs().get(1).what(), 1));
    }
    public String leftovers(GameTestHelper helper, BlockPos machine, Recipe recipe) {
        return PlaythroughMekanismMachineAdapter.idle(helper, machine);
    }
}
