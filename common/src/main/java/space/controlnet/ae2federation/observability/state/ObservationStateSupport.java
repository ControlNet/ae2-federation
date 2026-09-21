package space.controlnet.ae2federation.observability.state;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.observability.ObservationLimits;
import space.controlnet.ae2federation.observability.id.ScopedObservationId;

final class ObservationStateSupport {
    private ObservationStateSupport() {
    }

    static void validate(FabricReference scope, ScopedObservationId id, String status) {
        Objects.requireNonNull(scope);
        Objects.requireNonNull(id);
        if (!scope.fabricId().equals(id.fabricId())) {
            throw new IllegalArgumentException("Observation state ID belongs to another Fabric");
        }
        ObservationLimits.boundedString(status, "Observation status");
    }

    static <T extends ObservationState> List<T> canonical(
            FabricReference scope, List<T> states, int maximum, String field) {
        Objects.requireNonNull(states);
        ObservationLimits.boundedCount(states.size(), maximum, field);
        states.forEach(state -> {
            if (!scope.equals(state.scope())) {
                throw new IllegalArgumentException(field + " contains state from another Fabric generation");
            }
        });
        var canonical = states.stream().sorted(Comparator.comparing(state -> state.id().value())).toList();
        for (var index = 1; index < canonical.size(); index++) {
            if (canonical.get(index - 1).id().equals(canonical.get(index).id())) {
                throw new IllegalArgumentException(field + " contains duplicate observation IDs");
            }
        }
        return canonical;
    }
}
