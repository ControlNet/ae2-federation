package space.controlnet.ae2federation.p2p;

import java.util.List;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

/** Which tunnels of one frequency a tunnel is linked to, as if Federation cable ran between their fronts. */
final class TunnelLinks {
    private TunnelLinks() {
    }

    /**
     * The tunnels of {@code group} that {@code self} is linked to: every other active one, while {@code self} and the
     * frequency's input are active. Outputs reach one another only through a working input, as items do in AE2.
     */
    static <T> List<T> linked(T self, @Nullable T input, List<T> group, Predicate<T> active) {
        if (input == null || !active.test(self) || !active.test(input)) {
            return List.of();
        }
        return group.stream().filter(other -> other != self && active.test(other)).toList();
    }
}
