package space.controlnet.ae2federation.compat;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.misc.InscriberBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.recipes.handlers.InscriberRecipe;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

final class PlaythroughInscriberAdapter implements PlaythroughMachineAdapter {
    public String id() { return "inscriber"; }
    public String recipeType() { return "ae2:inscriber"; }
    public Recipe read(GameTestHelper helper, RecipeHolder<?> holder, Function<Ingredient, ItemStack> choose,
                       Function<Ingredient, ItemStack> equipment) {
        if (!(holder.value() instanceof InscriberRecipe recipe)) throw new IllegalArgumentException("Not an AE2 inscriber recipe");
        var inputs = new ArrayList<GenericStack>();
        var catalysts = new TreeMap<Integer, ItemStack>();
        inputs.add(GenericStack.fromItemStack(choose.apply(recipe.getMiddleInput())));
        var sides = java.util.List.of(recipe.getTopOptional(), recipe.getBottomOptional());
        for (int slot = 0; slot < sides.size(); slot++) if (!sides.get(slot).isEmpty()) {
            // Presses are not consumed; prefer a provided alternative so the recipe need not wait for one.
            var stack = recipe.getProcessType().name().equals("INSCRIBE")
                    ? equipment.apply(sides.get(slot)) : choose.apply(sides.get(slot));
            if (recipe.getProcessType().name().equals("INSCRIBE")) catalysts.put(slot, stack);
            else inputs.add(GenericStack.fromItemStack(stack));
        }
        return new Recipe(id(), holder.id().toString(), inputs,
                java.util.List.of(GenericStack.fromItemStack(recipe.getResultItem())), catalysts, 100);
    }
    public void place(PlaythroughMachines.Site site, Recipe recipe) {
        site.block(site.machine(), AEBlocks.INSCRIBER.block());
        var machine = site.helper().<InscriberBlockEntity>getBlockEntity(site.machine());
        machine.getConfigManager().putSetting(Settings.INSCRIBER_SEPARATE_SIDES, YesNo.NO);
        if (!machine.getUpgrades().addItems(AEItems.SPEED_CARD.stack(4)).isEmpty())
            throw new IllegalStateException("Inscriber refused its four acceleration cards");
        recipe.catalysts().forEach((slot, stack) -> machine.getInternalInventory().setItemDirect(slot, stack.copy()));
    }
    public String leftovers(GameTestHelper helper, BlockPos position, Recipe recipe) {
        var machine = helper.<InscriberBlockEntity>getBlockEntity(position);
        var inv = machine.getInternalInventory();
        for (int slot = 0; slot < inv.size(); slot++) {
            var expected = recipe.catalysts().getOrDefault(slot, ItemStack.EMPTY);
            if (!ItemStack.matches(expected, inv.getStackInSlot(slot))) return "Inscriber slot " + slot + ": " + inv.getStackInSlot(slot);
        }
        return machine.isSmash() || machine.getTask() != null ? "Inscriber active" : "";
    }
}
