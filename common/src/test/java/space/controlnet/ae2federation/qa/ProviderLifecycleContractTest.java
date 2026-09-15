package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

final class ProviderLifecycleContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void manifestRegistersExactlyFiveNativeProviderCases() throws IOException {
        var manifest = Files.readString(ROOT.resolve("tests/scenarios/manifest.json"));
        var entries = Pattern.compile("\\{[^{}]*\"id\":\"(provider\\.(?:pattern-mapping|refresh-priority|single-inventory|reject-stale-pattern|native-ticker-count))\"[^{}]*}")
                .matcher(manifest);
        var cases = new TreeSet<String>();
        while (entries.find()) {
            assertTrue(cases.add(entries.group(1)));
            assertTrue(entries.group().contains("\"backend\":\"gametest\""));
            assertTrue(Pattern.compile("\"testId\":\"provider[a-z]+\"").matcher(entries.group()).find());
        }
        assertEquals(Set.of("provider.pattern-mapping", "provider.refresh-priority", "provider.single-inventory",
                "provider.reject-stale-pattern", "provider.native-ticker-count"), cases);
    }

    @Test
    void productionProviderRetainsNativeOwnershipBoundaries() throws IOException {
        var provider = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/processing/provider/MappedPatternProvider.java"));
        var composition = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/ae2/processing/NativeProviderLaneComposition.java"));
        var lane = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/ae2/processing/NativeProviderLane.java"));

        assertTrue(provider.contains("getTerminalPatternInventory"));
        assertTrue(provider.contains("composition.patternInventory()"));
        assertTrue(provider.contains("composition.writeToNBT"));
        assertTrue(provider.contains("composition.addDrops"));
        assertTrue(provider.contains("composition.refreshLanes"));
        assertTrue(composition.contains("refreshGlobalCraftingProvider"));
        assertTrue(composition.contains("new NativeProviderLaneTicker"));
        assertTrue(lane.contains("currentPatterns.contains"));
        assertTrue(lane.contains("super.pushPattern"));
        assertFalse(provider.contains("new AppEngInternalInventory"));
        assertFalse(provider.contains("implements ICraftingProvider"));
    }

    @Test
    void runtimeAndEvidenceBindTaskSixteenSemantics() throws IOException {
        var tests = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/ProviderLifecycleGameTests.java"));
        var testMod = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/FederationTestMod.java"));
        var script = Files.readString(ROOT.resolve("gradle/federation-qa.gradle"));

        assertTrue(tests.contains("providerPatternMapping"));
        assertTrue(tests.contains("providerRefreshPriority"));
        assertTrue(tests.contains("providerSingleInventory"));
        assertTrue(tests.contains("providerRejectStalePattern"));
        assertTrue(tests.contains("providerNativeTickerCount"));
        assertTrue(testMod.contains("ProviderLifecycleGameTests.class"));
        assertTrue(script.contains("verifyTaskSixteenEvidence"));
        assertTrue(script.contains("AE2F_PROVIDER_NATIVE_TRACE"));
        assertTrue(script.contains("federationTaskSixteenEvidenceSelfTest"));
    }
}
