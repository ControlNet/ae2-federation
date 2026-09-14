package space.controlnet.ae2federation.fabric;

import java.util.Objects;

public record FabricSourceId(String value) implements Comparable<FabricSourceId> {
    public FabricSourceId {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Fabric source identity must not be blank");
        }
    }

    public static FabricSourceId nativePort(FabricPortId port) {
        return new FabricSourceId("native:" + port.node() + ":" + port.port());
    }

    public FabricSourceId child(String role) {
        return new FabricSourceId(value + ":" + role);
    }

    @Override
    public int compareTo(FabricSourceId other) {
        return value.compareTo(other.value);
    }
}
