package space.controlnet.ae2federation.compat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Federation against one AE2 Grid for the same scenes. A whole run can order joined
 * ({@code -Dae2federation.compat.joinBeforeOrder=true}); each crafting scene records how many game ticks its job took,
 * and the report carries them, so a Federation run and a joined run of the same tests compare test by test. A joined run
 * skips {@link FederationOnly} tests, which check only what Federation does.
 */
final class NativeParity {
    /** The property that orders every crafting scene of a run joined. */
    static final String JOINED_RUN = "ae2federation.compat.joinBeforeOrder";

    private static final Map<String, Long> JOB_TICKS = new ConcurrentHashMap<>();

    private NativeParity() {
    }

    static boolean joinedRun() {
        return Boolean.getBoolean(JOINED_RUN);
    }

    /** Records that {@code test}'s first job took {@code ticks} game ticks from its submission until its CPU was idle. */
    static void recordJobTicks(String test, long ticks) {
        JOB_TICKS.putIfAbsent(test, ticks);
    }

    /** {@code test}'s recorded job ticks; null for a test without a crafting scene or whose job never finished. */
    static Long jobTicks(String test) {
        return JOB_TICKS.get(test);
    }
}
