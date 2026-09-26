package space.controlnet.ae2federation.test.processing;

import java.util.EnumMap;
import java.util.Map;
import space.controlnet.ae2federation.processing.provider.ProviderTargetState;

public final class ProviderTargetObservation {
    private static final Map<ProviderTargetState, Integer> AUTHORIZATIONS = new EnumMap<>(ProviderTargetState.class);
    private static int bindings;
    private static int mixinLookups;
    private static int nativeTargetLookups;
    private static int nativeTargetsFound;
    private static int capabilityLookups;

    private ProviderTargetObservation() {
    }

    public static synchronized void reset() {
        AUTHORIZATIONS.clear();
        bindings = 0;
        mixinLookups = 0;
        nativeTargetLookups = 0;
        nativeTargetsFound = 0;
        capabilityLookups = 0;
    }

    public static synchronized void recordBinding() {
        bindings++;
    }

    public static synchronized void recordAuthorization(ProviderTargetState state) {
        AUTHORIZATIONS.merge(state, 1, Integer::sum);
    }

    public static synchronized void recordMixinLookup() {
        mixinLookups++;
    }

    public static synchronized void recordNativeTargetLookup() {
        nativeTargetLookups++;
    }

    public static synchronized void recordNativeTargetFound() {
        nativeTargetsFound++;
    }

    public static synchronized void recordCapabilityLookup() {
        capabilityLookups++;
    }

    public static synchronized int bindings() {
        return bindings;
    }

    public static synchronized int mixinLookups() {
        return mixinLookups;
    }

    public static synchronized int nativeTargetLookups() {
        return nativeTargetLookups;
    }

    public static synchronized int nativeTargetsFound() {
        return nativeTargetsFound;
    }

    public static synchronized int capabilityLookups() {
        return capabilityLookups;
    }

    public static synchronized int authorizations(ProviderTargetState state) {
        return AUTHORIZATIONS.getOrDefault(state, 0);
    }
}
