package space.controlnet.ae2federation.processing.provider;

public enum ProviderTargetState {
    ACTIVE,
    ENDPOINT_OFFLINE,
    ROTATION_PENDING,
    IDENTITY_UNSETTLED,
    CLAIM_MISMATCH,
    FEDERATION_DOMAIN_DISCONNECTED,
    /** The Provider's domain is over cable capacity and stopped as a whole. */
    FEDERATION_DOMAIN_OVER_CAPACITY,
    /** The Provider's domain is unpowered and stopped as a whole. */
    FEDERATION_DOMAIN_UNPOWERED,
    SAME_SOURCE_GRID,
    OVERLAPPING_SUBNET,
    NATIVE_TARGET_UNAVAILABLE
}
