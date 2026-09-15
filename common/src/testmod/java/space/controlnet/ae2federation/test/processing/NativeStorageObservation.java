package space.controlnet.ae2federation.test.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NativeStorageObservation {
    private static final Logger LOGGER = LoggerFactory.getLogger(NativeStorageObservation.class);

    private NativeStorageObservation() {
    }

    public static void recordMount(Object aggregate, Object storage, int priority) {
        log("AE2F_STORAGE_NATIVE_MOUNT testId={} aggregate={} storage={} priority={}", aggregate, storage, priority);
    }

    public static void recordDelegate(Object storage) {
        var testId = testId();
        if (!testId.isEmpty()) {
            LOGGER.info("AE2F_STORAGE_NATIVE_DELEGATE testId={} storage={}", testId, identity(storage));
        }
    }

    public static void recordProviderMount(Object provider, Object storage, int priority) {
        log("AE2F_STORAGE_PROVIDER_MOUNT testId={} provider={} storage={} priority={}", provider, storage, priority);
    }

    private static void log(String message, Object owner, Object storage, int priority) {
        var testId = testId();
        if (!testId.isEmpty()) {
            LOGGER.info(message, testId, identity(owner), identity(storage), priority);
        }
    }

    private static String testId() {
        var value = System.getProperty("ae2federation.testId", "");
        return value.startsWith("storageproof") ? value : "";
    }

    private static String identity(Object value) {
        return Integer.toUnsignedString(System.identityHashCode(value));
    }
}
