package space.controlnet.ae2federation.identity;

import java.util.Objects;
import java.util.UUID;

public record NetworkId(UUID value) {
    public NetworkId {
        Objects.requireNonNull(value);
    }

    public static NetworkId create() {
        return new NetworkId(UUID.randomUUID());
    }

    public static NetworkId parse(String value) {
        return new NetworkId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "ae2federation:network:v1:" + value;
    }
}
