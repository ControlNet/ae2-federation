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
 * network that comes back keeps its ID.
 */
public final class NetworkShortIdBook {
    public static final String PREFIX = "network";

    private final Map<NetworkId, String> ids = new HashMap<>();
    private final Map<String, NetworkId> networks = new HashMap<>();

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

    public Map<NetworkId, String> entries() {
        return Collections.unmodifiableMap(ids);
    }
}
