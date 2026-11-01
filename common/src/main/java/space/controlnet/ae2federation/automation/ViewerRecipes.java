package space.controlnet.ae2federation.automation;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.script.ListValue;
import space.controlnet.ae2federation.script.RecordValue;

/**
 * The processing recipes a player's recipe viewer (JEI) shows for categories the server cannot read itself, kept in the
 * world save: what {@code mc.recipes_for} lists beside {@link RecipeIndex}'s. A client scan replaces them whole
 * ({@link ViewerScans}); between scans, and with no player online, the saved ones stay.
 *
 * <p>Each category also names its machines: the items the viewer shows as its catalysts. A category the server reads
 * itself ({@code minecraft:smelting} and the other vanilla types) carries machines only, which add to the server's.
 *
 * <p>Assistant choices: a slot whose candidates are an item tag keeps the tag and one stack for its amount, and reads
 * as the tag's items in the server's current tags. A recipe a program could not name is not listed: one with a
 * resource whose ID does not read back as itself, such as an item with components. Recipes are listed by category,
 * then ID, then in scan order.
 */
public final class ViewerRecipes extends SavedData {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ViewerRecipes.class);
    private static final String NAME = "ae2federation_viewer_recipes";
    /** Per slot, page and scan: generous for real recipes, small enough that a page cannot ask for unbounded memory. */
    public static final int MAX_STACKS = 64;
    public static final int MAX_SLOTS = 81;
    public static final int MAX_MACHINES = 64;
    public static final int MAX_PAGE_RECIPES = 4096;
    public static final int MAX_PAGE_CATEGORIES = 4096;

    /** One input slot: the stacks it accepts, or an item tag with one stack giving the amount. */
    public record Slot(List<GenericStack> stacks, Optional<ResourceLocation> tag) {
        static final Codec<Slot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                GenericStack.CODEC.listOf().fieldOf("stacks").forGetter(Slot::stacks),
                ResourceLocation.CODEC.optionalFieldOf("tag").forGetter(Slot::tag)).apply(instance, Slot::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Slot> STREAM_CODEC = StreamCodec.composite(
                GenericStack.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_STACKS)), Slot::stacks,
                ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs::optional), Slot::tag, Slot::new);

        /** The stacks the slot accepts now: the tag's items in the server's tags, else the stacks it came with. */
        List<GenericStack> resolve() {
            if (tag.isEmpty() || stacks.isEmpty()) {
                return stacks;
            }
            var amount = stacks.get(0).amount();
            var items = BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, tag.get()));
            if (items.isEmpty() || items.get().size() == 0) {
                return stacks;
            }
            var out = new ArrayList<GenericStack>();
            items.get().stream().map(holder -> holder.value())
                    .sorted(Comparator.comparing((Item item) -> BuiltInRegistries.ITEM.getKey(item).toString()))
                    .forEach(item -> out.add(new GenericStack(AEItemKey.of(item), amount)));
            return out;
        }
    }

    /** A recipe the viewer shows: its category, its ID if the viewer gives one, inputs slot by slot, and outputs. */
    public record Recipe(String category, Optional<String> id, List<Slot> inputs, List<GenericStack> outputs,
            List<GenericStack> byproducts) {
        static final Codec<Recipe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("category").forGetter(Recipe::category),
                Codec.STRING.optionalFieldOf("id").forGetter(Recipe::id),
                Slot.CODEC.listOf().fieldOf("inputs").forGetter(Recipe::inputs),
                GenericStack.CODEC.listOf().fieldOf("outputs").forGetter(Recipe::outputs),
                GenericStack.CODEC.listOf().fieldOf("byproducts").forGetter(Recipe::byproducts))
                .apply(instance, Recipe::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Recipe> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(256), Recipe::category,
                ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs::optional), Recipe::id,
                Slot.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_SLOTS)), Recipe::inputs,
                GenericStack.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_STACKS)), Recipe::outputs,
                GenericStack.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_STACKS)), Recipe::byproducts,
                Recipe::new);
    }

    /** A viewer category: its ID (the server's recipe type for the vanilla ones) and the items of its machines. */
    public record Category(String id, List<String> machines) {
        static final Codec<Category> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Category::id),
                Codec.STRING.listOf().fieldOf("machines").forGetter(Category::machines))
                .apply(instance, Category::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Category> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(256), Category::id,
                ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(MAX_MACHINES)), Category::machines,
                Category::new);
    }

    private List<Category> categories = List.of();
    private List<Recipe> recipes = List.of();
    private Map<String, List<String>> machines = Map.of();
    private Map<Item, List<Recipe>> byOutput = Map.of();

    private ViewerRecipes() {
    }

    public static ViewerRecipes get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ViewerRecipes::new, ViewerRecipes::load, null), NAME);
    }

    /** Replaces everything with a scan's categories and recipes. */
    public void replace(List<Category> categories, List<Recipe> recipes) {
        this.categories = List.copyOf(categories);
        this.recipes = List.copyOf(recipes);
        index();
        setDirty();
    }

    public int size() {
        return recipes.size();
    }

    /** The items of the machines the viewer shows for a category or recipe type; empty if it shows none. */
    List<String> machines(@Nullable String category) {
        return category == null ? List.of() : machines.getOrDefault(category, List.of());
    }

    /** The recipes that make the item, as {@code Recipe} records; those a program could not name left out. */
    List<RecordValue> making(Item item, Function<String, Optional<AEKey>> named) {
        var out = new ArrayList<RecordValue>();
        for (var recipe : byOutput.getOrDefault(item, List.of())) {
            var record = record(recipe, named);
            if (record != null) {
                out.add(record);
            }
        }
        return out;
    }

    private @Nullable RecordValue record(Recipe recipe, Function<String, Optional<AEKey>> named) {
        var inputs = new ArrayList<Object>();
        for (var slot : recipe.inputs()) {
            var stacks = slot.resolve();
            if (stacks.stream().anyMatch(stack -> !RecipeIndex.named(stack, named))) {
                return null;
            }
            inputs.add(new ListValue(new ArrayList<>(stacks.stream()
                    .map(stack -> (Object) DomainCalls.stack(stack.what(), stack.amount())).toList())));
        }
        if (recipe.outputs().stream().anyMatch(stack -> !RecipeIndex.named(stack, named))
                || recipe.byproducts().stream().anyMatch(stack -> !RecipeIndex.named(stack, named))) {
            return null;
        }
        return RecipeIndex.record(recipe.id().orElse(null), "processing", recipe.category(),
                new ArrayList<>(machines(recipe.category())), inputs, stacks(recipe.outputs()),
                stacks(recipe.byproducts()), 0, 0);
    }

    private static List<Object> stacks(List<GenericStack> stacks) {
        return new ArrayList<>(stacks.stream().map(stack -> (Object) DomainCalls.stack(stack.what(), stack.amount()))
                .toList());
    }

    private void index() {
        var machines = new HashMap<String, List<String>>();
        for (var category : categories) {
            machines.computeIfAbsent(category.id(), id -> new ArrayList<>()).addAll(category.machines());
        }
        machines.replaceAll((id, items) -> List.copyOf(new LinkedHashSet<>(items)));
        this.machines = machines;
        var sorted = new ArrayList<>(recipes);
        sorted.sort(Comparator.comparing(Recipe::category).thenComparing(recipe -> recipe.id().orElse("")));
        var byOutput = new LinkedHashMap<Item, List<Recipe>>();
        for (var recipe : sorted) {
            var made = new LinkedHashSet<Item>();
            for (var output : recipe.outputs()) {
                if (output.what() instanceof AEItemKey key) {
                    made.add(key.getItem());
                }
            }
            made.forEach(item -> byOutput.computeIfAbsent(item, each -> new ArrayList<>()).add(recipe));
        }
        this.byOutput = byOutput;
    }

    // ---- the world save ----

    /** Recipe by recipe, so one whose item is gone (a mod removed) is dropped alone. */
    private static ViewerRecipes load(CompoundTag tag, HolderLookup.Provider registries) {
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        var data = new ViewerRecipes();
        var categories = new ArrayList<Category>();
        for (var each : tag.getList("categories", Tag.TAG_COMPOUND)) {
            Category.CODEC.parse(ops, each).resultOrPartial(error -> { }).ifPresent(categories::add);
        }
        var recipes = new ArrayList<Recipe>();
        int dropped = 0;
        for (var each : tag.getList("recipes", Tag.TAG_COMPOUND)) {
            var recipe = Recipe.CODEC.parse(ops, each).result();
            if (recipe.isPresent()) {
                recipes.add(recipe.get());
            } else {
                dropped++;
            }
        }
        if (dropped > 0) {
            LOGGER.info("Dropped {} saved viewer recipes that no longer load", dropped);
        }
        data.categories = List.copyOf(categories);
        data.recipes = List.copyOf(recipes);
        data.index();
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        var categories = new ListTag();
        for (var category : this.categories) {
            Category.CODEC.encodeStart(ops, category).result().ifPresent(categories::add);
        }
        var recipes = new ListTag();
        for (var recipe : this.recipes) {
            Recipe.CODEC.encodeStart(ops, recipe).result().ifPresent(recipes::add);
        }
        tag.put("categories", categories);
        tag.put("recipes", recipes);
        return tag;
    }
}
