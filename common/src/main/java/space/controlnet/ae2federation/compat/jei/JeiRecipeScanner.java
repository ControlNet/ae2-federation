package space.controlnet.ae2federation.compat.jei;

import appeng.api.stacks.GenericStack;
import com.mojang.serialization.DynamicOps;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import com.mojang.datafixers.util.Either;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.IIngredientSupplier;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiKeyMapping;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.automation.BucketedRecipes;
import space.controlnet.ae2federation.automation.RecipeBuckets;
import space.controlnet.ae2federation.automation.ViewerRecipes;
import space.controlnet.ae2federation.automation.ViewerScans;
import space.controlnet.ae2federation.client.automation.ViewerScanner;
import space.controlnet.ae2federation.compat.ae2jeiintegration.Ae2JeiStacks;

/**
 * JEI's recipes as {@link ViewerRecipes}: every category JEI shows, each with the items JEI shows as its catalysts, and
 * the recipes of the categories the server does not read itself, each read from the slots JEI lays it out with.
 *
 * <p>Assistant choices, from the AE All Pattern mod's JEI scan (MIT, read 2026-11-01), which reads slots the same
 * way: an input slot is read as all the stacks it shows, an item tag when JEI says the slot is one; a recipe with an
 * input slot of something AE2 cannot hold (energy, mana) is left out, while such an output is only left out of the
 * outputs. An output is a byproduct, never promised by a pattern, when its slot shows several stacks or its tooltip
 * reads as a chance ("chance", "probab", "%", "概率", "几率" ...): JEI has no field for chance outputs, so this reads
 * the text mods show, and may miss. Items and fluids convert themselves; other kinds (Mekanism chemicals) go through
 * AE2 JEI Integration's converters when that mod is installed. A slot's item tag is a tag whose items are the slot's, in order, as JEI
 * finds one, looked up in a table built once a scan: JEI's own lookup walks every item tag for each slot, which in a
 * pack of hundreds of mods made the scan a few dozen recipes a second. Of several such tags the smallest ID is taken,
 * so a scan of the same pack saves the same tags (JEI takes the first in a hash order that changes from run to run).
 *
 * <p>Owner decisions (2026-11-02): only real recipes are read, those JEI holds as a {@link RecipeHolder} or a bare
 * {@link Recipe}; what JEI or a mod's plugin builds to show something (anvil combinations, MineColonies workers, fuel
 * tables, information pages, brewing) is left out. So is a recipe with no inputs, no outputs, or outputs equal to its
 * inputs. A recipe keeps an ID only when the server has a recipe of that ID: a holder's own, or, for a bare recipe, the
 * ID of the holder the server sent with that very object (NeoForge 1.21.1 sends every recipe to the client). Holders
 * that a mod builds on the client carry IDs the server lacks; those recipes are told apart by content.
 *
 * <p>Before reading, each recipe is fingerprinted ({@link RecipeBuckets}) from what JEI says it takes and makes, without
 * laying it out (about a tenth of a read): partitioned by its server ID, else by its items and fluids with their
 * amounts but not their components, so a recipe stays in its bucket however its contents change; its digest is its
 * ID and its datapack encoding when its serializer gives one, else its ingredients with their components. Per recipe,
 * never per category, so two clients agree whatever order JEI lists them in.
 */
final class JeiRecipeScanner implements ViewerScanner {
    /** JEI's categories of the recipe types the server reads itself, by the server's recipe type. */
    private static final Map<ResourceLocation, String> SERVER_TYPES = Map.of(
            RecipeTypes.CRAFTING.getUid(), "minecraft:crafting",
            RecipeTypes.STONECUTTING.getUid(), "minecraft:stonecutting",
            RecipeTypes.SMITHING.getUid(), "minecraft:smithing",
            RecipeTypes.SMELTING.getUid(), "minecraft:smelting",
            RecipeTypes.BLASTING.getUid(), "minecraft:blasting",
            RecipeTypes.SMOKING.getUid(), "minecraft:smoking");
    /**
     * JEI categories left out entirely. Owner decision (2026-11-01): FTB Quests shows quest rewards, which no machine
     * makes, and rolls loot rewards anew each time the client loads.
     */
    private static final Set<ResourceLocation> SKIPPED = Set.of(ResourceLocation.fromNamespaceAndPath("ftbquests",
            "quest"));
    private static final List<String> CHANCE = List.of("chance", "probab", "%", "概率", "几率", "機率", "確率", "확률");

