package space.controlnet.ae2federation.domain;

public enum FederationDomainInvalidationReason {
    TOPOLOGY_CHANGED,
    SOURCE_UNLOADED,
    IDENTITY_UNSETTLED,
    NON_RECIPROCAL_EDGE,
    INVALID_BRIDGE
}
