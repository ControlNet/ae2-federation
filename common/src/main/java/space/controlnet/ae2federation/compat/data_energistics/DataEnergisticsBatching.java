package space.controlnet.ae2federation.compat.data_energistics;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import space.controlnet.ae2federation.crafting.projection.AddonProjections;
import space.controlnet.ae2federation.crafting.projection.ProjectedProvider;

/**
 * Data Energistics' counted batches through a projection. The Trinity CPU pushes many crafts to a provider as one batch
 * only when Data Energistics knows the provider as counted; any other provider gets one craft per push, one push a
 * tick. A projection of a provider that is Data Energistics' public {@code CountedCraftingProviderAdapter}, such as an
 * AE2 Pattern Provider with Data Energistics' mixin, has that interface too, and the two public interfaces through which
 * Data Energistics names a provider to its provider plugins. When the projection mounts on the consumer's crafting
 * service, Data Energistics binds it to this mod's plugin ({@link DataEnergisticsCountedProjections}), which makes it
 * counted: the real provider admits each batch, and the hand-over goes through {@link ProjectedProvider#pushBatch}.
 *
 * <p>This class only names Data Energistics' types, so it stays inert without Data Energistics. A real provider that
 * Data Energistics counts only through another plugin, not through the public interface, stays one craft per push.
 */
public final class DataEnergisticsBatching implements AddonProjections {
    private static final String API = "com.fish_dan_.data_energistics.api.";
    private static final String COUNTED = API + "crafting.dispatch.CountedCraftingProviderAdapter";
    private static final List<String> NAMING = List.of(API + "registry.provider.runtime.PatternProviderIdentitySource",
            API + "registry.provider.runtime.PatternProviderRuntimeLink");

    /** The interfaces a projection of a provider of a type has: none, or the counted one and the naming ones. */
    private static final ClassValue<List<Class<?>>> INTERFACES = new ClassValue<>() {
        @Override
        protected List<Class<?>> computeValue(Class<?> type) {
            var counted = find(type, COUNTED);
            if (counted == null) return List.of();
            try {
                var interfaces = new ArrayList<Class<?>>();
                interfaces.add(counted);
                for (var name : NAMING) interfaces.add(Class.forName(name, false, counted.getClassLoader()));
                return List.copyOf(interfaces);
            } catch (ClassNotFoundException | LinkageError unknownApi) {
                return List.of();
            }
        }
    };

    @Override
    public List<Class<?>> interfaces(Class<?> type) {
        return INTERFACES.get(type);
    }

    @Override
    public Object invoke(ProjectedProvider projection, Object proxy, Method method, Object[] arguments)
            throws Throwable {
        return DataEnergisticsCountedProjections.invoke(projection, proxy, method, arguments);
    }

    /** The interface named {@code name} among {@code type}'s; null without one. */
    private static Class<?> find(Class<?> type, String name) {
        var pending = new ArrayList<Class<?>>();
        for (var current = type; current != null; current = current.getSuperclass()) {
            pending.addAll(List.of(current.getInterfaces()));
        }
        var seen = new HashSet<Class<?>>();
        while (!pending.isEmpty()) {
            var candidate = pending.removeLast();
            if (!seen.add(candidate)) continue;
            if (candidate.getName().equals(name)) return candidate;
            pending.addAll(List.of(candidate.getInterfaces()));
        }
        return null;
    }
}
