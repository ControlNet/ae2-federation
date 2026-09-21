package space.controlnet.ae2federation.observability.meter;

import java.util.Objects;
import java.util.UUID;

public record OperationEventId(UUID value) {
    public OperationEventId {
        Objects.requireNonNull(value);
    }

    public static OperationEventId create() {
        return new OperationEventId(UUID.randomUUID());
    }
}
