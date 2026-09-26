package space.controlnet.ae2federation.test.crafting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TerminalCraftingEvidence {
    private static final Logger LOGGER = LoggerFactory.getLogger(TerminalCraftingEvidence.class);

    private TerminalCraftingEvidence() {
    }

    public static void write(String testId, int assertions, Map<String, String> facts) {
        var ordered = new TreeMap<>(facts);
        ordered.forEach((name, value) -> LOGGER.info("AE2F_TERMINAL_FACT testId={} fact={} value={}",
                testId, name, value));
        emitReceipts(testId, TerminalNativeObservation.snapshot());
        var configured = System.getProperty("ae2federation.nativeEvidenceFile", "");
        if (configured.isBlank()) return;
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
        properties.setProperty("inserted", facts.getOrDefault("resultAmount", "0"));
        properties.setProperty("extracted", facts.getOrDefault("resultAmount", "0"));
        properties.setProperty("elapsedNanos", "0");
        ordered.forEach(properties::setProperty);
        try {
            Files.createDirectories(path.getParent());
            try (var output = Files.newOutputStream(temporary)) {
                properties.store(output, "AE2 Federation native terminal evidence");
            }
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write native terminal evidence to " + path, exception);
        } finally {
            TerminalNativeObservation.close();
        }
    }

    private static void emitReceipts(String testId, TerminalNativeObservation.Snapshot snapshot) {
        LOGGER.info("AE2F_TERMINAL_NATIVE_ENTRY testId={} selected={} phase=final service={} requester={} "
                        + "requesterNode={} provider={} cpu={} jobs={} beginCalls={} submitCalls={} providerPushes={} "
                        + "cpuSubmissions={} resultCallbacks={} physicalInserts={} cpuLogic={} callbackOwner={} physicalOwner={}",
                testId, selectedTest(), snapshot.serviceIdentity(), snapshot.requesterIdentity(),
                snapshot.requesterNodeIdentity(), snapshot.providerIdentity(), snapshot.cpuIdentity(),
                snapshot.jobIds().isEmpty() ? "none" : snapshot.joinedJobIds(), snapshot.beginCalls(),
                snapshot.submitCalls(), snapshot.providerPushes(), snapshot.cpuSubmissions(), snapshot.resultCallbacks(),
                snapshot.physicalInserts(), snapshot.cpuLogicIdentity(), snapshot.resultCallbackOwner(),
                snapshot.physicalResultOwner());
        LOGGER.info("AE2F_TERMINAL_PLANNER testId={} selected={} phase=final thread={} actionCalls={} nodeCalls={} "
                        + "rejectedMutableAccess={}", testId, selectedTest(), snapshot.plannerThreadToken(),
                snapshot.requesterActionCalls(), snapshot.requesterNodeCalls(),
                snapshot.rejectedMutableAccess().isEmpty() ? "none"
                        : snapshot.rejectedMutableAccess().stream().sorted().collect(java.util.stream.Collectors.joining(",")));
    }

    private static String selectedTest() {
        return System.getProperty("ae2federation.testId", "");
    }
}
