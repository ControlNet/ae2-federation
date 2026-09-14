package space.controlnet.ae2federation.ae2.processing.endpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NativeEndpointTrace {
    private static final Logger LOGGER = LoggerFactory.getLogger(NativeEndpointTrace.class);
    private static int pushOwner;
    private static int targetOwner;

    private NativeEndpointTrace() {
    }

    public static void reset() {
        pushOwner = 0;
        targetOwner = 0;
    }

    public static void recordPush(Object owner) {
        pushOwner = System.identityHashCode(owner);
        log("PatternProviderLogic.pushPattern", pushOwner);
    }

    public static void recordTarget(Object owner) {
        targetOwner = System.identityHashCode(owner);
        log("PatternProviderTargetCache.find", targetOwner);
    }

    public static String pushOwnerIdentity() {
        return Integer.toUnsignedString(pushOwner);
    }

    public static String targetOwnerIdentity() {
        return Integer.toUnsignedString(targetOwner);
    }

    private static void log(String method, int owner) {
        var testId = System.getProperty("ae2federation.testId", "");
        if (testId.startsWith("endpoint")) {
            LOGGER.info("AE2F_ENDPOINT_NATIVE_ENTRY testId={} method={} owner={}", testId, method,
                    Integer.toUnsignedString(owner));
        }
    }
}
