package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class TaskThirtyFourMultiClientContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void harnessUsesTwoRealClientsAndOneDedicatedServer() throws IOException {
        var build = Files.readString(ROOT.resolve("neoforge-1.21.1/build.gradle"));
        var harness = Files.readString(ROOT.resolve("gradle/federation-ui.gradle"));
        var manifest = Files.readString(ROOT.resolve("tests/scenarios/manifest.json"));
        assertTrue(build.contains("multiclientServer") && build.contains("multiclientClientA")
                && build.contains("multiclientClientB"));
        assertTrue(build.contains("--server', '127.0.0.1") && build.contains("online-mode', 'false'"));
        assertTrue(harness.contains("clientCommand('A')") && harness.contains("clientCommand('B')"));
        assertTrue(harness.contains("server.outputStream.withWriter") && harness.contains("serverExit"));
        for (var id : new String[] { "multiclient.shared-server", "multiclient.conflicting-edit",
                "multiclient.domain-split", "multiclient.disconnect-cleanup" }) {
            assertTrue(manifest.contains("\"id\":\"" + id + "\",\"backend\":\"dedicated-multiclient\""));
        }
    }

    @Test
    void evidenceRequiresProductionAuthorityInvalidationAndCleanup() throws IOException {
        var server = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/multiclient/MultiClientServerHarness.java"));
        var client = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/multiclient/MultiClientClientHarness.java"));
        var verifier = Files.readString(ROOT.resolve("gradle/federation-ui.gradle"));
        assertTrue(server.contains("PolicyService.get(level).revision(policyKey)"));
        assertTrue(server.contains("FederationDomainRegistryAccess.get(level).isCurrent(originalReference)"));
        assertTrue(server.contains("subscriptions.activeCount() == 0"));
        assertTrue(client.contains("getElementById(id)")
                && client.contains("UIEvent.create(UIEvents.MOUSE_DOWN)")
                && client.contains("UIEventDispatcher.dispatchEvent(click)"));
        assertTrue(client.contains("FrameCapture.grab()") && client.contains("hasSingleplayerServer()"));
        assertTrue(client.contains("ConnectScreen.startConnecting") && client.contains("ServerAddress.parseString"));
        assertTrue(client.contains("ScreenEvent.Render.Post")
                && client.contains("status.hasClass(\"stale_revision\") && renderedText.equals(expectedText)")
                && client.contains("staleRevisionStableFrames >= 3")
                && client.contains("staleRevisionRenderedText = renderedText"));
        assertTrue(client.contains("minecraft.gui.getChat().clearMessages(true)"));
        assertTrue(!client.contains("setText(status"));
        assertTrue(client.contains("Component.translatable(\"ae2federation.ui.domain.status.ready\").getString()")
                && client.contains("Component.translatable(\"ae2federation.ui.domain.members\", 2).getString()")
                && client.contains("status.hasClass(\"ready\") && renderedStatus.equals(expectedStatus)")
                && client.contains("refreshedStableFrames >= 3")
                && client.contains("refreshedRenderedStatus = renderedStatus")
                && client.contains("refreshedRenderedMembers = memberLines.getFirst()"));
        assertTrue(verifier.contains("refreshedRenderedStatus") && verifier.contains("refreshedRenderedMembers")
                && verifier.contains("refreshedStableFrames")
                && verifier.contains("fabricated-refreshed-render"));
    }

    @Test
    void rejoinReconstructsRouterAndRequiresExactCurrentTwoMemberFederationDomain() throws IOException {
        var server = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/multiclient/MultiClientServerHarness.java"));
        var verifier = Files.readString(ROOT.resolve("gradle/federation-ui.gradle"));
        assertTrue(server.contains("settleRetiredRouter(level)")
                && server.contains("settleRestoredExtension(level)")
                && server.contains("settleCurrentFederationDomain(level)"));
        assertTrue(server.contains("level.setBlockAndUpdate(router, Blocks.AIR.defaultBlockState())")
                && server.contains("level.setBlockAndUpdate(router, RouterRegistration.ROUTER.get().defaultBlockState())")
                && server.contains("routerEntity.neighborChanged(router.north())"));
        assertTrue(server.contains("clientFirstPhaseComplete(\"a\")")
                && server.contains("clientFirstPhaseComplete(\"b\")"));
        assertTrue(server.contains("federationDomain.memberships().size() != 2")
                && server.contains("!registry.isCurrent(candidateReference)")
                && server.contains("federationDomain.memberships().containsKey(mainNetwork)")
                && server.contains("federationDomain.memberships().containsKey(outerNetwork)"));
        assertTrue(verifier.contains("refreshedFederationDomainMembers")
                && verifier.contains("refreshedReferenceCurrent")
                && verifier.contains("refreshedExpectedIdentities"));
    }

    @Test
    void uiSmallBenchmarkBindsGraphCountsCacheAndClosedGuiProgress() throws IOException {
        var harness = Files.readString(ROOT.resolve("gradle/federation-qa.gradle"));
        var benchmark = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/UiGraphBenchmarkGameTests.java"));
        var profile = Files.readString(ROOT.resolve("tests/benchmarks/ui/ui-small.json"));
        assertTrue(harness.contains("verifyTaskThirtyFourBenchmarkEvidence"));
        assertTrue(harness.contains("dataOnlyLayoutReused") && harness.contains("topologyLayoutRebuilt"));
        assertTrue(harness.contains("Task 34 production projection authority mismatch"));
        assertTrue(harness.contains("Task 34 closed-GUI native progress authority mismatch"));
        assertTrue(harness.contains("federationTaskThirtyFourBenchmarkEvidenceSelfTest"));
        assertTrue(benchmark.contains("FederationDomainGraphProjection.snapshot")
                && benchmark.contains("ProviderObservationRegistry.entries"));
        assertTrue(benchmark.contains("scene.closeFirstMenu()") && benchmark.contains("scene.closeSecondMenu()")
                && benchmark.contains("scene.nativeTickerInvocations()"));
        assertTrue(!benchmark.contains("nativeSimulationContinuedWithGuiClosed"));
        assertTrue(profile.contains("\"members\": 24") && profile.contains("\"patterns\": 64"));
    }
}
