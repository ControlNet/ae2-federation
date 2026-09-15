package space.controlnet.ae2federation.processing.provider;

public enum ProviderTargetState {
    ACTIVE,
    ENDPOINT_OFFLINE,
    ROTATION_PENDING,
    IDENTITY_UNSETTLED,
    CLAIM_MISMATCH,
    POLICY_DENIED,
    FABRIC_DISCONNECTED,
    SAME_SOURCE_GRID,
    OVERLAPPING_SUBNET,
    NATIVE_TARGET_UNAVAILABLE
}
