package space.controlnet.ae2federation.test.processing;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderLogic;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Test-only stand-in for an addon that hooks AE2's pattern refresh, as AE2 Lightning Tech drops its overload patterns
 * inside {@code PatternProviderLogic.updatePatterns}. Off unless a test turns it on.
 */
public final class AddonPatternHookEmulation {
    private static final Set<PatternProviderLogic> REFRESHED = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>()));
    private static volatile AEKey rejectedOutput;

    private AddonPatternHookEmulation() {
    }

    /** Patterns whose primary output is {@code output} are dropped during refresh, as the addon drops its own kind. */
    public static void reject(AEKey output) {
        rejectedOutput = output;
        REFRESHED.clear();
    }

    public static void reset() {
        rejectedOutput = null;
        REFRESHED.clear();
    }

    public static void recordRefresh(PatternProviderLogic logic) {
        if (rejectedOutput != null) REFRESHED.add(logic);
    }

    public static boolean refreshed(PatternProviderLogic logic) {
        return REFRESHED.contains(logic);
    }

    public static IPatternDetails filter(IPatternDetails details) {
        var rejected = rejectedOutput;
        return details != null && rejected != null && rejected.equals(details.getPrimaryOutput().what()) ? null : details;
    }
}
