package space.controlnet.ae2federation.compat;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.core.definitions.AEItems;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Data Energistics' Data Reassembler with four acceleration cards. It is an ME-powered machine that joins the input
 * subnet through the cable east of it; its nine input slots accumulate every pushed batch, and it runs the first
 * recipe, by ID, that the slot contents satisfy. Only item recipes are adapted: fluid inputs and the custom data flow
 * resource have no pack-justified supply here, and frequency-assigning outputs are not fixed stacks.
 */
final class PlaythroughDataReassemblerAdapter implements PlaythroughMachineAdapter {
    private static final String RECIPE = "com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerRecipe";
    private static final String INPUT = "com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerRecipeInput";
    private static final String RESOLVER = "com.fish_dan_.data_energistics.recipe.reassembler.DataReassemblerRecipeResolver";
    /** DataRipperReassemblerBlockEntity: nine 512-item input slots; each acceleration card saves 40 of the recipe's ticks. */
    private static final int INPUT_SLOTS = 9, SLOT_CAPACITY = 512, SPEED_CARDS = 4;

    public String id() { return "data-reassembler"; }
    public String recipeType() { return "data_energistics:data_reassembler"; }

    public Recipe read(GameTestHelper helper, RecipeHolder<?> holder, Function<Ingredient, ItemStack> choose,
                       Function<Ingredient, ItemStack> equipment) {
        var recipe = holder.value();
        if (!recipe.getClass().getName().equals(RECIPE)) throw new IllegalArgumentException("Not a Data Reassembler recipe");
        if (!((List<?>) call(recipe, "getFluidInputs")).isEmpty() || !((List<?>) call(recipe, "getFluidOutputs")).isEmpty())
            throw new IllegalArgumentException("Data Reassembler fluid inputs or outputs have no pack-justified supply in this adapter");
        if (call(recipe, "getKeyInput") != null || call(recipe, "getKeyOutput") != null)
            throw new IllegalArgumentException("Data Reassembler data flow resource has no pack-justified supply in this adapter");
        for (var output : (List<?>) call(recipe, "getItemOutputDefinitions"))
            if (call(output, "modifier") != null) throw new IllegalArgumentException("Data Reassembler output modifier is not a fixed stack: " + call(output, "modifier"));
        var amounts = new LinkedHashMap<AEItemKey, Long>();
        for (var requirement : (List<?>) call(recipe, "getItemInputs")) {
            var stack = choose.apply((Ingredient) call(requirement, "ingredient"));
            amounts.merge(AEItemKey.of(stack), (long) (Integer) call(requirement, "count"), Math::addExact);
        }
        if (amounts.isEmpty() || amounts.size() > INPUT_SLOTS) throw new IllegalArgumentException("Data Reassembler recipe needs 1 to 9 item inputs");
        var outputs = new ArrayList<GenericStack>();
        for (var stack : (List<?>) call(recipe, "getItemOutputs")) outputs.add(GenericStack.fromItemStack((ItemStack) stack));
        if (outputs.isEmpty()) throw new IllegalArgumentException("Data Reassembler recipe has no item output");
        // Batches accumulate in the slots, so the machine's recipe choice must hold for one run and for full slots.
        long most = amounts.values().stream().mapToLong(Long::longValue).max().orElseThrow();
        for (long runs : List.of(1L, Math.max(1, SLOT_CAPACITY / most))) {
            var selected = select(helper, amounts, runs);
            if (!holder.id().equals(selected))
                throw new IllegalArgumentException("Data Reassembler selects " + selected + " for " + runs + " stacked runs");
        }
        var inputs = amounts.entrySet().stream().map(entry -> new GenericStack(entry.getKey(), entry.getValue())).toList();
        int ticks = Math.max(1, (Integer) call(recipe, "getProcessTicks") - 40 * SPEED_CARDS);
        return new Recipe(id(), holder.id().toString(), inputs, outputs, Map.of(), ticks);
    }

    /** The recipe ID the machine's own resolver picks for these slot contents. */
    private static Object select(GameTestHelper helper, Map<AEItemKey, Long> amounts, long runs) {
        var items = new ArrayList<ItemStack>(Collections.nCopies(INPUT_SLOTS, ItemStack.EMPTY));
        int slot = 0;
        for (var entry : amounts.entrySet()) items.set(slot++, entry.getKey().toStack(Math.toIntExact(entry.getValue() * runs)));
        try {
            var input = Class.forName(INPUT).getConstructor(List.class, List.class, List.class)
                    .newInstance(items, List.of(), Collections.singletonList(null));
            var resolver = Class.forName(RESOLVER).getField("INSTANCE").get(null);
            var find = resolver.getClass().getMethod("find", net.minecraft.world.level.Level.class, Class.forName(INPUT),
                    it.unimi.dsi.fastutil.objects.ObjectSet.class);
            var found = (RecipeHolder<?>) find.invoke(resolver, helper.getLevel(), input, it.unimi.dsi.fastutil.objects.ObjectSets.emptySet());
            return found == null ? null : found.id();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Data Reassembler recipe resolver unavailable", exception);
        }
    }

    public void place(PlaythroughMachines.Site site, Recipe recipe) {
        site.block(site.machine(), AddonCraftingScene.block("data_energistics:data_reassembler"));
        var machine = (IUpgradeableObject) site.helper().getBlockEntity(site.machine());
        if (!machine.getUpgrades().addItems(AEItems.SPEED_CARD.stack(SPEED_CARDS)).isEmpty())
            throw new IllegalStateException("Data Reassembler refused its acceleration cards");
    }

    public String leftovers(GameTestHelper helper, BlockPos position, Recipe recipe) {
        var machine = helper.getBlockEntity(position);
        var storage = (appeng.api.inventories.InternalInventory) call(machine, "getStorageInventory");
        if (!storage.isEmpty()) return "Data Reassembler holds items";
        return (Boolean) call(machine, "isWorking") ? "Data Reassembler in progress" : "";
    }

    private static Object call(Object target, String method) {
        try {
            var handle = target.getClass().getMethod(method);
            handle.setAccessible(true);
            return handle.invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Data Reassembler method unavailable: " + method, exception);
        }
    }
}
