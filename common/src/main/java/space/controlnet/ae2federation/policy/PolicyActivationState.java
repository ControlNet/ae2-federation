package space.controlnet.ae2federation.policy;

public enum PolicyActivationState {
    UNCONFIGURED,
    OFF,
    DISCONNECTED,
    /** The networks share only domains that are over cable capacity, which stop as a whole. */
    OVER_CAPACITY,
    BACKEND_UNREADY,
    ACTIVE
}