    private static final List<RecipeIngredientRole> CONTENT = List.of(RecipeIngredientRole.INPUT,
            RecipeIngredientRole.OUTPUT, RecipeIngredientRole.CATALYST);

    private final IRecipeManager manager;
    private final IIngredientManager ingredients;
    private final IFocusGroup noFocus;
    private @Nullable Map<List<Item>, ResourceLocation> tags;
    /** The server's recipes as the client received them: the IDs, and each recipe object's ID. */
    private @Nullable Set<ResourceLocation> serverIds;
    private final Map<Recipe<?>, ResourceLocation> serverIdOf = new IdentityHashMap<>();

    JeiRecipeScanner(IJeiRuntime runtime) {
        manager = runtime.getRecipeManager();
        ingredients = runtime.getIngredientManager();
        noFocus = runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
    }

    @Override
    public Set<String> categoryIds() {
        return categories().keySet();
    }

    /** JEI's categories by the ID the server knows them by, but those left out and those of too long an ID. */
    private Map<String, IRecipeCategory<?>> categories() {
        var out = new LinkedHashMap<String, IRecipeCategory<?>>();
        manager.createRecipeCategoryLookup().get().forEach(category -> {
            var uid = category.getRecipeType().getUid();
            var id = SERVER_TYPES.getOrDefault(uid, uid.toString());
            if (!SKIPPED.contains(uid) && id.length() <= 256) {
                out.putIfAbsent(id, category);
            }
        });
        return out;
    }

    @Override
    public Job<ViewerScans.Listed> list() {
        var categories = categories().entrySet().iterator();
        return (deadline, out) -> {
            while (System.nanoTime() < deadline) {
                if (!categories.hasNext()) {
                    return true;
                }
                var next = categories.next();
                var type = next.getValue().getRecipeType();
                var machines = new LinkedHashSet<String>();
                manager.createRecipeCatalystLookup(type).get(VanillaTypes.ITEM_STACK)
                        .forEach(stack -> machines.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
                var count = SERVER_TYPES.containsKey(type.getUid()) ? 0
                        : (int) manager.createRecipeLookup(type).get().filter(JeiRecipeScanner::real).count();
                out.accept(new ViewerScans.Listed(next.getKey(), count, machines.stream()
                        .limit(ViewerRecipes.MAX_MACHINES).toList()));
            }
            return false;
        };
    }

    @Override
    public Prints fingerprint(String categoryId) {
        var category = categories().get(categoryId);
        var recipes = category == null || SERVER_TYPES.containsKey(category.getRecipeType().getUid())
                ? Collections.emptyIterator() : manager.createRecipeLookup(category.getRecipeType()).get().iterator();
        return new Printing(categoryId, category, recipes);
    }

    /** What JEI holds as a recipe of the game: a holder, or a bare recipe; anything else a mod built to show. */
    private static boolean real(Object object) {
        return object instanceof RecipeHolder<?> || object instanceof Recipe<?>;
    }

    /** The server's ID of a recipe object: a holder's own when the server has it, else the bare recipe's. */
    private @Nullable ResourceLocation serverId(Object object) {
        if (serverIds == null) {
            serverIds = new HashSet<>();
            var connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                for (var holder : connection.getRecipeManager().getRecipes()) {
                    serverIds.add(holder.id());
                    serverIdOf.put(holder.value(), holder.id());
                }
            }
        }
        if (object instanceof RecipeHolder<?> holder) {
            return serverIds.contains(holder.id()) ? holder.id() : null;
        }
        return object instanceof Recipe<?> bare ? serverIdOf.get(bare) : null;
    }

