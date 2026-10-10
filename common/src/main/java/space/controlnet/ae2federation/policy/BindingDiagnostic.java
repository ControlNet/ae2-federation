package space.controlnet.ae2federation.policy;

/** A historical backend observation, never authorization to use a capability. */
public record BindingDiagnostic(Reason reason, PolicyRevision policyRevision, long topologyRevision) {
    public enum Reason {
        IDENTITY_UNCONFIRMED,
        /** A crafting rule without the REQUEST operation. */
        CRAFTING_REQUEST_MISSING,
        POLICY_UNCONFIGURED,
        POLICY_DISABLED,
        NETWORK_PAIR_DISCONNECTED,
        BACKEND_UNREADY,
        DOMAIN_REFERENCE_MISSING,
        ENERGY_CONNECTION_MISSING,
        /** A storage rule whose operations and filters allow nothing. */
        STORAGE_ACCESS_NONE,
        /** The provider network has no storage it can share. */
        STORAGE_SOURCE_EMPTY,
        /** The level's storage relationships exceeded the dependency compiler's budget. */
        STORAGE_COMPILE_BUDGET
    }

    public static Reason inactiveReason(PolicyActivationState state) {
        return switch (state) {
            case UNCONFIGURED -> Reason.POLICY_UNCONFIGURED;
            case OFF -> Reason.POLICY_DISABLED;
            case DISCONNECTED -> Reason.NETWORK_PAIR_DISCONNECTED;
            case BACKEND_UNREADY -> Reason.BACKEND_UNREADY;
            case ACTIVE -> throw new IllegalArgumentException("Active relationships have no inactive reason");
        };
    }

    public boolean matches(PolicyRevision revision, long topology) {
        return policyRevision.equals(revision) && topologyRevision == topology;
    }
}
