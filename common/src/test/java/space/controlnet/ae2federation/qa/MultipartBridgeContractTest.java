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

final class MultipartBridgeContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void manifestRegistersExactlyFiveExecutableBridgeCases() throws IOException {
        var manifest = Files.readString(ROOT.resolve("tests/scenarios/manifest.json"));
        var entries = Pattern.compile("\\{[^{}]*\"id\":\"(bridge\\.[^\"]+)\"[^{}]*}").matcher(manifest);
        var cases = new TreeSet<String>();
        while (entries.find()) {
            assertTrue(cases.add(entries.group(1)));
            assertTrue(entries.group().contains("\"backend\":\"gametest\""));
            assertTrue(Pattern.compile("\"testId\":\"bridge[a-z]+\"").matcher(entries.group()).find());
            assertTrue(Pattern.compile("\"assertions\":[1-9][0-9]*").matcher(entries.group()).find());
        }
        assertEquals(Set.of("bridge.valid", "bridge.invalid", "bridge.same-grid",
                "bridge.reject-federation-cable", "bridge.reload-replace"), cases);
    }

    @Test
    void productionPartUsesNativeAttachmentsWithoutJoiningDomains() throws IOException {
        var part = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/bridge/MultipartBridgePart.java"));
        var topology = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/bridge/BridgeTopology.java"));
        var registration = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/bridge/BridgeRegistration.java"));
        assertTrue(part.contains("extends AEBasePart"));
        assertTrue(part.contains("NativeAttachmentResolver.resolve"));
        assertTrue(part.contains("getExternalFacingNode"));
        assertTrue(part.contains("onNeighborChanged"));
        assertTrue(part.contains("onUpdateShape"));
        assertTrue(part.contains("removeFromWorld"));
        assertTrue(part.contains("outerNode.destroy()"));
        assertTrue(topology.contains("BridgeOperationalReason"));
        assertTrue(topology.contains("mainAttachment.grid() != outerAttachment.grid()"));
        assertFalse(part.contains("GridHelper.createConnection"));
        assertTrue(registration.contains("PartItem<MultipartBridgePart>"));
        assertTrue(registration.contains("PartModels.registerModels"));
    }

    @Test
    void productionApiExposesDiagnosticMembershipAndRightClickContext() throws IOException {
        var bridgePackage = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/bridge/MultipartBridgePart.java"))
                + Files.readString(ROOT.resolve(
                        "common/src/main/java/space/controlnet/ae2federation/bridge/BridgeStatus.java"))
                + Files.readString(ROOT.resolve(
                        "common/src/main/java/space/controlnet/ae2federation/bridge/BridgeOperationalReason.java"))
                + Files.readString(ROOT.resolve(
                        "common/src/main/java/space/controlnet/ae2federation/bridge/BridgeRightClickContext.java"));
        assertTrue(bridgePackage.contains("BridgeOperationalReason operationalReason()"));
        assertTrue(bridgePackage.contains("Optional<BridgeMembershipCandidate> membershipCandidate()"));
        assertTrue(bridgePackage.contains("BridgeRightClickContext rightClickContext()"));
        assertTrue(bridgePackage.contains("onUseWithoutItem"));
        assertTrue(bridgePackage.contains("MISSING_MAIN_ATTACHMENT"));
        assertTrue(bridgePackage.contains("MISSING_OUTER_ATTACHMENT"));
        assertTrue(bridgePackage.contains("SAME_GRID"));
        assertTrue(bridgePackage.contains("FEDERATION_CABLE_UNSUPPORTED"));
        assertTrue(bridgePackage.contains("REMOVED"));
    }

    @Test
    void testmodAndEvidencePipelineBindNativeBridgeSemantics() throws IOException {
        var tests = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/MultipartBridgeGameTests.java"));
        var testMod = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/FederationTestMod.java"));
        var script = Files.readString(ROOT.resolve("gradle/federation-qa.gradle"));
        assertTrue(tests.contains("bridgeValid"));
        assertTrue(tests.contains("bridgeInvalid"));
        assertTrue(tests.contains("bridgeSameGrid"));
        assertTrue(tests.contains("bridgeRejectFederationCable"));
        assertTrue(tests.contains("bridgeReloadReplace"));
        assertTrue(testMod.contains("MultipartBridgeGameTests.class"));
        assertTrue(script.contains("verifyTaskElevenEvidence"));
        assertTrue(script.contains("AE2F_BRIDGE_NATIVE_TRACE"));
        assertTrue(script.contains("federationTaskElevenEvidenceSelfTest"));
        assertTrue(script.contains("Task 11 fabricated native attachment identity"));
    }
}
