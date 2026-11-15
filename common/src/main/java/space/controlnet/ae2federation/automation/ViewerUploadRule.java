package space.controlnet.ae2federation.automation;

/**
 * Who may send the recipes their recipe viewer shows, which every program's {@code mc.recipes_for} then lists:
 * everyone (the default, owner's choice 2026-11-15), or operators only (permission level 2). The owner of a
 * singleplayer or LAN world always may. The server configuration sets it when it loads; until then, and in tests,
 * the default holds.
 */
public final class ViewerUploadRule {
    public enum Who {
        EVERYONE,
        OPERATORS
    }

    private static volatile Who who = Who.EVERYONE;

    private ViewerUploadRule() {
    }

    public static Who who() {
        return who;
    }

    public static void set(Who allowed) {
        who = java.util.Objects.requireNonNull(allowed);
    }

    /** Whether a player who is, or is not, an operator and the world's owner may send their viewer's recipes. */
    public static boolean allows(boolean operator, boolean owner) {
        return who == Who.EVERYONE || operator || owner;
    }
}
