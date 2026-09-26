package space.controlnet.ae2federation.storage.provenance;

public enum ProvenanceDiagnostic {
    UNSETTLED_ORIGIN,
    CALLBACK_CHANGED,
    COMPLETE_AGGREGATE,
    OPAQUE_EXTERNAL_ALIAS,
    UNPROVEN_GRID_REBOUND,
    /** Several mounted AE2 delegating wrappers provably forward to one unmounted inner inventory. */
    AMBIGUOUS_SHARED_DELEGATE,
    /**
     * A mounted wrapper that is not provably transparent (for example AE2's filtering {@code MEInventoryHandler})
     * forwards to another mounted handle: one source identity, but two different execution behaviours.
     */
    NON_TRANSPARENT_ALIAS,
    /** The Grid's storage service is not AE2's StorageService, so its real mount table cannot be observed. */
    NATIVE_MOUNT_TABLE_UNAVAILABLE
}
