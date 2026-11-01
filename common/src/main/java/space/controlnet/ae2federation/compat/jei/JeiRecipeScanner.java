package space.controlnet.ae2federation.compat.jei;

import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
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
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiKeyMapping;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.automation.ViewerRecipes;
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
 * AE2 JEI Integration's converters when that mod is installed. A recipe is told apart from another of its category
 * by its ID, else by what it takes and makes.
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
    private static final List<String> CHANCE = List.of("chance", "probab", "%", "概率", "几率", "機率", "確率", "확률");

    private final IJeiRuntime runtime;

    JeiRecipeScanner(IJeiRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public Job start() {
        return new Scan(runtime);
    }

    private static final class Scan implements Job {
        private final IRecipeManager manager;
        private final IIngredientManager ingredients;
        private final IFocusGroup noFocus;
        private final Iterator<IRecipeCategory<?>> categories;
        private final List<ViewerRecipes.Category> found = new ArrayList<>();
        private final Set<String> seen = new HashSet<>();
        private @Nullable IRecipeCategory<?> category;
        private String categoryId = "";
        private Iterator<?> recipes = Collections.emptyIterator();

        Scan(IJeiRuntime runtime) {
            manager = runtime.getRecipeManager();
            ingredients = runtime.getIngredientManager();
            noFocus = runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
            categories = manager.createRecipeCategoryLookup().get().toList().iterator();
        }

        @Override
        public boolean step(long deadline, Consumer<ViewerRecipes.Recipe> out) {
            while (System.nanoTime() < deadline) {
                if (recipes.hasNext()) {
                    var recipe = read(category, recipes.next());
                    if (recipe != null && seen.add(key(recipe))) {
                        out.accept(recipe);
                    }
                    continue;
                }
                if (!categories.hasNext()) {
                    return true;
                }
                category = categories.next();
                var type = category.getRecipeType();
                var server = SERVER_TYPES.get(type.getUid());
                categoryId = server != null ? server : type.getUid().toString();
                var machines = new LinkedHashSet<String>();
                manager.createRecipeCatalystLookup(type).get(VanillaTypes.ITEM_STACK)
                        .forEach(stack -> machines.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
                if (categoryId.length() <= 256) {
                    found.add(new ViewerRecipes.Category(categoryId, machines.stream()
                            .limit(ViewerRecipes.MAX_MACHINES).toList()));
                }
                recipes = server != null || categoryId.length() > 256 ? Collections.emptyIterator()
                        : manager.createRecipeLookup(type).get().iterator();
            }
            return false;
        }

        @Override
        public List<ViewerRecipes.Category> categories() {
            return found;
        }

        @SuppressWarnings("unchecked")
        private <T> ViewerRecipes.@Nullable Recipe read(IRecipeCategory<T> category, Object object) {
            var recipe = (T) object;
            Optional<? extends mezz.jei.api.gui.IRecipeLayoutDrawable<T>> layout;
            try {
                layout = manager.createRecipeLayoutDrawable(category, recipe, noFocus);
            } catch (RuntimeException broken) {
                return null;
            }
            if (layout.isEmpty()) {
                return null;
            }
            var view = layout.get().getRecipeSlotsView();
            var inputs = new ArrayList<ViewerRecipes.Slot>();
            for (var slot : view.getSlotViews(RecipeIngredientRole.INPUT)) {
                if (slot.isEmpty()) {
                    continue;
                }
                var stacks = stacks(slot);
                if (stacks.isEmpty()) {
                    return null;
                }
                var tag = slot.getTagKey().filter(key -> key.registry().equals(Registries.ITEM))
                        .map(key -> key.location());
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
            var id = category.getRegistryName(recipe);
            if (inputs.isEmpty() || outputs.isEmpty() || inputs.size() > ViewerRecipes.MAX_SLOTS
                    || outputs.size() > ViewerRecipes.MAX_STACKS || byproducts.size() > ViewerRecipes.MAX_STACKS) {
                return null;
            }
            var named = id == null || id.toString().length() > 256 ? Optional.<String>empty()
                    : Optional.of(id.toString());
            return new ViewerRecipes.Recipe(categoryId, named, inputs, outputs, byproducts);
        }
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

    private static String key(ViewerRecipes.Recipe recipe) {
        return recipe.category() + "|" + recipe.id().orElseGet(() -> recipe.inputs() + ">" + recipe.outputs() + "+"
                + recipe.byproducts());
    }
}
