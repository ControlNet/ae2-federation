package space.controlnet.ae2federation.client.policy;

import java.util.List;
import java.util.Locale;
import space.controlnet.ae2federation.identity.IdentitySettlement;
import space.controlnet.ae2federation.identity.IdentityStatus;
import space.controlnet.ae2federation.identity.NetworkId;

/** How a domain member's identity looks to players, from the settlements of every live Grid claiming it. */
public enum NetworkIdentityState {
    SETTLED,
    MERGE,
    SPLIT,
    LOADING,
    COPIED,
    UNLOADED;

    public static NetworkIdentityState of(NetworkId network, List<IdentitySettlement> settlements) {
        if (settlements.isEmpty()) return UNLOADED;
        if (settlements.stream().anyMatch(settlement -> settlement.status() == IdentityStatus.SETTLED
                && settlement.networkId().filter(network::equals).isPresent())) {
            return SETTLED;
        }
        var statuses = settlements.stream().map(IdentitySettlement::status).toList();
        if (statuses.contains(IdentityStatus.AMBIGUOUS_MERGE)) return MERGE;
        if (statuses.contains(IdentityStatus.AMBIGUOUS_SPLIT)) return SPLIT;
        if (statuses.contains(IdentityStatus.COPIED_LIVE_IDENTITY) || statuses.contains(IdentityStatus.CONFLICTING_NODE_DATA)) {
            return COPIED;
        }
        return LOADING;
    }

    /** Names are only shown and edited for an identity that is not in doubt. */
    public boolean renamable() {
        return this == SETTLED;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
