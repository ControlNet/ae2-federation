package space.controlnet.ae2federation.domain;

/** What block or part publishes a node, so rules over the structure (such as cable capacity) can tell nodes apart. */
public enum FederationDomainNodeKind {
    CABLE,
    ROUTER,
    SWITCH,
    PATTERN_PROVIDER,
    ENDPOINT,
    P2P_TUNNEL,
    /** A block of an ME Federation Beam Converter, which powers its domain. */
    CONVERTER,
    /** A block of an ME Federation Beam Former, which links its domain across a beam. */
    BEAM_FORMER,
    /** A block of an ME Federation Quantum Cube, which links its domain to the cube bound to it. */
    QUANTUM_CUBE,
    /** An ME Federation Automation Interface, a place in the world where programs act; a device, as an Endpoint. */
    AUTOMATION_INTERFACE,
    /** A computing device, which runs the functions installed on it; a device, as an Endpoint. */
    COMPUTING_DEVICE,
    /**
     * A communication interface of a Federation computation system: links a cable to its controller, which counts as
     * the device, so cable passes through it as through a Switch.
     */
    COMPUTING_INTERFACE,
    /** A node no structural rule treats specially, such as a test's synthetic node. */
    OTHER
}
