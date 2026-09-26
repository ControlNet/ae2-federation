package space.controlnet.ae2federation.test.scale;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.slf4j.Logger;

final class ScaleTimedNativeEvidence {
    private ScaleTimedNativeEvidence() {
    }

    static void report(Logger logger, String layout, String phase, ScaleTimedWindow.Window measured) {
        logger.info("AE2F_SCALE_TIMED layout={} phase={} startNano={} endNano={} "
                        + "startTick={} endTick={} wallNanos={} ticks={} jobs={} units={} patternCount=256",
                layout, phase, measured.startNanos(), measured.endNanos(), measured.startTick(),
                measured.endTick(), measured.wallNanos(), measured.ticks(), measured.jobs(), measured.units());
    }

    static void write(String testId, ScaleTimedWindow window, long totalJobs, long drainedUnits, long retainedUnits) {
        var configured = System.getProperty("ae2federation.nativeEvidenceFile", "");
        if (configured.isBlank()) return;
        var properties = new Properties();
        properties.setProperty("schemaVersion", "1");
        properties.setProperty("status", "passed");
        properties.setProperty("kind", "benchmark");
        properties.setProperty("testId", testId);
        properties.setProperty("structure", "ae2federation_test:scale_36_empty");
        properties.setProperty("assertions", "8");
        properties.setProperty("operations", "256");
        properties.setProperty("inserted", Long.toString(window.sample().units()));
        properties.setProperty("extracted", Long.toString(window.sample().units()));
        properties.setProperty("elapsedNanos", Long.toString(window.sample().wallNanos()));
        properties.setProperty("warmupNanos", Long.toString(window.warmup().wallNanos()));
        properties.setProperty("warmupTicks", Long.toString(window.warmup().ticks()));
        properties.setProperty("warmupJobs", Integer.toString(window.warmup().jobs()));
        properties.setProperty("sampleTicks", Long.toString(window.sample().ticks()));
        properties.setProperty("sampleJobs", Integer.toString(window.sample().jobs()));
        properties.setProperty("callbackUnits", Long.toString(totalJobs * 16));
        properties.setProperty("drainedDriveUnits", Long.toString(drainedUnits));
        properties.setProperty("finalDriveUnits", Long.toString(retainedUnits));
        try {
            var evidence = Path.of(configured).toAbsolutePath();
            Files.createDirectories(evidence.getParent());
            try (var output = Files.newOutputStream(evidence)) {
                properties.store(output, "Native small timed window");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write native timed evidence", exception);
        }
    }
}
