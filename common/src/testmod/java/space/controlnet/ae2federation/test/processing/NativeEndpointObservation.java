package space.controlnet.ae2federation.test.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NativeEndpointObservation {
    private static final Logger LOGGER = LoggerFactory.getLogger(NativeEndpointObservation.class);
    private static int pushOwner;
    private static int targetOwner;

    private NativeEndpointObservation() {
    }

    public static synchronized void reset() {
        pushOwner = 0;
        targetOwner = 0;
    }

    public static synchronized void recordPush(Object owner) {
        pushOwner = System.identityHashCode(owner);
        log("PatternProviderLogic.pushPattern", owner);
    }

    public static synchronized void recordTarget(Object owner) {
        targetOwner = System.identityHashCode(owner);
        log("PatternProviderTargetCache.find", owner);
    }

    public static synchronized String pushOwnerIdentity() {
        return Integer.toUnsignedString(pushOwner);
    }

    public static synchronized String targetOwnerIdentity() {
        return Integer.toUnsignedString(targetOwner);
    }

    private static void log(String method, Object owner) {
        var testId = System.getProperty("ae2federation.testId", "");
        if (testId.startsWith("endpoint")) {
            LOGGER.info("AE2F_ENDPOINT_NATIVE_ENTRY testId={} method={} owner={}", testId, method,
                    Integer.toUnsignedString(System.identityHashCode(owner)));
        }
    }
}
