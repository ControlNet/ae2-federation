package space.controlnet.ae2federation.automation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;
import space.controlnet.ae2federation.identity.NetworkId;

/**
 * The short ID a program names a network by, such as {@code network-p3x9ka}: drawn the first time it is asked for and
 * kept, as the network's persistent {@link NetworkId} is. Records of networks that no longer exist are kept, so a
 * network that comes back keeps its ID. When a merge folds a network into another, its ID becomes an alias of the
 * survivor, so a program naming it reaches the joined network.
 */
public final class NetworkShortIdBook {
    public static final String PREFIX = "network";

    private final Map<NetworkId, String> ids = new HashMap<>();
    private final Map<String, NetworkId> networks = new HashMap<>();
    // IDs of folded networks, by the network they now name; each is in networks too.
    private final Map<String, NetworkId> aliases = new HashMap<>();

    /** The network's ID, drawn now if it has none. */
    public String id(NetworkId network, RandomGenerator random) {
        return ids.computeIfAbsent(network, unnamed -> {
            var id = ShortId.draw(PREFIX, random, networks::containsKey);
            networks.put(id, unnamed);
            return id;
        });
    }

    public Optional<NetworkId> network(String id) {
        return Optional.ofNullable(networks.get(id));
    }

    /** A loaded record; refused when the ID is malformed or either side is already taken. */
    public boolean put(NetworkId network, String id) {
        if (!ShortId.valid(PREFIX, id) || networks.containsKey(id) || ids.containsKey(network)) {
            return false;
        }
        ids.put(network, id);
        networks.put(id, network);
        return true;
    }

    /** A loaded alias; refused when the ID is malformed or already taken. */
    public boolean alias(String id, NetworkId network) {
        if (!ShortId.valid(PREFIX, id) || networks.containsKey(id)) {
            return false;
        }
        networks.put(id, network);
        aliases.put(id, network);
        return true;
    }

    /**
     * {@code folded} was merged into {@code survivor}: every ID naming it names the survivor. Its own ID becomes the
     * survivor's when the survivor has none, else an alias.
     */
    public void fold(NetworkId folded, NetworkId survivor) {
        networks.replaceAll((id, network) -> network.equals(folded) ? survivor : network);
        aliases.replaceAll((id, network) -> network.equals(folded) ? survivor : network);
        var id = ids.remove(folded);
        if (id == null) return;
        if (ids.containsKey(survivor)) {
            aliases.put(id, survivor);
        } else {
            ids.put(survivor, id);
        }
    }

    public Map<NetworkId, String> entries() {
        return Collections.unmodifiableMap(ids);
    }

    public Map<String, NetworkId> aliases() {
        return Collections.unmodifiableMap(aliases);
    }
}
