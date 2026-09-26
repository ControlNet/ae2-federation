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

final class RouterTopologyContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void manifestRegistersExactlyFiveExecutableRouterCases() throws IOException {
        var manifest = Files.readString(ROOT.resolve("tests/scenarios/manifest.json"));
        var entries = Pattern.compile("\\{[^{}]*\"id\":\"(router\\.[^\"]+)\"[^{}]*}").matcher(manifest);
        var cases = new TreeSet<String>();
        while (entries.find()) {
            assertTrue(cases.add(entries.group(1)));
            assertTrue(entries.group().contains("\"backend\":\"gametest\""));
            assertTrue(Pattern.compile("\"testId\":\"router[a-z]+\"").matcher(entries.group()).find());
        }
        assertEquals(Set.of("router.mixed-six-faces", "router.six-independent-me", "router.repeat-network",
                "router.port-replacement", "router.reject-unsupported"), cases);
    }

    @Test
    void productionRouterOwnsSixIsolatedNativeBoundariesAndCustomFederationDomainPorts() throws IOException {
        var router = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/router/RouterBlockEntity.java"));
        var face = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/domain/port/RouterFacePort.java"));
        var capability = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/domain/port/FederationPortCapability.java"));
        assertTrue(router.contains("Direction.values()"));
        assertTrue(face.contains("setExposedOnSides(EnumSet.of(face))"));
        assertTrue(face.contains("setIdlePowerUsage(0.0)"));
        assertTrue(face.contains("GridFlags.CANNOT_CARRY"));
        assertTrue(face.contains("NativeAttachmentResolver.resolve"));
        assertTrue(face.contains("BlockCapabilityCache.create"));
        assertTrue(face.contains("level.isLoaded(neighborPosition)"));
        assertTrue(capability.contains("BlockCapability.createSided"));
        assertFalse(router.contains("GridHelper.createConnection"));
        assertFalse(face.contains("GridHelper.createConnection"));
    }

    @Test
    void registeredBlocksHaveProvisionalModelsAndEnglishNames() throws IOException {
        var registration = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/router/RouterRegistration.java"));
        var entrypoint = Files.readString(ROOT.resolve(
                "neoforge-1.21.1/src/main/java/space/controlnet/ae2federation/neoforge/NeoForgeEntrypoint.java"));
        var language = Files.readString(ROOT.resolve(
                "common/src/main/resources/assets/ae2federation/lang/en_us.json"));
        assertTrue(registration.contains("cable"));
        assertTrue(registration.contains("BlockEntityType.Builder.of"));
        assertTrue(registration.contains("RegisterCapabilitiesEvent"));
        assertTrue(registration.contains("AECapabilities.IN_WORLD_GRID_NODE_HOST"));
        assertTrue(entrypoint.contains("RouterRegistration.register(modBus)"));
        assertTrue(language.contains("Federation Router"));
        assertTrue(language.contains("Federation Cable"));
        assertTrue(Files.isRegularFile(ROOT.resolve(
                "common/src/main/resources/assets/ae2federation/models/block/router.json")));
        assertTrue(Files.isRegularFile(ROOT.resolve(
                "common/src/main/resources/assets/ae2federation/models/block/cable.json")));
    }

    @Test
    void runtimeAndEvidencePipelineBindTaskTwelveSemantics() throws IOException {
        var tests = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/RouterGameTests.java"));
        var testMod = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/FederationTestMod.java"));
        var script = Files.readString(ROOT.resolve("gradle/federation-qa.gradle"));
        assertTrue(tests.contains("routerMixedSixFaces"));
        assertTrue(tests.contains("routerSixIndependentMe"));
        assertTrue(tests.contains("routerRepeatNetwork"));
        assertTrue(tests.contains("routerPortReplacement"));
        assertTrue(tests.contains("routerRejectUnsupported"));
        assertTrue(testMod.contains("RouterGameTests.class"));
        assertTrue(script.contains("verifyTaskTwelveEvidence"));
        assertTrue(script.contains("AE2F_ROUTER_NATIVE_TRACE"));
        assertTrue(script.contains("federationTaskTwelveEvidenceSelfTest"));
    }
}