    /** A category's real recipes with ingredients going in and coming out, fingerprinted; then read on request. */
    private final class Printing implements Prints {
        private final String categoryId;
        private final @Nullable IRecipeCategory<?> category;
        private final Iterator<?> recipes;
        private final List<Object> objects = new ArrayList<>();
        private final List<RecipeBuckets.Fingerprint> prints = new ArrayList<>();
        private final DynamicOps<Tag> ops;

        Printing(String categoryId, @Nullable IRecipeCategory<?> category, Iterator<?> recipes) {
            this.categoryId = categoryId;
            this.category = category;
            this.recipes = recipes;
            ops = Minecraft.getInstance().getConnection().registryAccess()
                    .createSerializationContext(NbtOps.INSTANCE);
        }

        @Override
        public boolean step(long deadline, Consumer<RecipeBuckets.Fingerprint> out) {
            while (System.nanoTime() < deadline) {
                if (!recipes.hasNext()) {
                    return true;
                }
                var object = recipes.next();
                if (!real(object)) {
                    continue;
                }
                var print = fingerprint(category, object);
                if (print != null) {
                    objects.add(object);
                    prints.add(print);
                    out.accept(print);
                }
            }
            return false;
        }

        /**
         * Partitioned by the server's ID, else by the items and amounts the recipe takes and makes without
         * components; changed when the recipe's own datapack encoding changes, else when its ingredients with their
         * components do. None for a recipe JEI cannot give ingredients for, or one that takes or makes nothing.
         */
        @SuppressWarnings("unchecked")
        private <T> RecipeBuckets.@Nullable Fingerprint fingerprint(IRecipeCategory<T> category, Object object) {
            IIngredientSupplier supplier;
            try {
                supplier = manager.getRecipeIngredients(category, (T) object);
            } catch (RuntimeException | LinkageError broken) {
                return null;
            }
            if (supplier.getIngredients(RecipeIngredientRole.INPUT).isEmpty()
                    || supplier.getIngredients(RecipeIngredientRole.OUTPUT).isEmpty()) {
                return null;
            }
            var id = serverId(object);
            var partition = id != null ? "id:" + id : text(supplier, List.of(RecipeIngredientRole.INPUT,
                    RecipeIngredientRole.OUTPUT), false);
            var encoded = encoded(object instanceof RecipeHolder<?> holder ? holder.value() : (Recipe<?>) object);
            long digest;
            if (encoded != null) {
                var named = ((id == null ? "" : id.toString()) + "\0").getBytes(StandardCharsets.UTF_8);
                var bytes = new byte[named.length + encoded.length];
                System.arraycopy(named, 0, bytes, 0, named.length);
                System.arraycopy(encoded, 0, bytes, named.length, encoded.length);
                digest = RecipeBuckets.hash(bytes);
            } else {
                digest = RecipeBuckets.hash(text(supplier, CONTENT, true));
            }
            return new RecipeBuckets.Fingerprint(RecipeBuckets.hash(categoryId + "\0" + partition), digest);
        }

        /**
         * The recipe as a datapack writes it, every registry entry by name; null when its serializer cannot write it.
         * Not the network encoding: that writes registry entries by number, and a pack that drops a mod renumbers
         * most of them (ATM10, 2026-11-02: 73,070 recipes with unchanged ingredients changed their bytes).
         */
        private byte @Nullable [] encoded(Recipe<?> recipe) {
            try {
                return Recipe.CODEC.encodeStart(ops, recipe).result().map(tag -> tag.toString()
                        .getBytes(StandardCharsets.UTF_8)).orElse(null);
            } catch (RuntimeException | LinkageError broken) {
                return null;
            }
        }

