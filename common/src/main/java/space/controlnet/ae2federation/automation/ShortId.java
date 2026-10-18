package space.controlnet.ae2federation.automation;

import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * Short random IDs a player can read and type, such as {@code interface-k7m2qx}: a prefix, a dash and six characters
 * from an alphabet without look-alikes (no 0/o, 1/l/i). Random rather than counted, as the user chose; drawn again
 * while the caller says it is taken.
 */
public final class ShortId {
    public static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    public static final int LENGTH = 6;

    private ShortId() {
    }

    public static String draw(String prefix, RandomGenerator random, Predicate<String> taken) {
        while (true) {
            var id = new StringBuilder(prefix).append('-');
            for (int i = 0; i < LENGTH; i++) {
                id.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            if (!taken.test(id.toString())) {
                return id.toString();
            }
        }
    }

    public static boolean valid(String prefix, String id) {
        if (id.length() != prefix.length() + 1 + LENGTH || !id.startsWith(prefix + "-")) {
            return false;
        }
        return id.substring(prefix.length() + 1).chars().allMatch(c -> ALPHABET.indexOf(c) >= 0);
    }
}
