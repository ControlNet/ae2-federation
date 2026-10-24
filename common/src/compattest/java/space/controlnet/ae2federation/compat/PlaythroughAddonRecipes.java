package space.controlnet.ae2federation.compat;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

/** Reads addon recipe ingredient stacks by reflection; the compat source set does not build against those mods. */
final class PlaythroughAddonRecipes {
    /**
     * Fluids a player pumps from the world: water is an infinite source and lava renews from pointed dripstone. Phase 0
     * lists both source blocks as natural blocks.
     */
    static final Set<AEKey> WORLD_FLUIDS = Set.of(AEFluidKey.of(Fluids.WATER), AEFluidKey.of(Fluids.LAVA));

    private PlaythroughAddonRecipes() {
    }

    static Object call(Object target, String method, Object... arguments) {
        try {
            for (var candidate : target.getClass().getMethods())
                if (candidate.getName().equals(method) && candidate.getParameterCount() == arguments.length)
                    return candidate.invoke(target, arguments);
            throw new IllegalArgumentException(target.getClass().getName() + " has no " + method + "/" + arguments.length);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot call " + method + " on " + target.getClass().getName(), exception);
        }
    }

    static Object field(Object target, String name) {
        try {
            var field = target.getClass().getField(name);
            return field.get(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot read " + name + " on " + target.getClass().getName(), exception);
        }
    }

    /** An item IngredientStack ({@code getIngredient}, {@code getAmount}) as a concrete stack of its exact amount. */
    static GenericStack item(Object stack, Function<Ingredient, ItemStack> choose) {
        var ingredient = (Ingredient) call(stack, "getIngredient");
        int amount = (int) call(stack, "getAmount");
        if (amount <= 0) throw new IllegalArgumentException("Nonpositive ingredient amount " + amount);
        return new GenericStack(AEItemKey.of(choose.apply(ingredient)), amount);
    }

    /** A fluid IngredientStack, preferring a world fluid among its alternatives. */
    static GenericStack fluid(Object stack) {
        var ingredient = (FluidIngredient) call(stack, "getIngredient");
        int amount = (int) call(stack, "getAmount");
        if (amount <= 0) throw new IllegalArgumentException("Nonpositive fluid amount " + amount);
        var choice = java.util.Arrays.stream(ingredient.getStacks()).filter(fluid -> !fluid.isEmpty())
                .sorted(java.util.Comparator.<FluidStack>comparingInt(fluid -> WORLD_FLUIDS.contains(AEFluidKey.of(fluid)) ? 0 : 1)
                        .thenComparing(fluid -> BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Fluid ingredient has no concrete fluid"));
        return new GenericStack(AEFluidKey.of(choice), amount);
    }

    /** Sums inputs that resolved to the same key, so the pattern lists each key once. */
    static List<GenericStack> merged(List<GenericStack> stacks) {
        var sums = new LinkedHashMap<AEKey, Long>();
        stacks.forEach(stack -> sums.merge(stack.what(), stack.amount(), Math::addExact));
        var result = new ArrayList<GenericStack>();
        sums.forEach((key, amount) -> result.add(new GenericStack(key, amount)));
        return result;
    }

    /** One run's inputs as the stacks a machine inventory and tank would hold. */
    static List<Object> contents(List<GenericStack> inputs) {
        var result = new ArrayList<Object>();
        for (var input : inputs) {
            if (input.what() instanceof AEItemKey item) result.add(item.toStack((int) input.amount()));
            else if (input.what() instanceof AEFluidKey fluid) result.add(fluid.toStack((int) input.amount()));
        }
        return result;
    }
}
