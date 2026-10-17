package space.controlnet.ae2federation.beam;

/**
 * The server's beam rule: how far apart two Beam Formers may be, counted in blocks between their fronts. The server
 * configuration sets it when it loads; until then, and in tests, the default holds.
 */
public final class BeamLinkRule {
    public static final int DEFAULT_RANGE = 256;
    public static final int MAX_RANGE = 1024;

    private static volatile int range = DEFAULT_RANGE;

    private BeamLinkRule() {
    }

    public static int range() {
        return range;
    }

    public static void set(int blocks) {
        if (blocks < 1 || blocks > MAX_RANGE) throw new IllegalArgumentException("Beam range out of range: " + blocks);
        range = blocks;
    }
}
