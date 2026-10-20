package space.controlnet.ae2federation.identity;

import java.util.Objects;
import java.util.UUID;

public record NetworkId(UUID value) {
    /** How many leading hexadecimal digits name a network to a player, as in "Network 082A5C". */
    public static final int TAG_DIGITS = 6;

    public NetworkId {
        Objects.requireNonNull(value);
    }

    public static NetworkId create() {
        return new NetworkId(UUID.randomUUID());
    }

    public static NetworkId parse(String value) {
        return new NetworkId(UUID.fromString(value));
    }

    /** The leading digits of a NetworkId's text, in capitals: "082A5C" for "082a5c1e-...". */
    public static String tag(String value) {
        var digits = value.replace("-", "");
        return digits.substring(0, Math.min(TAG_DIGITS, digits.length())).toUpperCase(java.util.Locale.ROOT);
    }

    @Override
    public String toString() {
        return "ae2federation:network:v1:" + value;
    }
}
