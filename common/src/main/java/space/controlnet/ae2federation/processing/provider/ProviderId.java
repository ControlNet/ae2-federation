package space.controlnet.ae2federation.processing.provider;

import java.util.Objects;
import java.util.UUID;

public record ProviderId(UUID value) {
    public ProviderId {
        Objects.requireNonNull(value);
    }

    public static ProviderId create() {
        return new ProviderId(UUID.randomUUID());
    }
}
