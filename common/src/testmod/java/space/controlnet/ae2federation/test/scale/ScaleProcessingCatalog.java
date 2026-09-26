package space.controlnet.ae2federation.test.scale;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import space.controlnet.ae2federation.test.processing.NativeProviderLaneFixtures;

public final class ScaleProcessingCatalog {
    public static final int SIZE = 256;
    private static final List<Recipe> RECIPES = createRecipes();

    private ScaleProcessingCatalog() {
    }

    public static List<Recipe> recipes() {
        return RECIPES;
    }

    public static void install(NativeProviderLaneFixtures provider) {
        install(provider, slot -> true);
    }

    public static void install(NativeProviderLaneFixtures provider, java.util.function.IntPredicate slots) {
        for (int slot = 0; slot < RECIPES.size(); slot++) {
            if (!slots.test(slot)) continue;
            var recipe = RECIPES.get(slot);
            provider.setPattern(slot, List.of(new GenericStack(AEItemKey.of(recipe.input()), 1)),
                    List.of(new GenericStack(AEItemKey.of(recipe.output()), 1)));
        }
        provider.refreshPatterns();
    }

    private static List<Recipe> createRecipes() {
        var items = BuiltInRegistries.ITEM.stream()
                .filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft"))
                .filter(item -> item != Items.AIR && item != Items.COBBLESTONE && item != Items.DIRT
                        && item != Items.DIAMOND && item != Items.GOLD_INGOT)
                .filter(item -> !new ItemStack(item).isEmpty())
                .sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()))
                .toList();
        if (items.size() < 2 * (SIZE - 2)) {
            throw new IllegalStateException("Not enough distinct vanilla items for physical Processing recipes");
        }
        var recipes = new ArrayList<Recipe>(SIZE);
        recipes.add(new Recipe(Items.COBBLESTONE, Items.DIAMOND));
        recipes.add(new Recipe(Items.DIRT, Items.GOLD_INGOT));
        for (int index = 0; index < SIZE - 2; index++) {
            recipes.add(new Recipe(items.get(index), items.get(index + SIZE - 2)));
        }
        return List.copyOf(recipes);
    }

    public record Recipe(Item input, Item output) {
    }
}
