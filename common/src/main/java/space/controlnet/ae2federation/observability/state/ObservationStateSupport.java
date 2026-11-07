package space.controlnet.ae2federation.observability.state;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.observability.ObservationLimits;
import space.controlnet.ae2federation.observability.id.ScopedObservationId;

final class ObservationStateSupport {
    private ObservationStateSupport() {
    }

    static void validate(FederationDomainReference scope, ScopedObservationId id, String status) {
        Objects.requireNonNull(scope);
        Objects.requireNonNull(id);
        if (!scope.federationDomainId().equals(id.federationDomainId())) {
            throw new IllegalArgumentException("Observation state ID belongs to another Federation Domain");
        }
        ObservationLimits.boundedString(status, "Observation status");
    }

    static <T extends ObservationState> List<T> canonical(
            FederationDomainReference scope, List<T> states, int maximum, String field) {
        Objects.requireNonNull(states);
        ObservationLimits.boundedCount(states.size(), maximum, field);
        states.forEach(state -> {
            if (!scope.equals(state.scope())) {
                throw new IllegalArgumentException(field + " contains state from another Federation Domain generation");
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

    /**
     * The first {@code maximum} of {@code states} in canonical order, stopping early at the first one that no longer
     * fits in {@code budget}: a domain has no size limit, so a projection keeps a deterministic prefix of each list.
     */
    static <T extends ObservationState> List<T> bounded(List<T> states, int maximum, PayloadBudget budget) {
        var sorted = states.stream().sorted(Comparator.comparing(state -> state.id().value())).toList();
        var kept = new ArrayList<T>(Math.min(sorted.size(), maximum));
        for (var state : sorted) {
            if (kept.size() == maximum || !budget.take(encodedBytes(state))) {
                break;
            }
            kept.add(state);
        }
        return kept;
    }

    /**
     * An upper bound of the bytes one state takes in a snapshot payload; it mirrors the order the network codec writes
     * a state's fields in.
     */
    static int encodedBytes(ObservationState state) {
        var bytes = utf(state.id().federationDomainId().value()) + utf(state.id().value());
        return bytes + switch (state) {
            case MemberState member -> UUID_BYTES + utf(member.status());
            case ProviderState provider -> utf(provider.status());
            case EndpointState endpoint -> utf(endpoint.status());
            case PolicyState policy -> utf(policy.status());
            case LockState lock -> utf(lock.status());
            case TaskState task -> utf(task.status());
            // Event id, resource, amount (a var-long), unit and attribution (var-int ordinals), batch completion flag.
            case FlowState flow -> UUID_BYTES + utf(flow.resource()) + 10 + 5 + 5 + 1;
            default -> throw new IllegalArgumentException("Unknown observation state " + state.getClass());
        };
    }

    private static final int UUID_BYTES = 16;

    /** A length-prefixed UTF-8 string: the prefix is a var-int of at most three bytes for a bounded string. */
    private static int utf(String value) {
        return 3 + io.netty.buffer.ByteBufUtil.utf8Bytes(value);
    }

    /** What is left of one payload for its listed states. */
    static final class PayloadBudget {
        private long remaining;

        PayloadBudget(long bytes) {
            remaining = bytes;
        }

        boolean take(int bytes) {
            if (bytes > remaining) {
                return false;
            }
            remaining -= bytes;
            return true;
        }
    }
}
