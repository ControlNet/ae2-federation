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

    /** How the state is coloured: settled is fine, a doubt is a warning, copied data an error, loading information. */
    public enum Tone {
        OK,
        WARN,
        ERROR,
        INFO,
        MUTED
    }

    public Tone tone() {
        return switch (this) {
            case SETTLED -> Tone.OK;
            case MERGE, SPLIT -> Tone.WARN;
            case COPIED -> Tone.ERROR;
            case LOADING -> Tone.INFO;
            case UNLOADED -> Tone.MUTED;
        };
    }

    /** Merge and split have separate parts in the world that the player can be shown. */
    public boolean hasParts() {
        return this == MERGE || this == SPLIT;
    }

    /** States the player resolves by changing the world, with a resolution line of their own. */
    public boolean hasFix() {
        return this == MERGE || this == SPLIT || this == COPIED;
    }

    /** Names are only shown and edited for an identity that is not in doubt. */
    public boolean renamable() {
        return this == SETTLED;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
