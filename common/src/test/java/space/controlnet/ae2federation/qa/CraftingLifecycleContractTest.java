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

final class CraftingLifecycleContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void manifestRegistersExactTaskTwentyNineCases() throws IOException {
        var manifest = source("tests/scenarios/manifest.json");
        var entries = Pattern.compile("\\{[^{}]*\"id\":\"(crafting\\.(?:cancel-native|disconnect-restart|"
                + "reject-cycle|reject-replay|replace-requester))\"[^{}]*}").matcher(manifest);
        var cases = new TreeSet<String>();
        while (entries.find()) {
            assertTrue(cases.add(entries.group(1)));
            assertTrue(entries.group().contains("\"backend\":\"gametest\""));
            assertTrue(Pattern.compile("\"testId\":\"crafting[a-z]+\"").matcher(entries.group()).find());
        }
        assertEquals(Set.of("crafting.cancel-native", "crafting.disconnect-restart", "crafting.reject-cycle",
                "crafting.reject-replay", "crafting.replace-requester"), cases);
    }

    @Test
    void productionRetainsOnlyNativeRequestAuthority() throws IOException {
        var service = source(
                "common/src/main/java/space/controlnet/ae2federation/crafting/binding/CraftingBindingService.java");
        var binding = source(
                "common/src/main/java/space/controlnet/ae2federation/crafting/binding/NativeCraftingRequestBinding.java");
        var registry = source(
                "common/src/main/java/space/controlnet/ae2federation/crafting/binding/NativeCraftingRequestRegistry.java");
        var session = source(
                "common/src/main/java/space/controlnet/ae2federation/crafting/terminal/NativeTerminalSession.java");
        var request = source(
                "common/src/main/java/space/controlnet/ae2federation/crafting/terminal/NativeTerminalRequest.java");
        var guard = source(
                "common/src/main/java/space/controlnet/ae2federation/crafting/binding/CraftingDependencyCycleGuard.java");

        assertTrue(service.contains("synchronizeNativeRequest"));
        assertTrue(service.contains("retireNativeRequester"));
        assertTrue(service.contains("nativeRequest"));
        assertTrue(service.contains("CraftingDependencyCycleGuard.cyclicKeys"));
        assertTrue(binding.contains("ICraftingLink"));
        assertTrue(binding.contains("ICraftingRequester"));
        assertTrue(binding.contains("link().isCanceled()"));
        assertTrue(binding.contains("link().isDone()"));
        assertFalse(binding.contains("enum"));
        assertFalse(service.contains("resultBuffer"));
        assertFalse(service.contains("scheduler"));
        assertFalse(service.contains("taskQueue"));
        assertFalse(guard.contains("ICraftingService"));
        assertTrue(registry.contains("requester.getRequestedJobs().contains(link)"));
        assertTrue(registry.contains("nativeLinkOwners.remove"));
        assertTrue(registry.contains("nativeRequests.remove"));
        assertTrue(registry.contains("retireTerminal"));
        assertTrue(service.contains("nativeRequests.retireTerminal()"));
        assertTrue(request.contains("synchronizeNativeRequest"));
        assertTrue(request.contains("requester.getRequestedJobs()"));
        assertTrue(session.contains("implements AutoCloseable"));
        assertTrue(session.contains("public void close()"));
        assertTrue(session.contains("if (closed)"));
    }

    @Test
    void runtimeAndEvidenceUseNativeLifecycleAuthority() throws IOException {
        var registration = source(
                "common/src/testmod/java/space/controlnet/ae2federation/test/FederationTestMod.java");
        var tests = source(
                "common/src/testmod/java/space/controlnet/ae2federation/test/CraftingLifecycleGameTests.java");
        var fixture = source(
                "common/src/testmod/java/space/controlnet/ae2federation/test/crafting/CraftingLifecycleFixture.java");
        var bindingFixture = source(
                "common/src/testmod/java/space/controlnet/ae2federation/test/crafting/CraftingBindingFixture.java");
        var authority = source(
                "common/src/testmod/java/space/controlnet/ae2federation/test/crafting/CraftingLifecycleAuthorityObservation.java");
        var physicalInsertionMixin = source(
                "common/src/testmod/java/space/controlnet/ae2federation/test/mixin/TerminalPhysicalResultEvidenceMixin.java");
        var qa = source("gradle/federation-qa.gradle");

        assertTrue(registration.contains("CraftingLifecycleGameTests.class"));
        assertTrue(tests.contains("activeLink().cancel()"));
        assertTrue(tests.contains("removeBridges"));
        assertTrue(tests.contains("restoreBridge"));
        assertTrue(tests.contains("closeTerminalSession"));
        assertTrue(tests.contains("submitTracked"));
        assertTrue(tests.contains("synchronizeTracked"));
        assertTrue(tests.contains("assertTerminalSessionRejectsUse"));
        assertTrue(tests.indexOf("state.recordRetired();\n            var receipt")
                < tests.indexOf("closeLevel(helper.getLevel())"));
        assertTrue(tests.contains("CraftingLifecycleAuthorityObservation.authorizeRequest"));
        assertTrue(tests.contains("CraftingLifecycleAuthorityObservation.physicalInserted()"));
        assertTrue(tests.contains("lateWindow"));
        assertTrue(fixture.contains("NativeCraftingRequester"));
        assertTrue(fixture.contains("writeState"));
        assertTrue(fixture.contains("terminalSession().begin"));
        assertTrue(fixture.contains("binding.sourcePhysicalStorage()"));
        assertTrue(fixture.contains("terminalSession.close()"));
        assertTrue(fixture.contains("terminalSession().craftables()"));
        assertFalse(fixture.contains("terminalSession = null"));
        assertTrue(bindingFixture.contains("getOriginalCellInventory(0)"));
        assertTrue(bindingFixture.contains("return sourcePhysicalStorage().extract(outputKey()"));
        assertTrue(authority.contains("authorize"));
        assertTrue(authority.contains("fixture.sourcePhysicalStorage()"));
        assertTrue(authority.contains("fixture.physicalMaterialAmount(), fixture.physicalOutputAmount()"));
        assertTrue(authority.contains("request.outputAfter = request.fixture.physicalOutputAmount()"));
        assertTrue(authority.contains("recordCallbackAccepted"));
        assertTrue(authority.contains("request.callbackAccepted += accepted"));
        assertTrue(authority.contains("observePhysicalInsertion"));
        assertTrue(authority.contains("destination != request.destination"));
        assertTrue(authority.contains("request.physicalInserted += inserted"));
        assertFalse(authority.contains(".outputAmount()"));
        assertFalse(authority.contains("sourceStorage()"));
        assertTrue(physicalInsertionMixin.contains("CraftingLifecycleAuthorityObservation.observePhysicalInsertion"));
        assertTrue(authority.contains("AE2F_CRAFT_LIFECYCLE_AUTHORITY"));
        assertTrue(qa.contains("AE2F_CRAFT_LIFECYCLE_AUTHORITY"));
        assertTrue(qa.contains("coordinated-lifecycle-forgery"));
        assertTrue(qa.contains("verifyTaskTwentyNineEvidence(attempt"));
        assertTrue(qa.contains("federationTaskTwentyNineEvidenceConsumer"));
        assertTrue(qa.contains("federationTaskTwentyNineEvidenceSelfTest"));
        for (var caseId : Set.of("crafting.cancel-native", "crafting.disconnect-restart", "crafting.reject-cycle",
                "crafting.reject-replay", "crafting.replace-requester")) {
            assertTrue(qa.contains("case '" + caseId + "'"), () -> "missing Task 29 result dispatch: " + caseId);
        }
        for (var probe : Set.of("fake-cancellation-no-consumption", "disconnect-as-failure", "duplicate-resubmission",
                "canned-cycle-label", "replay-changed-job", "same-coordinate-identity-reuse",
                 "closed-ui-result-loss", "aggregate-only-result", "forged-lifecycle-authority",
                 "missing-child", "extra-child", "stale-identity", "extra-native-semantic-fact")) {
            assertTrue(qa.contains(probe), () -> "missing Task 29 adversarial probe: " + probe);
        }
    }

    private static String source(String path) throws IOException {
        return Files.readString(ROOT.resolve(path));
    }
}
