package space.controlnet.ae2federation.domain;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record FederationDomainNodeEvidence(FederationDomainNodeId nodeId, Map<String, FederationDomainPortEvidence> ports) {
    public FederationDomainNodeEvidence {
        Objects.requireNonNull(nodeId);
        Objects.requireNonNull(ports);
        ports = Map.copyOf(new TreeMap<>(ports));
    }

    public FederationDomainPortEvidence port(String name) {
        return ports.getOrDefault(name, FederationDomainPortEvidence.Disconnected.INSTANCE);
    }
}
