package space.controlnet.ae2federation.policy;

/**
 * A rule's switch: off, on, or on and passed on. Re-export lets the consumer pass on what it receives under the rule:
 * with "2 uses 3" re-exporting and "1 uses 2", network 1 reaches network 3 through network 2. A disabled rule never
 * re-exports.
 */
public enum RuleMode {
    DISABLED,
    ENABLED,
    REEXPORT;

    public static RuleMode of(PolicyRule rule) {
        if (!rule.enabled()) return DISABLED;
        return rule.allowReexport() ? REEXPORT : ENABLED;
    }

    public boolean enabled() {
        return this != DISABLED;
    }

    /** Shared energy is one pool across the pair, so it has nothing to pass on. */
    public boolean allowedFor(PolicyCapability capability) {
        return this != REEXPORT || capability != PolicyCapability.ME_POWER;
    }

    public RuleMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public RuleMode previous() {
        return values()[(ordinal() + values().length - 1) % values().length];
    }
}
