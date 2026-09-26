package space.controlnet.ae2federation.test.processing;

import appeng.api.behaviors.GenericInternalInventory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProcessingRegressionEvidence {
    public static final String AE2_VERSION = "19.2.17";
    public static final String AE2_DEPENDENCY_JAR_SHA256 =
            "460d779a0609b81409907d9956de8f6f70a1b0912257e3e5c3c7e75ac9630e95";
    public static final String AE2_PATTERN_PROVIDER_LOGIC_SOURCE_SHA256 =
            "46cbd4a6eab1862349c1739b9ef4b808453fe96d623748237a7aa2770777b225";
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingRegressionEvidence.class);

    private ProcessingRegressionEvidence() {
    }

    public static String identity(Object value) {
        return Integer.toUnsignedString(System.identityHashCode(value));
    }

    public static String snapshot(GenericInternalInventory inventory) {
        var values = new StringBuilder();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (slot > 0) {
                values.append(',');
            }
            var key = inventory.getKey(slot);
            values.append(key == null ? "empty" : key.getType().getId() + ":" + inventory.getAmount(slot));
        }
        return values.toString();
    }

    public static void write(String testId, int assertions, Map<String, String> observedFacts) {
        var facts = new TreeMap<>(observedFacts);
        facts.put("ae2Version", AE2_VERSION);
        facts.put("ae2DependencyJarSha256", AE2_DEPENDENCY_JAR_SHA256);
        facts.put("ae2PatternProviderLogicSourceSha256", AE2_PATTERN_PROVIDER_LOGIC_SOURCE_SHA256);
        facts.forEach((fact, value) -> LOGGER.info(
                "AE2F_PROCESSING_NATIVE_TRACE testId={} fact={} value={}", testId, fact, value));
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
        facts.forEach(properties::setProperty);
        try {
            Files.createDirectories(path.getParent());
            try (var output = Files.newOutputStream(temporary)) {
                properties.store(output, "AE2 Federation Task 19 processing regression evidence");
            }
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write Task 19 processing evidence to " + path, exception);
        }
    }
}
