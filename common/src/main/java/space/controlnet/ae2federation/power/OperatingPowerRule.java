package space.controlnet.ae2federation.power;

/**
 * The server's operating power rule: whether Federation domains need FE from a Beam Converter to run, and one multiplier
 * over every block's cost. This release ships without the Beam Converter, so nothing turns the rule on and domains run
 * on unlimited energy; tests may still set it.
 */
public final class OperatingPowerRule {
    public static final double DEFAULT_MULTIPLIER = 1.0;
    public static final double MAX_MULTIPLIER = 64.0;

    private static volatile boolean required = false;
    private static volatile double multiplier = DEFAULT_MULTIPLIER;

    private OperatingPowerRule() {
    }

    /** Whether domains need power; when not, they run on unlimited energy. */
    public static boolean required() {
        return required;
    }

    public static double multiplier() {
        return multiplier;
    }

    public static void set(boolean require, double costMultiplier) {
        if (!(costMultiplier >= 0 && costMultiplier <= MAX_MULTIPLIER)) {
            throw new IllegalArgumentException("Operating cost multiplier out of range: " + costMultiplier);
        }
        multiplier = costMultiplier;
        required = require;
    }
}
