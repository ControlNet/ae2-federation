package space.controlnet.ae2federation.p2p;

import java.util.List;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

/**
 * Which tunnels of one frequency a tunnel is linked to: the input to each output, as if Federation cable ran between
 * their fronts. Outputs reach one another through the input, as items do in AE2, so they need no links of their own:
 * one frequency's tunnels end up in one Federation Domain with a link per output instead of one per pair.
 */
final class TunnelLinks {
    private TunnelLinks() {
    }

    /**
     * The tunnels of {@code group} that {@code self} is linked to, while {@code self} and the frequency's input are
     * active: for the input, every other active tunnel; for an output, the input.
     */
    static <T> List<T> linked(T self, @Nullable T input, List<T> group, Predicate<T> active) {
        if (input == null || !active.test(self) || !active.test(input)) {
            return List.of();
        }
        if (self != input) {
            return List.of(input);
        }
        return group.stream().filter(other -> other != self && active.test(other)).toList();
    }
}