        /** Each role's ingredients as text, sorted, with their components or without. */
        private String text(IIngredientSupplier supplier, List<RecipeIngredientRole> roles, boolean components) {
            var text = new StringBuilder();
            for (var role : roles) {
                var parts = new ArrayList<String>();
                for (var typed : supplier.getIngredients(role)) {
                    parts.add(text(typed, components));
                }
                parts.sort(null);
                text.append(role.name()).append('[').append(String.join(";", parts)).append(']');
            }
            return text.toString();
        }

        /** JEI 19.64 marks {@code getUniqueId} for removal; {@code getUid} gives an object with no stable text. */
        @SuppressWarnings("removal")
        private <V> String text(ITypedIngredient<V> typed, boolean components) {
            if (typed.getIngredient() instanceof ItemStack stack) {
                return "i:" + BuiltInRegistries.ITEM.getKey(stack.getItem())
                        + (components ? patch(stack.getComponentsPatch()) : "") + "x" + stack.getCount();
            }
            if (typed.getIngredient() instanceof FluidStack fluid) {
                return "f:" + BuiltInRegistries.FLUID.getKey(fluid.getFluid())
                        + (components ? patch(fluid.getComponentsPatch()) : "") + "x" + fluid.getAmount();
            }
            try {
                var helper = ingredients.getIngredientHelper(typed.getType());
                return typed.getType().getUid() + ":" + helper.getUniqueId(typed.getIngredient(), UidContext.Recipe)
                        + "x" + helper.getAmount(typed.getIngredient());
            } catch (RuntimeException | LinkageError broken) {
                return typed.getType().getUid() + ":{!}";
            }
        }

        /** A component patch as text; {@code Tag.toString} sorts compound keys, so map order does not show. */
        private String patch(DataComponentPatch patch) {
            if (patch.isEmpty()) {
                return "";
            }
            var result = DataComponentPatch.CODEC.encodeStart(ops, patch);
            return result.result().map(Tag::toString).orElse("{!}");
        }

        @Override
        public Job<BucketedRecipes.Entry<ViewerRecipes.Recipe>> read(List<RecipeBuckets.Fingerprint> wanted) {
            var behind = new HashMap<RecipeBuckets.Fingerprint, ArrayDeque<Object>>();
            for (var index = 0; index < prints.size(); index++) {
                behind.computeIfAbsent(prints.get(index), each -> new ArrayDeque<>()).add(objects.get(index));
            }
            var next = wanted.iterator();
            return (deadline, out) -> {
                while (System.nanoTime() < deadline) {
                    if (!next.hasNext()) {
                        return true;
                    }
                    var print = next.next();
                    var queue = behind.get(print);
                    var object = queue == null ? null : queue.poll();
                    out.accept(object == null || category == null
                            ? new BucketedRecipes.Entry<>(print, BucketedRecipes.Status.FAILED, null, null)
                            : entry(category, categoryId, print, object));
                }
                return false;
            };
        }
    }

    /** The slots JEI lays a recipe out with, read as a record; skipped when it reads as no processing recipe. */
    @SuppressWarnings("unchecked")
    private <T> BucketedRecipes.Entry<ViewerRecipes.Recipe> entry(IRecipeCategory<T> category, String categoryId,
            RecipeBuckets.Fingerprint print, Object object) {
        Optional<? extends mezz.jei.api.gui.IRecipeLayoutDrawable<T>> layout;
        try {
            layout = manager.createRecipeLayoutDrawable(category, (T) object, noFocus);
        } catch (RuntimeException | LinkageError broken) {
            layout = Optional.empty();
        }
        var id = serverId(object);
        var named = id == null || id.toString().length() > 256 ? null : id.toString();
        if (layout.isEmpty()) {
            return new BucketedRecipes.Entry<>(print, BucketedRecipes.Status.FAILED, null, named);
        }
        var recipe = read(layout.get().getRecipeSlotsView(), categoryId, id);
        return recipe == null ? new BucketedRecipes.Entry<>(print, BucketedRecipes.Status.SKIPPED, null, named)
                : new BucketedRecipes.Entry<>(print, BucketedRecipes.Status.RECIPE, recipe);
    }

