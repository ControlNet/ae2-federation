package space.controlnet.ae2federation.crafting.projection;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import space.controlnet.ae2federation.identity.NetworkId;

/**
 * Which networks are joined through Federation Domains, directly or through members two domains share, as a chained
 * push travels: a returning output may cross only such a link.
 */
final class FederationLinks {
    private final Map<NetworkId, NetworkId> parents;

    private FederationLinks(Map<NetworkId, NetworkId> parents) {
        this.parents = parents;
    }

    /** {@code domains} holds each domain's member networks. */
    static FederationLinks of(Collection<? extends Set<NetworkId>> domains) {
        var parents = new HashMap<NetworkId, NetworkId>();
        for (var members : domains) {
            NetworkId first = null;
            for (var member : members) {
                parents.putIfAbsent(member, member);
                if (first == null) {
                    first = member;
                } else {
                    parents.put(root(parents, member), root(parents, first));
                }
            }
        }
        return new FederationLinks(parents);
    }

    boolean linked(NetworkId first, NetworkId second) {
        return parents.containsKey(first) && parents.containsKey(second)
                && root(parents, first).equals(root(parents, second));
    }

    private static NetworkId root(Map<NetworkId, NetworkId> parents, NetworkId network) {
        var root = network;
        while (!parents.get(root).equals(root)) root = parents.get(root);
        return root;
    }
}
