package space.controlnet.ae2federation.fabric;

import java.util.Objects;

public record FabricPortId(FabricNodeId node, String port) implements Comparable<FabricPortId> {
    public FabricPortId {
        Objects.requireNonNull(node);
        Objects.requireNonNull(port);
        if (port.isBlank()) {
            throw new IllegalArgumentException("Fabric port name must not be blank");
        }
    }

    @Override
    public int compareTo(FabricPortId other) {
        var nodeOrder = node.compareTo(other.node);
        return nodeOrder != 0 ? nodeOrder : port.compareTo(other.port);
    }
}