    /** The item tag whose items are the slot's, in order; none when the slot holds anything but items. */
    private Optional<ResourceLocation> tag(IRecipeSlotView slot) {
        if (tags == null) {
            tags = itemTags();
        }
        var items = new ArrayList<Item>();
        for (var typed : slot.getAllIngredients().toList()) {
            if (!(typed.getIngredient() instanceof ItemStack stack)) {
                return Optional.empty();
            }
            items.add(stack.getItem());
        }
        return Optional.ofNullable(tags.get(items));
    }

    private ViewerRecipes.@Nullable Recipe read(IRecipeSlotsView view, String categoryId,
            @Nullable ResourceLocation id) {
        var inputs = new ArrayList<ViewerRecipes.Slot>();
        for (var slot : view.getSlotViews(RecipeIngredientRole.INPUT)) {
            if (slot.isEmpty()) {
                continue;
            }
            var stacks = stacks(slot);
            if (stacks.isEmpty()) {
                return null;
            }
            var tag = tag(slot);
            inputs.add(new ViewerRecipes.Slot(tag.isPresent() ? stacks.subList(0, 1)
                    : stacks.subList(0, Math.min(stacks.size(), ViewerRecipes.MAX_STACKS)), tag));
        }
        var outputs = new ArrayList<GenericStack>();
        var byproducts = new ArrayList<GenericStack>();
        for (var slot : view.getSlotViews(RecipeIngredientRole.OUTPUT)) {
            var stacks = stacks(slot);
            if (stacks.isEmpty()) {
                continue;
            }
            (stacks.size() > 1 || chance(slot, ingredients) ? byproducts : outputs).add(stacks.get(0));
        }
        if (inputs.isEmpty() || outputs.isEmpty() || inputs.size() > ViewerRecipes.MAX_SLOTS
                || outputs.size() > ViewerRecipes.MAX_STACKS || byproducts.size() > ViewerRecipes.MAX_STACKS
                || makesWhatItTakes(inputs, outputs, byproducts)) {
            return null;
        }
        var named = id == null || id.toString().length() > 256 ? Optional.<String>empty()
                : Optional.of(id.toString());
        return new ViewerRecipes.Recipe(categoryId, named, inputs, outputs, byproducts);
    }

    /** Whether the outputs and byproducts are the inputs' first stacks, counted as a multiset. */
    private static boolean makesWhatItTakes(List<ViewerRecipes.Slot> inputs, List<GenericStack> outputs,
            List<GenericStack> byproducts) {
        var taken = new HashMap<GenericStack, Integer>();
        inputs.forEach(slot -> taken.merge(slot.stacks().get(0), 1, Integer::sum));
        var made = new HashMap<GenericStack, Integer>();
        outputs.forEach(stack -> made.merge(stack, 1, Integer::sum));
        byproducts.forEach(stack -> made.merge(stack, 1, Integer::sum));
        return taken.equals(made);
    }

    /** Each item tag by its items in order; of tags with the same items, the smallest ID. */
    private static Map<List<Item>, ResourceLocation> itemTags() {
        var out = new HashMap<List<Item>, ResourceLocation>();
        BuiltInRegistries.ITEM.getTags().forEach(pair -> out.merge(pair.getSecond().stream().map(Holder::value)
                .toList(), pair.getFirst().location(), (kept, other) -> kept.compareTo(other) <= 0 ? kept : other));
        return out;
    }

