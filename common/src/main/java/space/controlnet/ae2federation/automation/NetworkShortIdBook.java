package space.controlnet.ae2federation.automation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import space.controlnet.ae2federation.identity.NetworkId;

/**
 * The short ID a program names a network by, such as {@code network-082a5c}: the leading digits of its persistent
 * {@link NetworkId} that the topology shows as "Network 082A5C" (user, 2026-10-20). A network whose digits
 * start as an ID already given takes one more digit until its ID is free. An ID is kept once given, so a network that
 * starts as an older one never takes its ID; records of networks that no longer exist are kept too. When a merge folds
 * a network into another, its ID becomes an alias of the survivor, so a program naming it reaches the joined network.
 */
public final class NetworkShortIdBook {
    public static final String PREFIX = "network";
    public static final int MIN_DIGITS = NetworkId.TAG_DIGITS;
    private static final int DIGITS = 32;

    private final Map<NetworkId, String> ids = new HashMap<>();
    private final Map<String, NetworkId> networks = new HashMap<>();
    // IDs of folded networks, by the network they now name; each is in networks too.
    private final Map<String, NetworkId> aliases = new HashMap<>();

    /** Whether the string is shaped as a network's ID: the prefix, a dash and 6 to 32 hexadecimal digits, any case. */
    public static boolean valid(String id) {
        var start = PREFIX.length() + 1;
        if (!id.regionMatches(true, 0, PREFIX + "-", 0, start)) return false;
        int digits = id.length() - start;
        return digits >= MIN_DIGITS && digits <= DIGITS
                && id.substring(start).chars().allMatch(c -> Character.digit(c, 16) >= 0);
    }

    /** The ID as the book keeps it, in lower case. */
    public static String normalize(String id) {
        return id.toLowerCase(Locale.ROOT);
    }

    /** The network's ID, given now if it has none. */
    public String id(NetworkId network) {
        return ids.computeIfAbsent(network, unnamed -> {
            var digits = digits(unnamed);
            var length = MIN_DIGITS;
            while (networks.containsKey(PREFIX + "-" + digits.substring(0, length))) length++;
            var id = PREFIX + "-" + digits.substring(0, length);
            networks.put(id, unnamed);
            return id;
        });
    }

    public Optional<NetworkId> network(String id) {
        return Optional.ofNullable(networks.get(normalize(id)));
    }

    /** A loaded record; refused when the ID does not start the network's own digits or either side is taken. */
    public boolean put(NetworkId network, String id) {
        if (!valid(id) || !digits(network).startsWith(normalize(id).substring(PREFIX.length() + 1))
                || networks.containsKey(normalize(id)) || ids.containsKey(network)) {
            return false;
        }
        ids.put(network, normalize(id));
        networks.put(normalize(id), network);
        return true;
    }

    /** A loaded alias; refused when the ID is malformed or already taken. */
    public boolean alias(String id, NetworkId network) {
        if (!valid(id) || networks.containsKey(normalize(id))) {
            return false;
        }
        networks.put(normalize(id), network);
        aliases.put(normalize(id), network);
        return true;
    }

    /**
     * {@code folded} was merged into {@code survivor}: every ID naming it names the survivor, and its own ID becomes
     * an alias. The survivor keeps its ID, or is given its own when it asks.
     */
    public void fold(NetworkId folded, NetworkId survivor) {
        networks.replaceAll((id, network) -> network.equals(folded) ? survivor : network);
        aliases.replaceAll((id, network) -> network.equals(folded) ? survivor : network);
        var id = ids.remove(folded);
        if (id != null) aliases.put(id, survivor);
    }

    public Map<NetworkId, String> entries() {
        return Collections.unmodifiableMap(ids);
    }

    public Map<String, NetworkId> aliases() {
        return Collections.unmodifiableMap(aliases);
    }

    private static String digits(NetworkId network) {
        return network.value().toString().replace("-", "");
    }
}
