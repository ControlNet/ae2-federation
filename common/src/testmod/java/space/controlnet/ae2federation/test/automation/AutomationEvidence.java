package space.controlnet.ae2federation.test.automation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AutomationEvidence {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutomationEvidence.class);

    private AutomationEvidence() {
    }

    public static void write(String testId, int assertions, Map<String, String> facts) {
        AutomationAuthorityObservation.emit(testId);
        var ordered = new TreeMap<>(facts);
        ordered.forEach((name, value) -> LOGGER.info("AE2F_AUTOMATION_FACT testId={} fact={} value={}",
                testId, name, value));
        var snapshot = AutomationNativeObservation.snapshot();
        LOGGER.info("AE2F_AUTOMATION_NATIVE_ENTRY testId={} selected={} phase=final trackerOwner={} trackerCalls={} "
                        + "trackerSubmissions={} jobs={} importBus={} exportBus={} importWork={} exportWork={} "
                        + "projection={} storageInsertCalls={} storageExtractCalls={} storageInserted={} "
                        + "storageExtracted={} maxRequested={} keys={}",
                testId, selectedTest(), snapshot.trackerOwner(), snapshot.trackerCalls(), snapshot.trackerSubmissions(),
                snapshot.joinedJobs(), snapshot.importBus(), snapshot.exportBus(), snapshot.importWork(),
                snapshot.exportWork(), snapshot.projection(), snapshot.storageInsertCalls(),
                snapshot.storageExtractCalls(), snapshot.storageInserted(), snapshot.storageExtracted(),
                snapshot.maxRequested(), snapshot.joinedKeys());
        LOGGER.info("AE2F_AUTOMATION_AUTHORITY testId={} selected={} phase=final owner={} source={} destination={} "
                        + "storageKey={} craftingKey={} forward={} reverse={} nativeState={} controller={} ledger={}",
                testId, selectedTest(), facts.get("owner"), facts.get("source"), facts.get("destination"),
                facts.get("storageKey"), facts.get("craftingKey"), facts.get("forwardAuthority"),
                facts.get("reverseAuthority"), facts.get("nativeState"), facts.get("federationController"),
                facts.get("federationLedger"));
        var configured = System.getProperty("ae2federation.nativeEvidenceFile", "");
        if (configured.isBlank()) {
            AutomationNativeObservation.close();
            AutomationAuthorityObservation.close();
            return;
        }
        var path = Path.of(configured).toAbsolutePath().normalize();
        var temporary = path.resolveSibling(path.getFileName() + ".tmp");
        var properties = new Properties();
        properties.setProperty("schemaVersion", "1");
        properties.setProperty("status", "passed");
        properties.setProperty("kind", "verify");
        properties.setProperty("testId", testId);
        properties.setProperty("structure", "ae2federation_test:harness_native_smoke");
        properties.setProperty("assertions", Integer.toString(assertions));
        properties.setProperty("operations", "1");
        properties.setProperty("inserted", "1");
        properties.setProperty("extracted", "1");
        properties.setProperty("elapsedNanos", "0");
        ordered.forEach(properties::setProperty);
        try {
            Files.createDirectories(path.getParent());
            try (var output = Files.newOutputStream(temporary)) {
                properties.store(output, "AE2 Federation native automation evidence");
            }
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write native automation evidence to " + path, exception);
        } finally {
            AutomationNativeObservation.close();
            AutomationAuthorityObservation.close();
        }
    }

    public static String identity(Object value) {
        return Integer.toUnsignedString(System.identityHashCode(value));
    }

    private static String selectedTest() {
        return System.getProperty("ae2federation.testId", "");
    }
}
