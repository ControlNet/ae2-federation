package space.controlnet.ae2federation.domain;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record FederationDomainNodeEvidence(FederationDomainNodeId nodeId, FederationDomainNodeKind kind,
        Map<String, FederationDomainPortEvidence> ports) {
    public FederationDomainNodeEvidence {
        Objects.requireNonNull(nodeId);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(ports);
        ports = Map.copyOf(new TreeMap<>(ports));
    }

    /** A node of no particular kind. */
    public FederationDomainNodeEvidence(FederationDomainNodeId nodeId, Map<String, FederationDomainPortEvidence> ports) {
        this(nodeId, FederationDomainNodeKind.OTHER, ports);
    }

    public FederationDomainPortEvidence port(String name) {
        return ports.getOrDefault(name, FederationDomainPortEvidence.Disconnected.INSTANCE);
    }
}
