package space.controlnet.ae2federation.automation;

import appeng.api.crafting.IPatternDetails;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.mixin.SmithingTransformRecipeAccess;
import space.controlnet.ae2federation.script.ListValue;
import space.controlnet.ae2federation.script.RecordValue;

/**
 * The recipes AE2 can write on patterns, found by the item they make: crafting-table recipes, stonecutting, and
 * smithing upgrades. Built from the server's recipes when the server has started, and again after a datapack reload,
 * which replaces the server's recipe manager: a large pack's take about a second (ATM10's 94,646 recipes, 1.1 s), which
 * a program's first call would otherwise stop the running server for.
 *
 * <p>A recipe reads as its slots, each with the stacks it accepts: the crafting grid's nine row by row (a shaped recipe
 * in its top-left corner), the stonecutter's one, the smithing table's template, base and addition.
 *
 * <p>Assistant choices: a recipe is left out when AE2 could not use its pattern or a program could not name what it
 * makes: special recipes (map copies, fireworks, dyed armor), recipes with an ingredient that matches nothing, results
 * with components (an armor trim's result is a trimmed chestplate, and a program names items by ID only), and smithing
 * recipes other than upgrades, as AE2 decodes no others. A crafting recipe other than {@link ShapedRecipe} fills the
 * grid in order, as a shapeless one; writing it checks that the recipe matches.
 */
public final class RecipeIndex {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(RecipeIndex.class);
    /** Each recipe manager's index; a reload's new manager builds a new one, and the old one goes with its manager. */
    private static final Map<RecipeManager, RecipeIndex> BUILT = new WeakHashMap<>();

    /** A recipe as a pattern sees it: its slots, each with the stacks it accepts, and what it makes. */
    record Entry(RecipeHolder<?> holder, String kind, List<List<ItemStack>> slots, ItemStack output, int width,
            int height) {
        String id() {
            return holder.id().toString();
        }
    }

    private final Map<Item, List<Entry>> byOutput;
    private final Map<ResourceLocation, Entry> byId;

    private RecipeIndex(RecipeManager manager, RegistryAccess registries) {
        var outputs = new HashMap<Item, List<Entry>>();
        var ids = new HashMap<ResourceLocation, Entry>();
        for (var holder : manager.getRecipes()) {
            Entry entry;
            try {
                entry = entry(holder, registries);
            } catch (RuntimeException broken) {
                // An addon's recipe that fails to list its ingredients or result is one no pattern can hold either.
                LOGGER.debug("Recipe {} cannot be read for patterns", holder.id(), broken);
                continue;
            }
            if (entry != null) {
                ids.put(holder.id(), entry);
                outputs.computeIfAbsent(entry.output().getItem(), item -> new ArrayList<>()).add(entry);
            }
        }
        outputs.values().forEach(list -> list.sort(Comparator.comparing(Entry::id)));
        this.byOutput = outputs;
        this.byId = ids;
    }

    /** Once, at mod start. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> of(event.getServer()));
        // With no player, the event is a reload's, after the server took the new recipes.
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> {
            if (event.getPlayer() == null) {
                of(event.getPlayerList().getServer());
            }
        });
    }

    static RecipeIndex of(MinecraftServer server) {
        return BUILT.computeIfAbsent(server.getRecipeManager(),
                manager -> new RecipeIndex(manager, server.registryAccess()));
    }

    /** The recipes that make the item, by ID. */
    List<Entry> making(Item item) {
        return byOutput.getOrDefault(item, List.of());
    }

    /** The recipe of that ID, if a pattern can hold it. */
    @Nullable Entry byId(String id) {
        var location = ResourceLocation.tryParse(id);
        return location == null ? null : byId.get(location);
    }

    // ---- reading recipes ----

    private static @Nullable Entry entry(RecipeHolder<?> holder, RegistryAccess registries) {
        var recipe = holder.value();
        if (recipe.isSpecial() || recipe.isIncomplete()) {
            return null;
        }
        var output = recipe.getResultItem(registries);
        if (output.isEmpty() || !output.getComponentsPatch().isEmpty()) {
            return null;
        }
        if (recipe instanceof CraftingRecipe) {
            return crafting(holder, output);
        }
        if (recipe instanceof StonecutterRecipe stonecutting) {
            var slots = slots(List.of(stonecutting.getIngredients().get(0)));
            return slots == null ? null : new Entry(holder, "stonecutting", slots, output, 0, 0);
        }
        if (recipe instanceof SmithingTransformRecipe smithing) {
            var access = (SmithingTransformRecipeAccess) smithing;
            var parts = List.of(access.ae2federation$template(), access.ae2federation$base(),
                    access.ae2federation$addition());
            // AE2's smithing pattern needs all three.
            var slots = parts.stream().anyMatch(Ingredient::isEmpty) ? null : slots(parts);
            return slots == null ? null : new Entry(holder, "smithing", slots, output, 0, 0);
        }
        return null;
    }

