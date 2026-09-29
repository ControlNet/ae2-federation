package space.controlnet.ae2federation.policy;

import java.util.Optional;

/**
 * What one network may use of another under a rule. Processing is not a rule: a Provider may use every Endpoint in the
 * domain its network is a member of, since the Endpoint joins that domain through its Federation face.
 */
public enum PolicyCapability {
    STORAGE,
    CRAFTING,
    ME_POWER;

    /** The rule capability Processing was until Endpoints were authorized by their domain. */
    private static final String REMOVED_PROCESSING = "PROCESSING";

    /** A saved capability name; empty for the removed Processing capability, whose saved rules are dropped. */
    public static Optional<PolicyCapability> persisted(String name) {
        return name.equals(REMOVED_PROCESSING) ? Optional.empty() : Optional.of(valueOf(name));
    }
}
