package space.controlnet.ae2federation.ae2.processing;

import java.util.EnumMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.processing.provider.ProviderTargetState;

public final class ProviderTargetTrace {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderTargetTrace.class);
    private static final Map<ProviderTargetState, Integer> AUTHORIZATIONS = new EnumMap<>(ProviderTargetState.class);
    private static int bindings;
    private static int mixinLookups;
    private static int nativeTargetLookups;
    private static int capabilityLookups;

    private ProviderTargetTrace() {
    }

    public static synchronized void reset() {
        AUTHORIZATIONS.clear();
        bindings = 0;
        mixinLookups = 0;
        nativeTargetLookups = 0;
        capabilityLookups = 0;
    }

    public static synchronized void recordBinding(Object logic) {
        bindings++;
        log("bind", "BOUND", logic);
    }

    public static synchronized void recordAuthorization(ProviderTargetState state, Object sourceNode) {
        AUTHORIZATIONS.merge(state, 1, Integer::sum);
        log("authorize", state.name(), sourceNode);
    }

    public static synchronized void recordMixin(Object logic, boolean bound, boolean targetFound) {
        mixinLookups++;
        log("mixin", bound ? targetFound ? "AUTHORIZED" : "DENIED" : "UNBOUND", logic);
    }

    public static synchronized void recordNativeTarget(Object target) {
        nativeTargetLookups++;
        log("native-target", target == null ? "MISSING" : "FOUND", target);
    }

    public static synchronized void recordCapabilityLookup(Object owner) {
        capabilityLookups++;
        log("capability", "LOOKUP", owner);
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

    public static synchronized int capabilityLookups() {
        return capabilityLookups;
    }

    public static synchronized int authorizations(ProviderTargetState state) {
        return AUTHORIZATIONS.getOrDefault(state, 0);
    }

    private static void log(String stage, String state, Object owner) {
        var testId = System.getProperty("ae2federation.testId", "");
        if (testId.startsWith("provider") || testId.startsWith("claim")) {
            LOGGER.info("AE2F_PROVIDER_TARGET_ENTRY testId={} stage={} state={} owner={}", testId, stage, state,
                    owner == null ? "0" : Integer.toUnsignedString(System.identityHashCode(owner)));
        }
    }
}
