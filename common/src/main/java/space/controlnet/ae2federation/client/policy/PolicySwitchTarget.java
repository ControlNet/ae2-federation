package space.controlnet.ae2federation.client.policy;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyRevision;

/** One pair-editor switch: the complete directional key, its requested state and the revision the client saw. */
public record PolicySwitchTarget(PolicyKey key, boolean enabled, PolicyRevision observedRevision) {
    public PolicySwitchTarget {
        Objects.requireNonNull(key);
        Objects.requireNonNull(observedRevision);
        if (key.consumerNetworkId().equals(key.providerNetworkId())) {
            throw new IllegalArgumentException("A policy switch cannot target its own network");
        }
    }

    public String encode() {
        return key.consumerNetworkId().value() + "/" + key.providerNetworkId().value() + "/" + key.capability().name()
                + "/" + (enabled ? "1" : "0") + "/" + observedRevision.value();
    }

    public static Optional<PolicySwitchTarget> parse(String value) {
        var fields = value.split("/", -1);
        if (fields.length != 5 || !(fields[3].equals("1") || fields[3].equals("0"))) return Optional.empty();
        try {
            var key = new PolicyKey(new NetworkId(UUID.fromString(fields[0])), new NetworkId(UUID.fromString(fields[1])),
                    PolicyCapability.valueOf(fields[2]));
            return Optional.of(new PolicySwitchTarget(key, fields[3].equals("1"), new PolicyRevision(Long.parseLong(fields[4]))));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
