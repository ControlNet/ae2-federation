package space.controlnet.ae2federation.client;

/** M optical design parameters. Load is a visual input, not measured cable traffic. */
public record CableBeamStyle(double load, boolean overloaded, float coreRadius, float haloRadius,
        float pulseAmplitude, float pulseDensity, float emission) {
    /** Capacity accounting is not implemented; this is an explicit presentation default. */
    public static final double DEFAULT_LOAD = 0.6;

    public static CableBeamStyle fromLoad(double load) {
        if (!Double.isFinite(load) || load < 0) throw new IllegalArgumentException("Invalid visual load");
        float u = (float) Math.min(load, 1);
        // Sized for the shell's 6-voxel glass window (half-width 3/16): visible at play distance, never touching the frame.
        return new CableBeamStyle(load, load > 1, .018f + .027f * u, .06f + .09f * u,
                .028f * u, 1 + 2 * u, .45f + 1.55f * u);
    }

    /** Bounded 64-tick clock, continuous across wrap, including negative coordinates. */
    public static double phase(int coordinate, float along, long tick, float partialTick) {
        return (Math.floorMod(coordinate, 2) + along) / 2.0
                - (Math.floorMod(tick, 64) + partialTick) / 64.0;
    }

    public float wave(double phase) {
        int low = (int) pulseDensity;
        float blend = pulseDensity - low;
        return (float) ((1 - blend) * packet(phase * low) + blend * packet(phase * (low + 1)));
    }

    private static double packet(double phase) {
        double value = .5 + .5 * Math.cos(Math.TAU * phase);
        double square = value * value;
        double fourth = square * square;
        return fourth * fourth * fourth;
    }

    public float alarm(long tick, float partialTick) {
        return .5f + .5f * (float) Math.cos(Math.TAU * (Math.floorMod(tick, 32) + partialTick) / 32);
    }

    public float strength(long tick, float partialTick) {
        return emission * (overloaded ? .7f + .4f * alarm(tick, partialTick) : 1);
    }

    public float breath(long tick, float partialTick) {
        return overloaded ? 1 + .1f * alarm(tick, partialTick)
                : 1 + .018f * (float) Math.sin(Math.TAU * (Math.floorMod(tick, 64) + partialTick) / 64);
    }

    /** Use for visual interpolation only; never decide gameplay overload from this value. */
    public static double smooth(double previous, double target, double seconds) {
        if (!Double.isFinite(previous) || !Double.isFinite(target) || !Double.isFinite(seconds)
                || previous < 0 || target < 0 || seconds < 0) throw new IllegalArgumentException("Invalid smoothing input");
        return target + (previous - target) * Math.exp(-seconds / (target > previous ? .25 : .8));
    }
}
