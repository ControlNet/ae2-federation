package space.controlnet.ae2federation.test.mixed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public record MixedFactoryTopology(List<Item> alternatives, List<Item> chainOutputs,
        List<Recipe> blockedRecipes, List<Item> resources) {
    private static final List<Item> ALTERNATIVE_CATALOG = List.of(Items.COBBLESTONE, Items.DIRT, Items.SAND,
            Items.GRAVEL);
    private static final List<Item> CHAIN_CATALOG = List.of(Items.STONE, Items.IRON_INGOT, Items.GOLD_INGOT,
            Items.EMERALD, Items.DIAMOND, Items.NETHERITE_SCRAP);
    private static final List<Recipe> BLOCKED_CATALOG = List.of(new Recipe(Items.REDSTONE, Items.GLASS),
            new Recipe(Items.COAL, Items.BRICK), new Recipe(Items.CLAY_BALL, Items.TERRACOTTA));
    private static final List<Item> RESOURCE_CATALOG = List.of(Items.DIAMOND, Items.LAPIS_LAZULI, Items.QUARTZ,
            Items.COPPER_INGOT, Items.AMETHYST_SHARD, Items.FLINT, Items.CHARCOAL, Items.OBSIDIAN,
            Items.PRISMARINE_SHARD, Items.GLOWSTONE_DUST, Items.ENDER_PEARL, Items.BLAZE_POWDER);

    public MixedFactoryTopology {
        alternatives = List.copyOf(alternatives);
        chainOutputs = List.copyOf(chainOutputs);
        blockedRecipes = List.copyOf(blockedRecipes);
        resources = List.copyOf(resources);
    }

    public static MixedFactoryTopology from(MixedFactoryProfile profile) {
        var alternatives = ALTERNATIVE_CATALOG.subList(0, profile.alternativeInputs());
        var chain = CHAIN_CATALOG.subList(0, profile.recipeChainLength());
        var blocked = BLOCKED_CATALOG.subList(0, profile.blockedLanes());
        var required = new LinkedHashSet<Item>();
        required.add(Items.DIAMOND);
        required.add(Items.LAPIS_LAZULI);
        required.add(Items.QUARTZ);
        required.addAll(alternatives);
        required.addAll(chain);
        blocked.forEach(recipe -> {
            required.add(recipe.input());
            required.add(recipe.output());
        });
        if (required.size() > profile.resourceKeyCount()) {
            throw new IllegalArgumentException("Mixed resourceKeyCount cannot represent configured topology");
        }
        var shuffled = new ArrayList<>(RESOURCE_CATALOG);
        Collections.shuffle(shuffled, new Random(profile.seed()));
        for (var candidate : shuffled) {
            if (required.size() == profile.resourceKeyCount()) {
                break;
            }
            required.add(candidate);
        }
        if (required.size() != profile.resourceKeyCount()) {
            throw new IllegalArgumentException("Mixed resourceKeyCount exceeds supported deterministic resource catalog");
        }
        return new MixedFactoryTopology(alternatives, chain, blocked, List.copyOf(required));
    }

    public Item selectedAlternative(MixedFactoryProfile profile, int iteration) {
        return alternatives.get(profile.selectedAlternative(iteration));
    }

    public Item chainResult() {
        return chainOutputs.getLast();
    }

    public List<Recipe> processingRecipes() {
        var recipes = new ArrayList<Recipe>();
        alternatives.forEach(input -> recipes.add(new Recipe(input, chainOutputs.getFirst())));
        for (var index = 1; index < chainOutputs.size(); index++) {
            recipes.add(new Recipe(chainOutputs.get(index - 1), chainOutputs.get(index)));
        }
        recipes.addAll(blockedRecipes);
        return List.copyOf(recipes);
    }

    public List<Set<Integer>> lanesByPattern() {
        var result = new ArrayList<Set<Integer>>();
        var chainPatterns = alternatives.size() + chainOutputs.size() - 1;
        for (var index = 0; index < chainPatterns; index++) {
            result.add(Set.of(0));
        }
        for (var index = 0; index < blockedRecipes.size(); index++) {
            result.add(Set.of(index + 1));
        }
        return List.copyOf(result);
    }

    public LinkedHashMap<String, String> resourceAliases() {
        var aliases = new LinkedHashMap<String, String>();
        for (var item : resources) {
            var id = BuiltInRegistries.ITEM.getKey(item);
            aliases.put(id.getPath(), id.toString());
        }
        return aliases;
    }

    public record Recipe(Item input, Item output) {
    }
}
