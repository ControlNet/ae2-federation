package space.controlnet.ae2federation.persistence;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import space.controlnet.ae2federation.identity.NetworkId;

/**
 * Player-assigned network names keyed by the persistent {@link NetworkId}. Records of networks that no longer exist
 * are kept, so a network that comes back keeps its name.
 */
public final class NetworkNameBook {
    public static final int MAX_CODE_POINTS = 32;

    private final Map<NetworkId, String> names = new HashMap<>();

    /** The trimmed name, or empty when it is too long or contains control characters or formatting codes. */
    public static Optional<String> sanitize(String raw) {
        var name = raw.strip();
        if (name.codePointCount(0, name.length()) > MAX_CODE_POINTS) return Optional.empty();
        if (name.codePoints().anyMatch(point -> Character.isISOControl(point) || point == '§')) return Optional.empty();
        return Optional.of(name);
    }

    public Optional<String> name(NetworkId network) {
        return Optional.ofNullable(names.get(network));
    }

    /** Sets or, with an empty name, clears a name; returns whether anything changed. */
    public boolean rename(NetworkId network, String name) {
        var sanitized = sanitize(name).orElseThrow(() -> new IllegalArgumentException("Invalid network name"));
        var previous = sanitized.isEmpty() ? names.remove(network) : names.put(network, sanitized);
        return !sanitized.equals(previous == null ? "" : previous);
    }

    public Map<NetworkId, String> entries() {
        return Collections.unmodifiableMap(names);
    }
}
