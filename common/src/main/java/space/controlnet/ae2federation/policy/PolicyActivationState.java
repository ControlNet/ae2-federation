package space.controlnet.ae2federation.policy;

public enum PolicyActivationState {
    UNCONFIGURED,
    OFF,
    DISCONNECTED,
    /** The networks share only domains that are over cable capacity, which stop as a whole. */
    OVER_CAPACITY,
    /** The networks share only stopped domains, and none of them over capacity: their converters cannot pay. */
    UNPOWERED,
    BACKEND_UNREADY,
    ACTIVE
}
