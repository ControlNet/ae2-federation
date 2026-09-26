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

final class FederationDomainRegistryContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void manifestRegistersExactlyFiveExecutableFederationDomainCases() throws IOException {
        var manifest = Files.readString(ROOT.resolve("tests/scenarios/manifest.json"));
        var entries = Pattern.compile("\\{[^{}]*\"id\":\"(domain\\.[^\"]+)\"[^{}]*}").matcher(manifest);
        var cases = new TreeSet<String>();
        while (entries.find()) {
            assertTrue(cases.add(entries.group(1)));
            assertTrue(entries.group().contains("\"backend\":\"gametest\""));
            assertTrue(Pattern.compile("\"testId\":\"federationdomain[a-z]+\"").matcher(entries.group()).find());
        }
        assertEquals(Set.of("domain.bridge-diamond", "domain.router-merge-split", "domain.redundant-membership",
                "domain.partial-unload", "domain.reject-stale-route"), cases);
    }

    @Test
    void productionRegistryIsIncrementalBudgetedAndFailClosed() throws IOException {
        var registry = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/domain/FederationDomainRegistry.java"));
        var access = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/domain/FederationDomainRegistryAccess.java"));
        assertTrue(registry.contains("FederationDomainRecomputeBudget"));
        assertTrue(registry.contains("incomingFederation"));
        assertTrue(registry.contains("NON_RECIPROCAL_EDGE"));
        assertTrue(registry.contains("BUDGET_EXHAUSTED"));
        assertTrue(registry.contains("networkIndex"));
        assertTrue(registry.contains("isCurrent(FederationDomainReference"));
        assertTrue(access.contains("IdentityStatus.SETTLED"));
        assertFalse(registry.contains("getAllChunks"));
        assertFalse(registry.contains("GridHelper.createConnection"));
    }

    @Test
    void runtimeAndEvidencePipelineBindTaskThirteenSemantics() throws IOException {
        var tests = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/FederationDomainGameTests.java"));
        var bridgeTests = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/FederationDomainBridgeGameTests.java"));
        var testMod = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/FederationTestMod.java"));
        var script = Files.readString(ROOT.resolve("gradle/federation-qa.gradle"));
        assertTrue(bridgeTests.contains("federationDomainBridgeDiamond"));
        assertTrue(tests.contains("federationDomainRouterMergeSplit"));
        assertTrue(tests.contains("federationDomainRedundantMembership"));
        assertTrue(tests.contains("federationDomainPartialUnload"));
        assertTrue(tests.contains("federationDomainRejectStaleRoute"));
        assertTrue(testMod.contains("FederationDomainGameTests.class"));
        assertTrue(testMod.contains("FederationDomainBridgeGameTests.class"));
        assertTrue(script.contains("verifyTaskThirteenEvidence"));
        assertTrue(script.contains("AE2F_DOMAIN_NATIVE_TRACE"));
        assertTrue(script.contains("federationTaskThirteenEvidenceSelfTest"));
    }

    @Test
    void routerIdentitySeedChecksLoadedNeighborBeforeLookup() throws IOException {
        var source = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/domain/port/RouterFacePort.java"));
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
