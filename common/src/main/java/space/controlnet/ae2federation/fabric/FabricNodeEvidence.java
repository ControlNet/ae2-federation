package space.controlnet.ae2federation.fabric;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record FabricNodeEvidence(FabricNodeId nodeId, Map<String, FabricPortEvidence> ports) {
    public FabricNodeEvidence {
        Objects.requireNonNull(nodeId);
        Objects.requireNonNull(ports);
        ports = Map.copyOf(new TreeMap<>(ports));
    }

    public FabricPortEvidence port(String name) {
        return ports.getOrDefault(name, FabricPortEvidence.Disconnected.INSTANCE);
    }
}
