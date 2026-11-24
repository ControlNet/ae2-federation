package space.controlnet.ae2federation.compat.neoecoae;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import space.controlnet.ae2federation.crafting.projection.AddonProjections;
import space.controlnet.ae2federation.crafting.projection.ProjectedProvider;

/**
 * Neo ECO's batches through a projection. Neo ECO's CPU (and AE2's, with Neo ECO's mixin) pushes many crafts as one
 * batch only to a provider with one of its batch interfaces, which it recognises by type:
 * <ul>
 *   <li>{@code ECOFastPathDispatchProvider}, such as a crafting system's pattern bus: the provider prepares the batch,
 *   the CPU takes the inputs and hands the batch over;</li>
 *   <li>{@code ECOParallelCraftingProvider}, such as the Large Integrated Working Station's interface: the provider
 *   says how many crafts it takes at once, and the CPU pushes them as one.</li>
 * </ul>
 * A projection of such a provider has the same interface. The real provider prepares or takes each batch, and the
 * hand-over goes through {@link ProjectedProvider#pushBatch}, which owes the batch's outputs as a push owes a
 * pattern's.
 *
 * <p>This class only names Neo ECO's interfaces; {@link NeoEcoBatchingDirect}, which calls them, is loaded once a
 * provider with one of them was seen. A Neo ECO whose batch API differs from the one it was built against gets no batch
 * from a projection, as a provider without these interfaces would.
 *
 * <p>The real provider never gets the CPU's job id. With one, Neo ECO's machines return a batch only to that job's CPU
 * on their own network, where a consumer's CPU is not; without one, they store the outputs on their network, where the
 * return router takes them, as after an ordinary push. Neo ECO's exact batches need the job id, so a projection does
 * not offer them.
 */
public final class NeoEcoBatching implements AddonProjections {
    private static final String PACKAGE = "cn.dancingsnow.neoecoae.api.me.provider.";
    private static final List<String> BATCH_APIS =
            List.of(PACKAGE + "ECOFastPathDispatchProvider", PACKAGE + "ECOParallelCraftingProvider");

    /** Neo ECO's batch interfaces among {@code type}'s. */
    @Override
    public List<Class<?>> interfaces(Class<?> type) {
        var found = new ArrayList<Class<?>>();
        var pending = new ArrayList<Class<?>>();
        for (var current = type; current != null; current = current.getSuperclass()) {
            pending.addAll(List.of(current.getInterfaces()));
        }
        var seen = new HashSet<Class<?>>();
        while (!pending.isEmpty()) {
            var candidate = pending.removeLast();
            if (!seen.add(candidate)) continue;
            if (BATCH_APIS.contains(candidate.getName())) found.add(candidate);
            pending.addAll(List.of(candidate.getInterfaces()));
        }
        return found;
    }

    /** Answers a call on one of Neo ECO's batch interfaces for the projection behind {@code proxy}. */
    @Override
    public Object invoke(ProjectedProvider projection, Object proxy, Method method, Object[] arguments)
            throws Throwable {
        try {
            return NeoEcoBatchingDirect.invoke(projection, proxy, method, arguments);
        } catch (LinkageError otherApi) {
            // Another batch API than the one this was built against: no batch, so the CPU pushes crafts one by one.
            return switch (method.getName()) {
                case "eco$getAvailableParallelSlots" -> 0;
                case "eco$pushPatternBatch" -> false;
                default -> null;
            };
        }
    }
}
