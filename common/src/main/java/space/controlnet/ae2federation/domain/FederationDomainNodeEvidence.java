package space.controlnet.ae2federation.domain;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * What one node publishes. {@code capacityFactor} is how many cables' capacity a cable node carries: 1 for a Federation
 * Cable, the bundle's multiple for a bundled cable, {@link #UNLIMITED} for one with no limit.
 */
public record FederationDomainNodeEvidence(FederationDomainNodeId nodeId, FederationDomainNodeKind kind,
        Map<String, FederationDomainPortEvidence> ports, int capacityFactor) {
    public static final int UNLIMITED = 0;

    public FederationDomainNodeEvidence {
        Objects.requireNonNull(nodeId);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(ports);
        if (capacityFactor < 0) throw new IllegalArgumentException("Negative capacity factor: " + capacityFactor);
        ports = Map.copyOf(new TreeMap<>(ports));
    }

    public FederationDomainNodeEvidence(FederationDomainNodeId nodeId, FederationDomainNodeKind kind,
            Map<String, FederationDomainPortEvidence> ports) {
        this(nodeId, kind, ports, 1);
    }

    /** A node of no particular kind. */
    public FederationDomainNodeEvidence(FederationDomainNodeId nodeId, Map<String, FederationDomainPortEvidence> ports) {
        this(nodeId, FederationDomainNodeKind.OTHER, ports);
    }

    public FederationDomainPortEvidence port(String name) {
        return ports.getOrDefault(name, FederationDomainPortEvidence.Disconnected.INSTANCE);
    }
}
