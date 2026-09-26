package space.controlnet.ae2federation.test.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

public final class UiGraphBenchmarkEvidence {
    private UiGraphBenchmarkEvidence() {
    }

    public static void write(UiGraphBenchmarkProfile profile, Map<String, String> measured) {
        var facts = new TreeMap<>(measured);
        facts.put("schemaVersion", "1");
        facts.put("status", "passed");
        facts.put("kind", "benchmark");
        facts.put("testId", "uigraphbenchmarksmall");
        facts.put("structure", "ae2federation_test:harness_native_smoke");
        facts.put("assertions", "14");
        facts.put("operations", Integer.toString(profile.samples() + 2));
        facts.put("inserted", "0");
        facts.put("extracted", "0");
        facts.put("profileVersion", profile.version());
        facts.put("seed", Long.toString(profile.seed()));
        facts.put("profileSha256", profile.sha256());
        var configured = System.getProperty("ae2federation.nativeEvidenceFile", "");
        if (configured.isBlank()) {
            return;
        }
        var path = Path.of(configured).toAbsolutePath().normalize();
        var temporary = path.resolveSibling(path.getFileName() + ".tmp");
        var properties = new Properties();
        facts.forEach(properties::setProperty);
        try {
            Files.createDirectories(path.getParent());
            try (var output = Files.newOutputStream(temporary)) {
                properties.store(output, "AE2 Federation UI graph benchmark evidence");
            }
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write UI graph benchmark evidence " + path, exception);
        }
    }
}
