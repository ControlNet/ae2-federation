package space.controlnet.ae2federation.bridge;

public enum BridgeOperationalReason {
    VALID,
    MISSING_MAIN_ATTACHMENT,
    MISSING_OUTER_ATTACHMENT,
    SAME_GRID,
    FEDERATION_CABLE_UNSUPPORTED,
    REMOVED
}
