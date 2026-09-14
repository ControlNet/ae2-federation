package space.controlnet.ae2federation.fabric;

import java.util.Objects;

public record FabricId(String value) implements Comparable<FabricId> {
    public FabricId {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Fabric identity must not be blank");
        }
    }

    public static FabricId direct(FabricSourceId source) {
        return new FabricId("direct:" + source.value());
    }

    public static FabricId physical(FabricNodeId firstNode) {
        return new FabricId("physical:" + firstNode);
    }

    @Override
    public int compareTo(FabricId other) {
        return value.compareTo(other.value);
    }
}
