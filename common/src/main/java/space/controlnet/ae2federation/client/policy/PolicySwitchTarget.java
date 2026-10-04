package space.controlnet.ae2federation.client.policy;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.policy.RuleMode;

/** One pair-editor switch: the complete directional key, its requested mode and the revision the client saw. */
public record PolicySwitchTarget(PolicyKey key, RuleMode mode, PolicyRevision observedRevision) {
    public PolicySwitchTarget {
        Objects.requireNonNull(key);
        Objects.requireNonNull(mode);
        Objects.requireNonNull(observedRevision);
        if (key.consumerNetworkId().equals(key.providerNetworkId())) {
            throw new IllegalArgumentException("A policy switch cannot target its own network");
        }
        if (!mode.allowedFor(key.capability())) {
            throw new IllegalArgumentException(key.capability() + " has no " + mode + " mode");
        }
    }

    public PolicySwitchTarget(PolicyKey key, boolean enabled, PolicyRevision observedRevision) {
        this(key, enabled ? RuleMode.ENABLED : RuleMode.DISABLED, observedRevision);
    }

    public boolean enabled() {
        return mode.enabled();
    }

    public String encode() {
        return key.consumerNetworkId().value() + "/" + key.providerNetworkId().value() + "/" + key.capability().name()
                + "/" + mode.ordinal() + "/" + observedRevision.value();
    }

    public static Optional<PolicySwitchTarget> parse(String value) {
        var fields = value.split("/", -1);
        if (fields.length != 5 || !fields[3].matches("[0-2]")) return Optional.empty();
        try {
            var key = new PolicyKey(new NetworkId(UUID.fromString(fields[0])), new NetworkId(UUID.fromString(fields[1])),
                    PolicyCapability.valueOf(fields[2]));
            return Optional.of(new PolicySwitchTarget(key, RuleMode.values()[Integer.parseInt(fields[3])],
                    new PolicyRevision(Long.parseLong(fields[4]))));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
