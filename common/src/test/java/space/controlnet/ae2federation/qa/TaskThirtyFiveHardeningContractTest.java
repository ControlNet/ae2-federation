package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class TaskThirtyFiveHardeningContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final String[] CASES = {
            "release.server-side-load", "release.no-test-content", "release.optional-absent",
            "packets.reject-malformed", "packets.reject-out-of-context"
    };

    @Test
    void manifestAndHarnessOwnTheExactTaskThirtyFiveCases() throws IOException {
        var manifest = Files.readString(ROOT.resolve("tests/scenarios/manifest.json"));
        var harness = Files.readString(ROOT.resolve("gradle/federation-qa.gradle"));
        for (var caseId : CASES) {
            assertTrue(manifest.contains("\"id\":\"" + caseId + "\""), "Missing Task 35 case " + caseId);
        }
        assertTrue(harness.contains("verifyTaskThirtyFiveEvidence"));
        assertTrue(harness.contains("federationTaskThirtyFiveEvidenceSelfTest"));
    }

    @Test
    void releaseSmokeUsesOnlyTheProductionSourceSetAndChecksOptionalAbsence() throws IOException {
        var build = Files.readString(ROOT.resolve("neoforge-1.21.1/build.gradle"));
        assertTrue(build.contains("releaseServer") && build.contains("sourceSet = sourceSets.main"));
        assertTrue(build.contains("federationReleaseServerSmoke"));
        assertTrue(build.contains("optionalAddonPresent', 'false'")
                && build.contains("com.glodblock.github.appflux"));
    }

    @Test
    void serverActionsUseOwnedPacketWithThreadMenuAndAuthorityValidation() throws IOException {
        var holder = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/client/menu/FederationDomainPolicyMenuHolder.java"));
        var menu = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/client/menu/FederationDomainPolicyMenu.java"));
        var payload = Files.readString(ROOT.resolve(
                "neoforge-1.21.1/src/main/java/space/controlnet/ae2federation/neoforge/network/FederationDomainPolicyActionPayload.java"));
        var registration = Files.readString(ROOT.resolve(
                "neoforge-1.21.1/src/main/java/space/controlnet/ae2federation/neoforge/network/FederationDomainPolicyActionPayloads.java"));
        assertTrue(holder.contains("setOnClick(event -> send(FederationDomainPolicyAction.")
                && !holder.contains("setOnServerClick"));
        assertTrue(menu.contains("isSameThread()") && menu.contains("player.containerMenu"));
        assertTrue(holder.contains("request.containerId()") && holder.contains("request.menuNonce()")
                && holder.contains("request.menuSequence()") && holder.contains("request.context()")
                && holder.contains("request.expectedRevision()"));
        assertTrue(payload.contains("MAX_PAYLOAD_BYTES") && payload.contains("buffer.isReadable()"));
        assertTrue(registration.contains("playToServer") && registration.contains("context.enqueueWork")
                && registration.contains("FederationDomainPolicyActionPayloads.handle"));
    }

    @Test
    void archiveVerificationParsesProductionMetadataAndRejectsTestSemantics() throws IOException {
        var build = Files.readString(ROOT.resolve("neoforge-1.21.1/build.gradle"));
        assertTrue(build.contains("verifyReleaseArchive"));
        assertTrue(build.contains("modId=\"ae2federation\"") && build.contains("modId=\"ae2federation_test\""));
        assertTrue(build.contains("GameTest") && build.contains("LDLRegisterClient"));
    }
}
