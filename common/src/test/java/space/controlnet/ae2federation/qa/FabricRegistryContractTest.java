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

final class FabricRegistryContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void manifestRegistersExactlyFiveExecutableFabricCases() throws IOException {
        var manifest = Files.readString(ROOT.resolve("tests/scenarios/manifest.json"));
        var entries = Pattern.compile("\\{[^{}]*\"id\":\"(fabric\\.[^\"]+)\"[^{}]*}").matcher(manifest);
        var cases = new TreeSet<String>();
        while (entries.find()) {
            assertTrue(cases.add(entries.group(1)));
            assertTrue(entries.group().contains("\"backend\":\"gametest\""));
            assertTrue(Pattern.compile("\"testId\":\"fabric[a-z]+\"").matcher(entries.group()).find());
        }
        assertEquals(Set.of("fabric.bridge-diamond", "fabric.hub-merge-split", "fabric.redundant-membership",
                "fabric.partial-unload", "fabric.reject-stale-route"), cases);
    }

    @Test
    void productionRegistryIsIncrementalBudgetedAndFailClosed() throws IOException {
        var registry = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/fabric/FabricRegistry.java"));
        var access = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/fabric/FabricRegistryAccess.java"));
        assertTrue(registry.contains("FabricRecomputeBudget"));
        assertTrue(registry.contains("incomingFederation"));
        assertTrue(registry.contains("NON_RECIPROCAL_EDGE"));
        assertTrue(registry.contains("BUDGET_EXHAUSTED"));
        assertTrue(registry.contains("networkIndex"));
        assertTrue(registry.contains("isCurrent(FabricReference"));
        assertTrue(access.contains("IdentityStatus.SETTLED"));
        assertFalse(registry.contains("getAllChunks"));
        assertFalse(registry.contains("GridHelper.createConnection"));
    }

    @Test
    void runtimeAndEvidencePipelineBindTaskThirteenSemantics() throws IOException {
        var tests = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/FabricGameTests.java"));
        var bridgeTests = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/FabricBridgeGameTests.java"));
        var testMod = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/FederationTestMod.java"));
        var script = Files.readString(ROOT.resolve("gradle/federation-qa.gradle"));
        assertTrue(bridgeTests.contains("fabricBridgeDiamond"));
        assertTrue(tests.contains("fabricHubMergeSplit"));
        assertTrue(tests.contains("fabricRedundantMembership"));
        assertTrue(tests.contains("fabricPartialUnload"));
        assertTrue(tests.contains("fabricRejectStaleRoute"));
        assertTrue(testMod.contains("FabricGameTests.class"));
        assertTrue(testMod.contains("FabricBridgeGameTests.class"));
        assertTrue(script.contains("verifyTaskThirteenEvidence"));
        assertTrue(script.contains("AE2F_FABRIC_NATIVE_TRACE"));
        assertTrue(script.contains("federationTaskThirteenEvidenceSelfTest"));
    }

    @Test
    void hubIdentitySeedChecksLoadedNeighborBeforeLookup() throws IOException {
        var source = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/fabric/port/HubFacePort.java"));
        var initialize = methodBody(source, "public void initialize", "public boolean tick");
        assertLookupFollowsLoadedCheck(initialize, "neighborPosition");
    }

    @Test
    void bridgeOuterIdentitySeedChecksLoadedNeighborBeforeLookup() throws IOException {
        var source = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/bridge/MultipartBridgePart.java"));
        var seeding = methodBody(source, "private void seedBoundaryNodes", "private void setStatus");
        assertLookupFollowsLoadedCheck(seeding, "outerPosition");
    }

    private static String methodBody(String source, String start, String end) {
        return source.substring(source.indexOf(start), source.indexOf(end));
    }

    private static void assertLookupFollowsLoadedCheck(String method, String position) {
        var loadedCheck = method.indexOf("serverLevel.isLoaded(" + position + ")");
        var lookup = method.indexOf("GridHelper.getExposedNode(serverLevel, " + position);
        assertTrue(loadedCheck >= 0);
        assertTrue(lookup > loadedCheck);
    }
}