    /** The slot's stacks AE2 can hold, each once, in the slot's order. */
    private static List<GenericStack> stacks(IRecipeSlotView slot) {
        var out = new LinkedHashMap<appeng.api.stacks.AEKey, GenericStack>();
        slot.getAllIngredients().forEach(typed -> stack(typed)
                .filter(stack -> stack.amount() > 0)
                .ifPresent(stack -> out.putIfAbsent(stack.what(), stack)));
        return new ArrayList<>(out.values());
    }

    private static Optional<GenericStack> stack(ITypedIngredient<?> typed) {
        var ingredient = typed.getIngredient();
        if (ingredient instanceof ItemStack item) {
            return item.isEmpty() ? Optional.empty() : Optional.ofNullable(GenericStack.fromItemStack(item));
        }
        if (ingredient instanceof FluidStack fluid) {
            return fluid.isEmpty() ? Optional.empty() : Optional.ofNullable(GenericStack.fromFluidStack(fluid));
        }
        return ModList.get().isLoaded("ae2jeiintegration") ? Ae2JeiStacks.stack(typed) : Optional.empty();
    }

    /**
     * Whether the slot's name, or a line its tooltip adds to the stack's own, reads as a chance. JEI 19.64 marks the
     * slot's tooltip for removal and offers no other way to read it; once it is gone, only slot names count.
     */
    @SuppressWarnings("removal")
    private static boolean chance(IRecipeSlotView slot, IIngredientManager ingredients) {
        if (slot.getSlotName().map(JeiRecipeScanner::chance).orElse(false)) {
            return true;
        }
        if (!(slot instanceof IRecipeSlotDrawable drawable)) {
            return false;
        }
        try {
            var lines = new Lines();
            drawable.getTooltip(lines);
            if (lines.text.stream().noneMatch(JeiRecipeScanner::chance)) {
                return false;
            }
            var own = new Lines();
            slot.getDisplayedIngredient().ifPresent(typed -> ownTooltip(own, typed, ingredients));
            var added = new ArrayList<>(lines.text);
            added.removeAll(own.text);
            return added.stream().anyMatch(JeiRecipeScanner::chance);
        } catch (RuntimeException | LinkageError broken) {
            return false;
        }
    }

    private static <T> void ownTooltip(Lines out, ITypedIngredient<T> typed, IIngredientManager ingredients) {
        var minecraft = Minecraft.getInstance();
        ingredients.getIngredientRenderer(typed.getType()).getTooltip(out, typed.getIngredient(),
                Item.TooltipContext.of(minecraft.level), minecraft.player,
                minecraft.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL);
    }

    /** A tooltip's text lines, without drawing it. */
    @SuppressWarnings("removal")
    private static final class Lines implements ITooltipBuilder {
        private final List<String> text = new ArrayList<>();

        @Override
        public void add(FormattedText line) {
            text.add(line.getString());
        }

        @Override
        public void addAll(Collection<? extends FormattedText> lines) {
            lines.forEach(this::add);
        }

        @Override
        public void add(TooltipComponent component) {
        }

        @Override
        public void addKeyUsageComponent(String key, IJeiKeyMapping mapping) {
        }

        @Override
        public void setIngredient(ITypedIngredient<?> typed) {
        }

        @Override
        public void clearIngredient() {
        }

        @Override
        public List<Either<FormattedText, TooltipComponent>> getLines() {
            return text.stream().<Either<FormattedText, TooltipComponent>>map(line -> Either.left(
                    FormattedText.of(line))).toList();
        }

        @Override
        public List<Component> toLegacyToComponents() {
            return text.stream().<Component>map(Component::literal).toList();
        }

        @Override
        public void removeAll(List<Component> lines) {
            lines.forEach(line -> text.remove(line.getString()));
        }
    }

    private static boolean chance(String text) {
        var lower = text.toLowerCase(Locale.ROOT);
        return CHANCE.stream().anyMatch(lower::contains);
    }
}
