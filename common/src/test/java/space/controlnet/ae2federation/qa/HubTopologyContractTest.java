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

final class HubTopologyContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void manifestRegistersExactlyFiveExecutableHubCases() throws IOException {
        var manifest = Files.readString(ROOT.resolve("tests/scenarios/manifest.json"));
        var entries = Pattern.compile("\\{[^{}]*\"id\":\"(hub\\.[^\"]+)\"[^{}]*}").matcher(manifest);
        var cases = new TreeSet<String>();
        while (entries.find()) {
            assertTrue(cases.add(entries.group(1)));
            assertTrue(entries.group().contains("\"backend\":\"gametest\""));
            assertTrue(Pattern.compile("\"testId\":\"hub[a-z]+\"").matcher(entries.group()).find());
        }
        assertEquals(Set.of("hub.mixed-six-faces", "hub.six-independent-me", "hub.repeat-network",
                "hub.port-replacement", "hub.reject-unsupported"), cases);
    }

    @Test
    void productionHubOwnsSixIsolatedNativeBoundariesAndCustomFabricPorts() throws IOException {
        var hub = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/hub/HubBlockEntity.java"));
        var face = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/fabric/port/HubFacePort.java"));
        var capability = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/fabric/port/FederationPortCapability.java"));
        assertTrue(hub.contains("Direction.values()"));
        assertTrue(face.contains("setExposedOnSides(EnumSet.of(face))"));
        assertTrue(face.contains("setIdlePowerUsage(0.0)"));
        assertTrue(face.contains("GridFlags.CANNOT_CARRY"));
        assertTrue(face.contains("NativeAttachmentResolver.resolve"));
        assertTrue(face.contains("BlockCapabilityCache.create"));
        assertTrue(face.contains("level.isLoaded(neighborPosition)"));
        assertTrue(capability.contains("BlockCapability.createSided"));
        assertFalse(hub.contains("GridHelper.createConnection"));
        assertFalse(face.contains("GridHelper.createConnection"));
    }

    @Test
    void registeredBlocksHaveProvisionalModelsAndEnglishNames() throws IOException {
        var registration = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/hub/HubRegistration.java"));
        var entrypoint = Files.readString(ROOT.resolve(
                "neoforge-1.21.1/src/main/java/space/controlnet/ae2federation/neoforge/NeoForgeEntrypoint.java"));
        var language = Files.readString(ROOT.resolve(
                "common/src/main/resources/assets/ae2federation/lang/en_us.json"));
        assertTrue(registration.contains("federation_cable"));
        assertTrue(registration.contains("BlockEntityType.Builder.of"));
        assertTrue(registration.contains("RegisterCapabilitiesEvent"));
        assertTrue(registration.contains("AECapabilities.IN_WORLD_GRID_NODE_HOST"));
        assertTrue(entrypoint.contains("HubRegistration.register(modBus)"));
        assertTrue(language.contains("Federation Hub"));
        assertTrue(language.contains("Federation Cable"));
        assertTrue(Files.isRegularFile(ROOT.resolve(
                "common/src/main/resources/assets/ae2federation/models/block/hub.json")));
        assertTrue(Files.isRegularFile(ROOT.resolve(
                "common/src/main/resources/assets/ae2federation/models/block/federation_cable.json")));
    }

    @Test
    void runtimeAndEvidencePipelineBindTaskTwelveSemantics() throws IOException {
        var tests = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/HubGameTests.java"));
        var testMod = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/FederationTestMod.java"));
        var script = Files.readString(ROOT.resolve("gradle/federation-qa.gradle"));
        assertTrue(tests.contains("hubMixedSixFaces"));
        assertTrue(tests.contains("hubSixIndependentMe"));
        assertTrue(tests.contains("hubRepeatNetwork"));
        assertTrue(tests.contains("hubPortReplacement"));
        assertTrue(tests.contains("hubRejectUnsupported"));
        assertTrue(testMod.contains("HubGameTests.class"));
        assertTrue(script.contains("verifyTaskTwelveEvidence"));
        assertTrue(script.contains("AE2F_HUB_NATIVE_TRACE"));
        assertTrue(script.contains("federationTaskTwelveEvidenceSelfTest"));
    }
}
