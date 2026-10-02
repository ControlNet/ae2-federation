package space.controlnet.ae2federation.policy;

/** A historical backend observation, never authorization to use a capability. */
public record BindingDiagnostic(Reason reason, PolicyRevision policyRevision, long topologyRevision) {
    public enum Reason {
        IDENTITY_UNCONFIRMED,
        CRAFTING_PROVIDER_MISSING,
        CRAFTING_CPU_MISSING,
        CRAFTING_CYCLE,
        /** A crafting rule without the REQUEST operation. */
        CRAFTING_REQUEST_MISSING,
        /** A crafting rule whose direction's storage rule is off: the CPU could not take the materials. */
        CRAFTING_STORAGE_REQUIRED,
        POLICY_UNCONFIGURED,
        POLICY_DISABLED,
        NETWORK_PAIR_DISCONNECTED,
        BACKEND_UNREADY,
        DOMAIN_REFERENCE_MISSING,
        ENERGY_CONNECTION_MISSING
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
