package space.controlnet.ae2federation.policy;

/** A historical backend observation, never authorization to use a capability. */
public record BindingDiagnostic(Reason reason, PolicyRevision policyRevision, long topologyRevision) {
    public enum Reason {
        IDENTITY_UNCONFIRMED,
        CRAFTING_PROVIDER_MISSING,
        CRAFTING_CPU_MISSING,
        ENERGY_SOURCE_MISSING,
        CRAFTING_CYCLE,
        POLICY_UNCONFIGURED,
        POLICY_DISABLED,
        NETWORK_PAIR_DISCONNECTED,
        BACKEND_UNREADY,
        DOMAIN_REFERENCE_MISSING,
        CONSUMER_ENERGY_INTERFACE_MISSING
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
