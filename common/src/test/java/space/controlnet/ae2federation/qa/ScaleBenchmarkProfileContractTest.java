package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ScaleBenchmarkProfileContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void fixedTiersDeclareRequiredMeasurementWindows() throws IOException {
        var targets = Map.of("small", List.of(16, 256, 1_000, 10_000),
                "late", List.of(128, 4_000, 100_000, 1_000_000),
                "ultra", List.of(512, 16_000, 1_000_000, 10_000_000));
        for (var entry : targets.entrySet()) {
            var path = ROOT.resolve("tests/benchmarks/scale/" + entry.getKey() + ".json");
            var profile = Files.readString(path);
            var target = entry.getValue();

            assertTrue(profile.contains("\"id\": \"" + entry.getKey() + "\""));
            assertEquals(target.get(0).intValue(), value(profile, "gridCount"));
            assertEquals(target.get(1).intValue(), value(profile, "logicalPatterns"));
            assertTrue(value(profile, "orderUnits") >= target.get(2));
            assertTrue(value(profile, "orderUnits") <= target.get(3));
            assertEquals(300, value(profile, "warmupSeconds"));
            assertEquals(600, value(profile, "sampleSeconds"));
            assertEquals(3, value(profile, "repetitions"));
            assertTrue(profile.contains("\"layouts\": [\"native-big-grid\", \"native-subnet\", \"federation\"]"));
            assertTrue(profile.contains("\"equalAcrossLayouts\": [\"cpus\", \"energy\", \"loadedChunks\", \"inputReplay\"]"));
            assertTrue(profile.contains("\"perKeyResourceConservation\": true"));
            assertFalse(profile.contains("\"referenceMedian\""));
            assertFalse(profile.contains("\"supportedScale\""));
            assertFalse(profile.contains("\"lifecycle\""));
        }
    }

    @Test
    void timingBudgetCannotBeFrozenFromUnpinnedHost() throws IOException {
        var budget = Files.readString(ROOT.resolve("tests/benchmarks/scale/budgets.json"));

        assertTrue(budget.contains("\"resourceReconciliationRequired\": true"));
        assertTrue(budget.contains("\"equalResourceInputsRequired\": true"));
        assertTrue(budget.contains("\"timingClassification\": \"environment-sensitive-secondary\""));
        assertFalse(budget.contains("\"referenceMedian\""));
        assertFalse(budget.contains("\"maxMspt\""));
    }

    @Test
    void allSmallLayoutsHaveSeparateSustainedNativeSelectors() throws IOException {
        var manifest = Files.readString(ROOT.resolve("tests/scenarios/manifest.json"));
        var selectors = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/ScaleFactoryGameTests.java"));
        var layouts = Map.of("native-big-grid", "ScaleLargeDirect256Replay",
                "native-subnet", "ScaleLargeNativeSubnet256Replay",
                "federation", "ScaleLargeFederation256Replay");
        for (var layout : layouts.entrySet()) {
            var testId = "scalesmall" + layout.getKey().replace("-", "") + "timed";
            assertTrue(manifest.contains("\"id\":\"small-" + layout.getKey() + "\",\"testId\":\"" + testId
                    + "\""), () -> "Missing selected benchmark profile for " + layout.getKey());
            assertTrue(selectors.contains(layout.getValue() + ".runTimed(helper)"),
                    () -> "Missing live timed GameTest for " + layout.getKey());
        }
    }

    @Test
    void scaleFixtureDoesNotForceChunksOutsideBoundedGameTestStructure() throws IOException {
        var fixture = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/scale/ScaleGridFixture.java"));
        assertFalse(fixture.contains("setChunkForced"), "Scale fixture must not create chunk tickets");
        assertFalse(fixture.contains("getForcedChunks"), "Scale fixture must not depend on forced chunks");
    }

    private static int value(String json, String field) {
        var matcher = java.util.regex.Pattern.compile("\\\"" + field + "\\\"\\s*:\\s*([0-9]+)").matcher(json);
        assertTrue(matcher.find(), () -> "missing numeric profile field: " + field);
        return Integer.parseInt(matcher.group(1));
    }
}
