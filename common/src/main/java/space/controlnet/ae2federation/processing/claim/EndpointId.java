package space.controlnet.ae2federation.processing.claim;

import java.util.Objects;
import java.util.UUID;

public record EndpointId(UUID value) {
    public EndpointId {
        Objects.requireNonNull(value);
    }

    public static EndpointId create() {
        return new EndpointId(UUID.randomUUID());
    }
}
