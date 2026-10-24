package space.controlnet.ae2federation.power;

import java.util.Arrays;

/**
 * One domain's power state, following AE2's {@code EnergyService}: a domain stops the tick its converters' pooled
 * buffer cannot pay its use, and runs again once more than {@link #RESTART_TICKS} ticks have passed with power. A
 * domain seen for the first time runs at once if it can pay, as a new AE2 grid starts powered.
 */
public final class PowerLedger {
    public static final int RESTART_TICKS = 30;

    private boolean paid = true;
    // AE2 starts a grid long past its last change, so it is powered on its first paid tick.
    private long ticksSinceChange = 900;
    private boolean running = true;

    /** Draws one tick's use from {@code stored}, one entry per converter, and returns whether the domain runs. */
    public boolean settle(double[] stored, double use) {
        var paidNow = drain(stored, use);
        ticksSinceChange = paidNow == paid ? ticksSinceChange + 1 : 0;
        paid = paidNow;
        if (paid && ticksSinceChange > RESTART_TICKS) {
            running = true;
        } else if (!paid) {
            running = false;
        }
        return running;
    }

    public boolean running() {
        return running;
    }

    /**
     * Takes {@code use} from the converters in proportion to what each holds, and returns whether it was paid. A buffer
     * that cannot pay is emptied, as an unpowered AE2 grid keeps drawing, so only input above use brings power back.
     */
    public static boolean drain(double[] stored, double use) {
        if (use <= 0) return true;
        var total = 0.0;
        for (var amount : stored) total += amount;
        if (total < use) {
            Arrays.fill(stored, 0);
            return false;
        }
        var left = (total - use) / total;
        for (var index = 0; index < stored.length; index++) stored[index] *= left;
        return true;
    }
}
