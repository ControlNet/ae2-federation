package space.controlnet.ae2federation.domain;

/** What block or part publishes a node, so rules over the structure (such as cable capacity) can tell nodes apart. */
public enum FederationDomainNodeKind {
    CABLE,
    ROUTER,
    SWITCH,
    PATTERN_PROVIDER,
    ENDPOINT,
    P2P_TUNNEL,
    /** A node no structural rule treats specially, such as a test's synthetic node. */
    OTHER
}
