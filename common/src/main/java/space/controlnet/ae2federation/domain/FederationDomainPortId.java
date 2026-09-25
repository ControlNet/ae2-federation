package space.controlnet.ae2federation.domain;

import java.util.Objects;

public record FederationDomainPortId(FederationDomainNodeId node, String port) implements Comparable<FederationDomainPortId> {
    public FederationDomainPortId {
        Objects.requireNonNull(node);
        Objects.requireNonNull(port);
        if (port.isBlank()) {
            throw new IllegalArgumentException("Federation Domain port name must not be blank");
        }
    }

    @Override
    public int compareTo(FederationDomainPortId other) {
        var nodeOrder = node.compareTo(other.node);
        return nodeOrder != 0 ? nodeOrder : port.compareTo(other.port);
    }
}
