package space.controlnet.ae2federation.compat.ae2jeiintegration;

import appeng.api.stacks.GenericStack;
import java.util.Optional;
import mezz.jei.api.ingredients.ITypedIngredient;
import tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverters;

/**
 * JEI ingredients of other kinds than items and fluids as AE2 stacks, through the converters addons register with AE2
 * JEI Integration (Applied Mekanistics' for Mekanism chemicals). Reached only while that mod is installed.
 */
public final class Ae2JeiStacks {
    private Ae2JeiStacks() {
    }

    public static <T> Optional<GenericStack> stack(ITypedIngredient<T> typed) {
        try {
            var converter = IngredientConverters.getConverter(typed.getType());
            return converter == null ? Optional.empty()
                    : Optional.ofNullable(converter.getStackFromIngredient(typed.getIngredient()));
        } catch (RuntimeException broken) {
            return Optional.empty();
        }
    }
}
