package space.controlnet.ae2federation.automation;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
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
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.script.ListValue;
import space.controlnet.ae2federation.script.RecordValue;

/**
 * The processing recipes a player's recipe viewer (JEI) shows for categories the server cannot read itself, kept in the
 * world save: what {@code mc.recipes_for} lists beside {@link RecipeIndex}'s. Between scans, and with no player online,
 * the saved ones stay.
 *
 * <p>This file lists the categories, each with its machines: the items the viewer shows as its catalysts. A category
 * the server reads itself ({@code minecraft:smelting} and the other vanilla types) carries machines only, which add to
 * the server's. Each category's recipes are saved in a file of their own ({@link CategoryFile}), in the buckets a viewer
 * compares ({@link BucketedRecipes}), so a change to one bucket saves that category again and nothing else.
 *
 * <p>Assistant choices: a slot whose candidates are an item tag keeps the tag and one stack for its amount, and reads
 * as the tag's items in the server's current tags. A recipe a program could not name is not listed: one with a
 * resource whose ID does not read back as itself, such as an item with components. Recipes are listed by category,
 * then ID, then bucket by bucket. A removed category's file is saved empty, as the game cannot delete a saved file.
 */
public final class ViewerRecipes extends SavedData {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ViewerRecipes.class);
    private static final String NAME = "ae2federation_viewer_recipes";
    private static final String FILE_PREFIX = "ae2federation_vr_";
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
    private Map<String, List<String>> machines = Map.of();
    /** Each category's file, by category ID in order; found by {@link #get}, as loading this file has no server. */
    private volatile SortedMap<String, CategoryFile> files = Collections.emptySortedMap();
    private @Nullable DimensionDataStorage storage;

    private ViewerRecipes() {
    }

    public static ViewerRecipes get(MinecraftServer server) {
        var storage = server.overworld().getDataStorage();
        var recipes = storage.computeIfAbsent(new SavedData.Factory<>(ViewerRecipes::new, ViewerRecipes::load, null),
                NAME);
        if (recipes.storage != storage) {
            recipes.attach(storage);
        }
        return recipes;
    }

    /** The name of a category's file: a readable part of its ID and a hash of all of it, under 80 characters. */
    public static String fileName(String category) {
        var readable = category.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        return FILE_PREFIX + readable.substring(0, Math.min(readable.length(), 48)) + "_"
                + String.format(Locale.ROOT, "%06x", RecipeBuckets.hash(category) & 0xffffff);
    }

    private void attach(DimensionDataStorage storage) {
        this.storage = storage;
        var files = new TreeMap<String, CategoryFile>();
        for (var category : categories) {
            if (!files.containsKey(category.id())) {
                var file = storage.get(CategoryFile.factory(category.id()), fileName(category.id()));
                if (file != null) {
                    files.put(category.id(), file);
                }
            }
        }
        this.files = Collections.unmodifiableSortedMap(files);
    }

    /**
     * Replaces everything with a scan's categories and recipes, each recipe fingerprinted from its record: partitioned
     * by its ID, else by the items and amounts it takes and makes.
     */
    public void replace(List<Category> categories, List<Recipe> recipes) {
        var byCategory = new LinkedHashMap<String, List<BucketedRecipes.Entry<Recipe>>>();
        for (var recipe : recipes) {
            byCategory.computeIfAbsent(recipe.category(), id -> new ArrayList<>()).add(new BucketedRecipes.Entry<>(
                    fingerprint(recipe), BucketedRecipes.Status.RECIPE, recipe));
        }
        var listed = new ArrayList<>(categories);
        byCategory.keySet().stream().filter(id -> categories.stream().noneMatch(each -> each.id().equals(id)))
                .forEach(id -> listed.add(new Category(id, List.of())));
        for (var file : files.values()) {
            if (!byCategory.containsKey(file.id)) {
                file.set(BucketedRecipes.of(0, List.of()));
            }
        }
        var files = new TreeMap<String, CategoryFile>();
        byCategory.forEach((id, entries) -> {
            var file = file(id);
            file.set(BucketedRecipes.of(RecipeBuckets.bitsFor(entries.size()), entries));
            files.put(id, file);
        });
        this.files = Collections.unmodifiableSortedMap(files);
        list(listed);
    }

    /** A category with no recipes in buckets of those bits; one already saved starts over. */
    public void create(String category, int bits) {
        var file = file(category);
        file.set(BucketedRecipes.of(bits, List.of()));
        var files = new TreeMap<>(this.files);
        files.put(category, file);
        this.files = Collections.unmodifiableSortedMap(files);
        if (categories.stream().noneMatch(each -> each.id().equals(category))) {
            var listed = new ArrayList<>(categories);
            listed.add(new Category(category, List.of()));
            list(listed);
        }
    }

    /** A viewer's answer for one bucket of a category ({@link BucketedRecipes#apply}); saves that category alone. */
    public void apply(String category, int bucket, List<RecipeBuckets.Fingerprint> drop,
            List<BucketedRecipes.Entry<Recipe>> add) {
        var file = files.get(category);
        if (file == null) {
            throw new IllegalArgumentException("No saved viewer category " + category);
        }
        file.set(file.recipes().apply(bucket, drop, add));
    }

    /** A category's file, or null when none is saved. */
    public @Nullable CategoryFile category(String category) {
        return files.get(category);
    }

    /** The recipes read, in every category; skipped and failed entries not counted. */
    public int size() {
        return files.values().stream().mapToInt(file -> file.view.read().size()).sum();
    }

    /** The recipes read, category by category. */
    public List<Recipe> recipes() {
        var out = new ArrayList<Recipe>();
        files.values().forEach(file -> out.addAll(file.view.read()));
        return out;
    }

    /** The items of the machines the viewer shows for a category or recipe type; empty if it shows none. */
    List<String> machines(@Nullable String category) {
        return category == null ? List.of() : machines.getOrDefault(category, List.of());
    }

    /** The recipes that make the item, as {@code Recipe} records; those a program could not name left out. */
    List<RecordValue> making(Item item, Function<String, Optional<AEKey>> named) {
        var out = new ArrayList<RecordValue>();
        for (var file : files.values()) {
            for (var recipe : file.view.byOutput().getOrDefault(item, List.of())) {
                var record = record(recipe, named);
                if (record != null) {
                    out.add(record);
                }
            }
        }
        return out;
    }

    private CategoryFile file(String category) {
        var file = files.get(category);
        if (file == null) {
            file = new CategoryFile(category, BucketedRecipes.of(0, List.of()));
            Objects.requireNonNull(storage, "Viewer recipes not reached through get(server)")
                    .set(fileName(category), file);
        }
        return file;
    }

    private void list(List<Category> categories) {
        this.categories = List.copyOf(categories);
        index();
        setDirty();
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
    }

    private static RecipeBuckets.Fingerprint fingerprint(Recipe recipe) {
        var partition = recipe.id().map(id -> "id:" + id).orElseGet(() -> "in:" + amounts(recipe.inputs().stream()
                .map(slot -> slot.stacks().get(0)).toList()) + "\0out:" + amounts(recipe.outputs()) + "\0by:"
                + amounts(recipe.byproducts()));
        return new RecipeBuckets.Fingerprint(RecipeBuckets.hash(recipe.category() + "\0" + partition),
                RecipeBuckets.hash(recipe.toString()));
    }

    /** The stacks' resource IDs and amounts, sorted: what a recipe takes or makes, without components. */
    private static String amounts(List<GenericStack> stacks) {
        return String.join(",", stacks.stream().map(stack -> stack.what().getId() + "x" + stack.amount()).sorted()
                .toList());
    }

    // ---- the world save ----

    public static ViewerRecipes load(CompoundTag tag, HolderLookup.Provider registries) {
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        var data = new ViewerRecipes();
        var categories = new ArrayList<Category>();
        for (var each : tag.getList("categories", Tag.TAG_COMPOUND)) {
            Category.CODEC.parse(ops, each).resultOrPartial(error -> { }).ifPresent(categories::add);
        }
        data.categories = List.copyOf(categories);
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
        tag.put("categories", categories);
        return tag;
    }

    /**
     * One category's recipes in their buckets, saved in a file of its own, with what each recipe makes indexed for
     * {@code mc.recipes_for}. A program may read it while a viewer's answer is saved: each change swaps in a whole new
     * view.
     */
    public static final class CategoryFile extends SavedData {
        private final String id;
        private volatile View view;

        private record View(BucketedRecipes<Recipe> recipes, List<Recipe> read, Map<Item, List<Recipe>> byOutput) {
            static View of(BucketedRecipes<Recipe> recipes) {
                var read = recipes.records();
                var sorted = new ArrayList<>(read);
                sorted.sort(Comparator.comparing(recipe -> recipe.id().orElse("")));
                var byOutput = new HashMap<Item, List<Recipe>>();
                for (var recipe : sorted) {
                    var made = new LinkedHashSet<Item>();
                    for (var output : recipe.outputs()) {
                        if (output.what() instanceof AEItemKey key) {
                            made.add(key.getItem());
                        }
                    }
                    made.forEach(item -> byOutput.computeIfAbsent(item, each -> new ArrayList<>()).add(recipe));
                }
                return new View(recipes, read, byOutput);
            }
        }

        private CategoryFile(String id, BucketedRecipes<Recipe> recipes) {
            this.id = id;
            this.view = View.of(recipes);
        }

        static SavedData.Factory<CategoryFile> factory(String id) {
            return new SavedData.Factory<>(() -> new CategoryFile(id, BucketedRecipes.of(0, List.of())),
                    (tag, registries) -> load(id, tag, registries), null);
        }

        public String id() {
            return id;
        }

        public BucketedRecipes<Recipe> recipes() {
            return view.recipes();
        }

        void set(BucketedRecipes<Recipe> recipes) {
            view = View.of(recipes);
            setDirty();
        }

        /**
         * Entry by entry, so a recipe whose item is gone (a mod removed) is dropped alone. A registry reads an ID it no
         * longer has as its default (air for items), so a recipe counts as loaded only when it saves back as it was.
         */
        public static CategoryFile load(String id, CompoundTag tag, HolderLookup.Provider registries) {
            var ops = registries.createSerializationContext(NbtOps.INSTANCE);
            var entries = new ArrayList<BucketedRecipes.Entry<Recipe>>();
            int dropped = 0;
            for (var each : tag.getList("entries", Tag.TAG_COMPOUND)) {
                var entry = (CompoundTag) each;
                var fingerprint = new RecipeBuckets.Fingerprint(entry.getLong("p"), entry.getLong("d"));
                if (entry.contains("r", Tag.TAG_COMPOUND)) {
                    var saved = entry.getCompound("r");
                    var recipe = Recipe.CODEC.parse(ops, saved).result().filter(read -> Recipe.CODEC
                            .encodeStart(ops, read).result().filter(saved::equals).isPresent());
                    if (recipe.isPresent()) {
                        entries.add(new BucketedRecipes.Entry<>(fingerprint, BucketedRecipes.Status.RECIPE,
                                recipe.get()));
                    } else {
                        dropped++;
                    }
                } else {
                    var status = switch (entry.getString("s")) {
                        case "skipped" -> BucketedRecipes.Status.SKIPPED;
                        case "failed" -> BucketedRecipes.Status.FAILED;
                        default -> null;
                    };
                    if (status != null) {
                        entries.add(new BucketedRecipes.Entry<>(fingerprint, status, null));
                    } else {
                        dropped++;
                    }
                }
            }
            if (dropped > 0) {
                LOGGER.info("Dropped {} saved viewer recipes of {} that no longer load", dropped, id);
            }
            var bits = Math.max(0, Math.min(RecipeBuckets.MAX_BITS, tag.getInt("bits")));
            return new CategoryFile(id, BucketedRecipes.of(bits, entries));
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            var ops = registries.createSerializationContext(NbtOps.INSTANCE);
            var recipes = view.recipes();
            var entries = new ListTag();
            for (var each : recipes.entries()) {
                var entry = new CompoundTag();
                entry.putLong("p", each.fingerprint().partition());
                entry.putLong("d", each.fingerprint().digest());
                if (each.record() != null) {
                    var record = Recipe.CODEC.encodeStart(ops, each.record()).result();
                    if (record.isEmpty()) {
                        continue;
                    }
                    entry.put("r", record.get());
                } else {
                    entry.putString("s", each.status() == BucketedRecipes.Status.FAILED ? "failed" : "skipped");
                }
                entries.add(entry);
            }
            tag.putString("category", id);
            tag.putInt("bits", recipes.bits());
            tag.put("entries", entries);
            return tag;
        }
    }
}
