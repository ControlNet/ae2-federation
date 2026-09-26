package space.controlnet.ae2federation.test.energy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NativeEnergyEvidence {
    private static final Logger LOGGER = LoggerFactory.getLogger(NativeEnergyEvidence.class);
    private static final String STRUCTURE = "ae2federation_test:harness_native_smoke";

    private NativeEnergyEvidence() {
    }

    public static void write(String testId, int assertions, Map<String, String> facts) {
        write(testId, assertions, 1, facts);
    }

    public static void write(String testId, int assertions, int operations, Map<String, String> facts) {
        var ordered = new TreeMap<>(facts);
        ordered.forEach((name, value) -> LOGGER.info("AE2F_ENERGY_NATIVE_ENTRY testId={} fact={} value={}",
                testId, name, value));
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
        properties.setProperty("structure", STRUCTURE);
        properties.setProperty("assertions", Integer.toString(assertions));
        properties.setProperty("operations", Integer.toString(operations));
        properties.setProperty("inserted", facts.getOrDefault("extracted", "0"));
        properties.setProperty("extracted", properties.getProperty("inserted"));
        properties.setProperty("elapsedNanos", "0");
        ordered.forEach(properties::setProperty);
        try {
            Files.createDirectories(path.getParent());
            try (var output = Files.newOutputStream(temporary)) {
                properties.store(output, "AE2 Federation native energy evidence");
            }
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write native energy evidence to " + path, exception);
        }
    }
}
