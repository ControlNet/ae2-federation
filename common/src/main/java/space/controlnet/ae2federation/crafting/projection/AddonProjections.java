package space.controlnet.ae2federation.crafting.projection;

import java.lang.reflect.Method;
import java.util.List;

/**
 * What a projection of an add-on's provider shows besides AE2's provider interface, so the add-on treats it as it treats
 * the real provider. An add-on's adapter recognises its classes by name, so it stays inert without the add-on.
 */
public interface AddonProjections {
    /**
     * Whether providers of {@code type} are logic that add-ons only read and push to through
     * {@link appeng.api.networking.crafting.ICraftingProvider#pushPattern}, which a projection shows through
     * {@link ProjectedProviderLogic}.
     */
    default boolean showsLogic(Class<?> type) {
        return false;
    }

    /**
     * The add-on's interfaces whose API this adapter knows that a projection of a provider of {@code type} has: some of
     * {@code type}'s own, or ones the add-on asks of a provider it treats as {@code type}'s; their methods go to
     * {@link #invoke}.
     */
    default List<Class<?>> interfaces(Class<?> type) {
        return List.of();
    }

    /** Answers a call of {@code method}, of one of {@link #interfaces}, on {@code proxy}, which shows {@code projection}. */
    default Object invoke(ProjectedProvider projection, Object proxy, Method method, Object[] arguments)
            throws Throwable {
        throw new UnsupportedOperationException(method.toString());
    }
}
