package space.controlnet.ae2federation.script;

import java.util.Collection;

/** Hands out fresh node IDs: {@code prefix} and a counter. A program's IDs only need to be unique within it. */
public final class Ids {
    private final String prefix;
    private int next;

    public Ids(String prefix) {
        this.prefix = prefix;
    }

    /** Continues after the highest of {@code taken} with this prefix, so new IDs never meet old ones. */
    public Ids(String prefix, Collection<String> taken) {
        this.prefix = prefix;
        for (var id : taken) {
            if (id.startsWith(prefix) && id.length() > prefix.length()
                    && id.substring(prefix.length()).chars().allMatch(Character::isDigit)) {
                next = Math.max(next, Integer.parseInt(id.substring(prefix.length())));
            }
        }
    }

    public String next() {
        return prefix + ++next;
    }
}
