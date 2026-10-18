package space.controlnet.ae2federation.function;

/**
 * What calling a function can do to the game, in the words the player is shown. A library function declares its own;
 * a player function's are those of everything it can reach. Named apart from the script type {@code Capability},
 * which is what a Federation rule shares.
 */
public enum Access {
    READS_NETWORK("reads the network", false),
    READS_WORLD("reads the world", false),
    CRAFTS("starts or cancels crafting", true),
    MOVES_RESOURCES("moves resources", true),
    EMITS_REDSTONE("emits redstone", true),
    CHANGES_WORLD("changes the world", true),
    CHANGES_RULES("changes Federation rules", true),
    WAITS("waits", true);

    private final String label;
    private final boolean acts;

    Access(String label, boolean acts) {
        this.label = label;
        this.acts = acts;
    }

    public String label() {
        return label;
    }

    /** Whether it acts rather than reads: such a call sits on the exec chain and takes a tick. */
    public boolean acts() {
        return acts;
    }
}
