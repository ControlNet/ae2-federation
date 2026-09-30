package space.controlnet.ae2federation.client.policy;

import java.util.HashSet;
import java.util.List;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;

public record PolicyEditorSelection(List<NetworkId> members, int consumerIndex, int providerIndex,
        int capabilityIndex) {
    public PolicyEditorSelection {
        members = List.copyOf(members);
        if (members.size() < 2 || new HashSet<>(members).size() != members.size()) {
            throw new IllegalArgumentException("Policy editor requires at least two distinct Federation Domain members");
        }
        if (consumerIndex < 0 || consumerIndex >= members.size()
                || providerIndex < 0 || providerIndex >= members.size() || consumerIndex == providerIndex) {
            throw new IllegalArgumentException("Policy endpoint selection is out of bounds or self-referential");
        }
        if (capabilityIndex < 0 || capabilityIndex >= PolicyCapability.values().length) {
            throw new IllegalArgumentException("Policy capability selection is out of bounds");
        }
    }

    public static PolicyEditorSelection initial(List<NetworkId> members) {
        var ordered = members.stream().sorted((left, right) -> left.toString().compareTo(right.toString())).toList();
        return new PolicyEditorSelection(ordered, 0, 1, 0);
    }

    /** Cycles the consumer; with no other distinct member (two-member domain) the direction is swapped. */
    public PolicyEditorSelection nextConsumer() {
        var next = nextDistinct(consumerIndex, providerIndex);
        return next == consumerIndex ? swapped() : withConsumer(next);
    }

    /** Cycles the provider; with no other distinct member (two-member domain) the direction is swapped. */
    public PolicyEditorSelection nextProvider() {
        var next = nextDistinct(providerIndex, consumerIndex);
        return next == providerIndex ? swapped() : withProvider(next);
    }

    /** Selects a consumer; choosing the current provider swaps the direction instead of being rejected. */
    public PolicyEditorSelection withConsumer(int index) {
        if (index == providerIndex) {
            return swapped();
        }
        return new PolicyEditorSelection(members, index, providerIndex, capabilityIndex);
    }

    /** Selects a provider; choosing the current consumer swaps the direction instead of being rejected. */
    public PolicyEditorSelection withProvider(int index) {
        if (index == consumerIndex) {
            return swapped();
        }
        return new PolicyEditorSelection(members, consumerIndex, index, capabilityIndex);
    }

    public PolicyEditorSelection swapped() {
        return new PolicyEditorSelection(members, providerIndex, consumerIndex, capabilityIndex);
    }

    public PolicyEditorSelection nextCapability() {
        return new PolicyEditorSelection(members, consumerIndex, providerIndex,
                (capabilityIndex + 1) % PolicyCapability.values().length);
    }

    public PolicyKey key() {
        return new PolicyKey(members.get(consumerIndex), members.get(providerIndex),
                PolicyCapability.values()[capabilityIndex]);
    }

    private int nextDistinct(int current, int excluded) {
        var next = (current + 1) % members.size();
        return next == excluded ? (next + 1) % members.size() : next;
    }
}
