package space.controlnet.ae2federation.processing.claim;

public enum ClaimRejection {
    WRONG_ENDPOINT,
    STALE_EPOCH,
    OWNER_CONFLICT,
    /** A native AE2 Pattern Provider on the Endpoint's Federation face uses it in Local mode. */
    LOCAL_MODE
}
