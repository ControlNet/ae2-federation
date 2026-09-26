package space.controlnet.ae2federation.test.policy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PolicyEvidence {
    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyEvidence.class);

    private PolicyEvidence() {
    }

    public static void trace(String testId, String fact, String value) {
        LOGGER.info("AE2F_POLICY_NATIVE_TRACE testId={} fact={} value={}", testId, fact, value);
    }

    public static void write(String testId, int assertions, Map<String, String> facts) {
        var ordered = new TreeMap<>(facts);
        ordered.forEach((fact, value) -> trace(testId, fact, value));
        var configured = System.getProperty("ae2federation.nativeEvidenceFile", "");
        if (configured.isBlank()) {
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
        properties.setProperty("inserted", "0");
        properties.setProperty("extracted", "0");
        properties.setProperty("elapsedNanos", "0");
        ordered.forEach(properties::setProperty);
        try {
            Files.createDirectories(path.getParent());
            try (var output = Files.newOutputStream(temporary)) {
                properties.store(output, "AE2 Federation Policy evidence");
            }
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write Policy evidence to " + path, exception);
        }
    }
}
