package space.controlnet.ae2federation.compat;

import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * A Mekanism item-to-item electric machine (Enrichment Chamber, Crusher). Recipes are read through Mekanism's own
 * recipe classes by reflection, since this test mod does not build against Mekanism. A Creative Energy Cube on the
 * machine's west powers it; it takes items on top and gives its product at the bottom, as a player configures them.
 */
record PlaythroughMekanismMachineAdapter(String id, String type, String block, String tile) implements PlaythroughMachineAdapter {
    public String recipeType() { return type; }
    public Recipe read(GameTestHelper helper, RecipeHolder<?> holder, Function<Ingredient, ItemStack> choose,
                       Function<Ingredient, ItemStack> equipment) {
        var recipe = holder.value();
        var ingredient = call(recipe, "getInput");
        var input = choose.apply(vanilla(ingredient));
        input = input.copyWithCount(Math.toIntExact((long) call(ingredient, "getNeededAmount", input)));
        // The machine runs the first recipe its input cache matches; another recipe for the same item could win.
        for (var other : recipes(helper, type)) if (!other.id().equals(holder.id())
                && (boolean) call(call(other.value(), "getInput"), "testType", input))
            throw new IllegalArgumentException("Another " + type + " recipe also accepts " + input + ": " + other.id());
        var output = (ItemStack) call(recipe, "getOutput", input);
        if (output.isEmpty()) throw new IllegalArgumentException("Mekanism recipe has no item output for " + input);
        return new Recipe(id, holder.id().toString(), List.of(GenericStack.fromItemStack(input)),
                List.of(GenericStack.fromItemStack(output)), Map.of(), baseTicks(recipe, tile));
    }
    public void place(PlaythroughMachines.Site site, Recipe recipe) {
        site.block(site.machine(), AddonCraftingScene.block(block));
        power(site);
        MekanismSetup.configure(site.helper(), site.machine(), "ITEM", "INPUT", Direction.UP, false);
        MekanismSetup.configure(site.helper(), site.machine(), "ITEM", "OUTPUT", Direction.DOWN, false);
    }
    public String leftovers(GameTestHelper helper, BlockPos machine, Recipe recipe) { return idle(helper, machine); }

    /** A filled Creative Energy Cube on the machine's west, ejecting into the machine's west energy input. */
    static void power(PlaythroughMachines.Site site) {
        var cube = site.machine().west();
        site.block(cube, AddonCraftingScene.block("mekanism:creative_energy_cube"));
        MekanismSetup.fill(site.helper(), cube);
        MekanismSetup.configure(site.helper(), cube, "ENERGY", "OUTPUT", Direction.EAST, true);
        MekanismSetup.configure(site.helper(), site.machine(), "ENERGY", "INPUT", Direction.WEST, false);
    }

    /** Empty when every internal slot and chemical tank is empty and no operation is in progress. */
    static String idle(GameTestHelper helper, BlockPos machine) {
        var entity = helper.getBlockEntity(machine);
        for (var slot : (List<?>) call(entity, "getInventorySlots", (Object) null)) {
            var stack = (ItemStack) call(slot, "getStack");
            if (!stack.isEmpty()) return "Mekanism slot holds " + stack;
        }
        for (var tank : (List<?>) call(entity, "getChemicalTanks", (Object) null)) {
            var stack = call(tank, "getStack");
            if (!(boolean) call(stack, "isEmpty")) return "Mekanism tank holds " + stack;
        }
        int ticks = (int) call(entity, "getOperatingTicks");
        return ticks == 0 ? "" : "Mekanism operation in progress: " + ticks + " ticks";
    }

    /** The NeoForge ingredient inside a Mekanism ItemStackIngredient. */
    static Ingredient vanilla(Object ingredient) {
        return ((net.neoforged.neoforge.common.crafting.SizedIngredient) call(ingredient, "ingredient")).ingredient();
    }

    @SuppressWarnings("unchecked")
    static List<RecipeHolder<?>> recipes(GameTestHelper helper, String type) {
        var recipeType = (RecipeType<net.minecraft.world.item.crafting.Recipe<net.minecraft.world.item.crafting.RecipeInput>>) BuiltInRegistries.RECIPE_TYPE
                .get(ResourceLocation.parse(type));
        if (recipeType == null) throw new IllegalArgumentException("Recipe type is not registered: " + type);
        return List.copyOf(helper.getLevel().getRecipeManager().getAllRecipesFor(recipeType));
    }

    /** A machine's unupgraded operation length, Mekanism's BASE_TICKS_REQUIRED for that block entity class. */
    static int baseTicks(Object recipe, String tile) {
        try {
            return Class.forName(tile, true, recipe.getClass().getClassLoader()).getField("BASE_TICKS_REQUIRED").getInt(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("No Mekanism operation length on " + tile, exception);
        }
    }

    /** Calls the public method {@code name} whose parameters accept {@code args}; reading failures reject the recipe. */
    static Object call(Object target, String name, Object... args) {
        for (var method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            var types = method.getParameterTypes();
            boolean fits = true;
            for (int i = 0; i < args.length && fits; i++)
                fits = args[i] == null ? !types[i].isPrimitive() : boxed(types[i]).isInstance(args[i]);
            if (!fits) continue;
            try {
                method.setAccessible(true);
                return method.invoke(target, args);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalArgumentException(target.getClass().getName() + "." + name + " failed", exception);
            }
        }
        throw new IllegalArgumentException(target.getClass().getName() + " has no " + name + " for " + java.util.Arrays.toString(args));
    }

    private static Class<?> boxed(Class<?> type) {
        return java.lang.invoke.MethodType.methodType(type).wrap().returnType();
    }
}
