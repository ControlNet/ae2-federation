package space.controlnet.ae2federation.client.policy;

/**
 * What dropping a pattern port on an Endpoint card would do, shown on every card while a wire is dragged. It mirrors
 * the server's refusals; the server still decides.
 */
public enum DropHint {
    CLAIM("claim", Tone.OK),
    ACCEPT("accept", Tone.OK),
    /** Mapping is allowed, but the server pauses dispatch until a processing rule allows it. */
    NO_RULE("no_rule", Tone.WARN),
    EXISTING("existing", Tone.MUTED),
    OCCUPIED("occupied", Tone.ERROR),
    LOCAL("local", Tone.MUTED),
    UNLOADED("unloaded", Tone.MUTED);

    public enum Tone { OK, WARN, ERROR, MUTED }

    private final String code;
    private final Tone tone;

    DropHint(String code, Tone tone) {
        this.code = code;
        this.tone = tone;
    }

    /**
     * {@code claim} is the card's claim code: free, in_use, retained, occupied, local or unobserved.
     * {@code ruleEnabled} is whether a processing rule from the Provider's network to the Endpoint's is on.
     */
    public static DropHint of(String claim, boolean alreadyWired, boolean ruleEnabled) {
        if (alreadyWired) return EXISTING;
        var hint = switch (claim) {
            case "free" -> CLAIM;
            case "in_use", "retained" -> ACCEPT;
            case "occupied" -> OCCUPIED;
            case "local" -> LOCAL;
            default -> UNLOADED;
        };
        return hint.accepts() && !ruleEnabled ? NO_RULE : hint;
    }

    public String code() {
        return code;
    }

    public Tone tone() {
        return tone;
    }

    public boolean accepts() {
        return tone == Tone.OK || tone == Tone.WARN;
    }
}
