package space.controlnet.ae2federation.test.processing.endpoint;

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

public final class EndpointModeEvidence {
    private static final Logger LOGGER = LoggerFactory.getLogger(EndpointModeEvidence.class);

    private EndpointModeEvidence() {
    }

    public static String identity(Object value) {
        return Integer.toUnsignedString(System.identityHashCode(value));
    }

    public static String inventorySnapshot(GenericInternalInventory inventory) {
        var snapshot = new StringBuilder();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (slot > 0) {
                snapshot.append(',');
            }
            snapshot.append(inventory.getAmount(slot));
        }
        return snapshot.toString();
    }

    public static InventoryObservation observeInventory(String testId, int candidate, String phase,
            GenericInternalInventory inventory) {
        var observation = new InventoryObservation(identity(inventory), inventorySnapshot(inventory), inventory.size());
        LOGGER.info("AE2F_ENDPOINT_INVENTORY_TRACE testId={} candidate={} phase={} identity={} slots={} snapshot={}",
                testId, candidate, phase, observation.identity(), observation.slots(), observation.snapshot());
        return observation;
    }

    public static void write(String testId, int assertions, Map<String, String> facts) {
        var ordered = new TreeMap<>(facts);
        ordered.forEach((fact, value) -> LOGGER.info(
                "AE2F_ENDPOINT_MODE_TRACE testId={} fact={} value={}", testId, fact, value));
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
        ordered.forEach(properties::setProperty);
        try {
            Files.createDirectories(path.getParent());
            try (var output = Files.newOutputStream(temporary)) {
                properties.store(output, "AE2 Federation Endpoint mode evidence");
            }
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write Endpoint mode evidence to " + path, exception);
        }
    }

    public record InventoryObservation(String identity, String snapshot, int slots) {
    }
}
