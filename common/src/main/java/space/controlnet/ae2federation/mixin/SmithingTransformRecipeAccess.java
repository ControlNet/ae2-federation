package space.controlnet.ae2federation.mixin;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The three ingredients of a smithing recipe, which Minecraft keeps package-private and lists nowhere else; programs
 * read them as the slots of a {@code Recipe}.
 */
@Mixin(SmithingTransformRecipe.class)
public interface SmithingTransformRecipeAccess {
    @Accessor("template")
    Ingredient ae2federation$template();

    @Accessor("base")
    Ingredient ae2federation$base();

    @Accessor("addition")
    Ingredient ae2federation$addition();
}
