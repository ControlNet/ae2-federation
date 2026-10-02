package space.controlnet.ae2federation.client.policy;

import org.jetbrains.annotations.Nullable;

/**
 * Two networks share energy through one switch, whichever way their ME power rule is written. The switch reads and
 * writes one of the pair's two rules; turning it off on the server turns off the other one too.
 */
public final class SharedEnergySwitch {
    private SharedEnergySwitch() {
    }

    /**
     * Whether the switch uses the second-to-first rule: the rule that is on, otherwise the one that is configured,
     * otherwise first-to-second. Each argument is a rule's enabled flag, or null when the rule is not configured.
     */
    public static boolean reversed(@Nullable Boolean forward, @Nullable Boolean reverse) {
        if (Boolean.TRUE.equals(forward)) return false;
        if (Boolean.TRUE.equals(reverse)) return true;
        return forward == null && reverse != null;
    }

    /** Whether the pair shares energy now: either of its rules is active. */
    public static boolean shares(@Nullable RuleHealth forward, @Nullable RuleHealth reverse) {
        return forward == RuleHealth.ACTIVE || reverse == RuleHealth.ACTIVE;
    }
}