    private static @Nullable Entry crafting(RecipeHolder<?> holder, ItemStack output) {
        var ingredients = holder.value().getIngredients();
        var cells = new ArrayList<>(Collections.nCopies(9, Ingredient.EMPTY));
        int width = 0;
        int height = 0;
        if (holder.value() instanceof ShapedRecipe shaped) {
            width = shaped.getWidth();
            height = shaped.getHeight();
            if (width > 3 || height > 3 || ingredients.size() != width * height) {
                return null;
            }
            for (int row = 0; row < height; row++) {
                for (int column = 0; column < width; column++) {
                    cells.set(row * 3 + column, ingredients.get(row * width + column));
                }
            }
        } else {
            if (ingredients.isEmpty() || ingredients.size() > 9) {
                return null;
            }
            for (int i = 0; i < ingredients.size(); i++) {
                cells.set(i, ingredients.get(i));
            }
        }
        var slots = slots(cells);
        return slots == null ? null : new Entry(holder, "crafting", slots, output, width, height);
    }

    /** The stacks each ingredient accepts, one per item; null if an ingredient that is not empty accepts nothing. */
    private static @Nullable List<List<ItemStack>> slots(List<Ingredient> ingredients) {
        var slots = new ArrayList<List<ItemStack>>();
        for (var ingredient : ingredients) {
            if (ingredient.isEmpty()) {
                slots.add(List.of());
                continue;
            }
            var accepted = new LinkedHashMap<Item, ItemStack>();
            for (var stack : ingredient.getItems()) {
                if (!stack.isEmpty()) {
                    accepted.putIfAbsent(stack.getItem(), stack.copy());
                }
            }
            if (accepted.isEmpty()) {
                return null;
            }
            slots.add(List.copyOf(accepted.values()));
        }
        return slots;
    }

    // ---- records ----

    /** The {@code Recipe} record of a recipe; new lists each time, as a program may change them. */
    static RecordValue record(Entry entry) {
        var inputs = new ArrayList<Object>();
        for (var slot : entry.slots()) {
            var accepted = new ArrayList<Object>();
            for (var stack : slot) {
                accepted.add(DomainCalls.stack(AEItemKey.of(stack), stack.getCount()));
            }
            inputs.add(new ListValue(accepted));
        }
        var outputs = new ArrayList<Object>();
        outputs.add(DomainCalls.stack(AEItemKey.of(entry.output()), entry.output().getCount()));
        return record(entry.id(), entry.kind(), inputs, outputs, entry.width(), entry.height());
    }

    /**
     * A pattern read back: its recipe when AE2 wrote it from one this index holds; otherwise what it takes and makes,
     * input by input, as a processing pattern (no ID) or, for a crafting-family pattern of a recipe no program could
     * write, with that recipe's ID and kind.
     */
    RecordValue read(ItemStack pattern, IPatternDetails details) {
        ResourceLocation recipeId = null;
        String kind = "processing";
        var crafting = pattern.get(AEComponents.ENCODED_CRAFTING_PATTERN);
        var stonecutting = pattern.get(AEComponents.ENCODED_STONECUTTING_PATTERN);
        var smithing = pattern.get(AEComponents.ENCODED_SMITHING_TABLE_PATTERN);
        if (crafting != null) {
            recipeId = crafting.recipeId();
            kind = "crafting";
        } else if (stonecutting != null) {
            recipeId = stonecutting.recipeId();
            kind = "stonecutting";
        } else if (smithing != null) {
            recipeId = smithing.recipeId();
            kind = "smithing";
        }
        var known = recipeId == null ? null : byId.get(recipeId);
        if (known != null) {
            return record(known);
        }
        var inputs = new ArrayList<Object>();
        for (var input : details.getInputs()) {
            var accepted = new ArrayList<Object>();
            for (var stack : input.getPossibleInputs()) {
                accepted.add(DomainCalls.stack(stack.what(), stack.amount() * input.getMultiplier()));
            }
            inputs.add(new ListValue(accepted));
        }
        var outputs = new ArrayList<Object>();
        for (var stack : details.getOutputs()) {
            outputs.add(DomainCalls.stack(stack.what(), stack.amount()));
        }
        return record(recipeId == null ? null : recipeId.toString(), kind, inputs, outputs, 0, 0);
    }

    private static RecordValue record(@Nullable String id, String kind, List<Object> inputs, List<Object> outputs,
            int width, int height) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("id", id);
        fields.put("kind", kind);
        fields.put("inputs", new ListValue(inputs));
        fields.put("outputs", new ListValue(outputs));
        fields.put("width", (long) width);
        fields.put("height", (long) height);
        return new RecordValue("Recipe", fields, true);
    }

    /** An item's ID, as programs name it. */
    static String id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
