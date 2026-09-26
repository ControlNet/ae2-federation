package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class EndpointModeContractTest {
    private static final Path REPOSITORY_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void taskSevenBaselineRemainsNativeAndBufferless() throws IOException {
        var composition = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/ae2/processing/endpoint/EndpointCapabilityComposition.java"));

        assertTrue(composition.contains("PatternProviderBlockEntity"));
        assertTrue(composition.contains("GridHelper.getExposedNode"));
        assertTrue(composition.contains("GenericStackItemStorage"));
        assertTrue(composition.contains("GenericStackFluidStorage"));
        assertTrue(composition.contains("getReturnInv()"));
        assertFalse(composition.contains("AppEngInternalInventory"));
    }

    @Test
    void productionRuntimeOwnsTypedModesAndImmutableNativeReturns() throws IOException {
        var runtime = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/processing/endpoint/EndpointRuntime.java"));
        var generation = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/processing/endpoint/EndpointModeGeneration.java"));
        var itemContext = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/processing/endpoint/EndpointItemReturnContext.java"));
        var fluidContext = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/processing/endpoint/EndpointFluidReturnContext.java"));

        assertTrue(runtime.contains("EndpointCapabilityComposition"));
        assertTrue(runtime.contains("discoverLocal"));
        assertTrue(runtime.contains("ClaimState.Owned"));
        assertFalse(runtime.contains("ItemStack.isSame"));
        assertTrue(generation.contains("record Local"));
        assertTrue(generation.contains("record Federated"));
        assertTrue(itemContext.contains("GenericStackItemStorage"));
        assertTrue(fluidContext.contains("GenericStackFluidStorage"));
    }

    @Test
    void federatedAuthorizationBindsModeGenerationAndNativeLaneProvenance() throws IOException {
        var target = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/processing/provider/AuthorizedNativeTarget.java"));
        var runtime = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/processing/endpoint/EndpointRuntime.java"));
        var cache = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/ae2/processing/FederationPatternProviderTargetCache.java"));
        var binding = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/processing/endpoint/EndpointTargetBinding.java"));

        assertTrue(target.contains("EndpointModeGeneration.Federated mode"));
        assertTrue(target.contains("ProviderLogicProvenance provenance"));
        assertTrue(runtime.contains("target.mode().equals(federated)"));
        assertTrue(cache.contains("authorized.target().provenance() != binding.provenance"));
        assertFalse(binding.contains("AuthorizedNativeTarget target, PatternProviderLogic logic"));
    }

    @Test
    void productionSourcesContainNoReplayBridge() throws IOException {
        var providerRuntime = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/processing/provider/ProviderRuntime.java"));
        var probe = REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/processing/provider/ProviderRuntimeProbe.java");
        var targetTrace = REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/ae2/processing/ProviderTargetTrace.java");
        var endpointTrace = REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/ae2/processing/endpoint/NativeEndpointTrace.java");
        var storageTrace = REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/ae2/storage/NativeStorageTrace.java");

        assertFalse(Files.exists(probe), "the production source set must not contain a replay probe");
        assertFalse(Files.exists(targetTrace), "the production source set must not contain Provider evidence state");
        assertFalse(Files.exists(endpointTrace), "the production source set must not contain Endpoint evidence state");
        assertFalse(Files.exists(storageTrace), "the production source set must not contain storage evidence state");
        assertFalse(providerRuntime.contains("replayResolutions"),
                "the production ProviderRuntime must not carry replay state");
        assertFalse(providerRuntime.contains("replayAuthorizedResolutionOnce"),
                "the production ProviderRuntime must not expose replay behavior");
        assertFalse(providerRuntime.contains("laneResolutions"),
                "the production ProviderRuntime must not retain authorization solely for tests");
        assertFalse(providerRuntime.contains("laneProvenance("),
                "the production ProviderRuntime must not expose Lane provenance to tests");
    }

    @Test
    void replayControlExistsOnlyInTheTestmodAndObservesProductionResults() throws IOException {
        var mixinConfig = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/testmod/resources/ae2federation_test.mixins.json"));
        var controller = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/processing/ProviderRuntimeReplayControl.java"));
        var runtimeMixin = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/mixin/ProviderRuntimeReplayMixin.java"));

        assertTrue(mixinConfig.contains("ProviderRuntimeReplayMixin"));
        assertTrue(runtimeMixin.contains("@At(\"RETURN\")"));
        assertTrue(runtimeMixin.contains("callback.getReturnValue()"));
        assertTrue(controller.contains("issued instanceof ProviderTargetResolution.Authorized"));
        assertFalse(controller.contains("ProviderTargetAuthorization.resolve"),
                "the testmod seam must not copy production authorization");
        assertFalse(controller.contains("new AuthorizedNativeTarget"),
                "the testmod seam must only replay package-issued targets");
    }

    @Test
    void noSinkUsesProductionReturnAttemptResult() throws IOException {
        var attempt = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/processing/endpoint/EndpointItemReturnAttempt.java"));
        var tests = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/EndpointReturnGameTests.java"));

        assertTrue(attempt.contains("handler == null"));
        assertTrue(attempt.contains("handler.insertItem"));
        assertTrue(tests.contains("EndpointItemReturnAttempt.insert"));
        assertFalse(tests.contains("var callerOwned = new ItemStack"));
    }

    @Test
    void productionCapabilitiesExcludeFederationFace() throws IOException {
        var registration = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/processing/ProcessingRegistration.java"));
        var endpoint = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/processing/endpoint/EndpointBlockEntity.java"));

        assertTrue(registration.contains("Capabilities.ItemHandler.BLOCK"));
        assertTrue(registration.contains("Capabilities.FluidHandler.BLOCK"));
        assertTrue(registration.contains("AECapabilities.ME_STORAGE"));
        assertTrue(registration.contains("AECapabilities.IN_WORLD_GRID_NODE_HOST"));
        assertTrue(endpoint.contains("EnumSet.complementOf"));
    }

    @Test
    void manifestAndEvidenceRegisterExactTaskEighteenCases() throws IOException {
        var manifest = Files.readString(REPOSITORY_ROOT.resolve("tests/scenarios/manifest.json"));
        var qa = Files.readString(REPOSITORY_ROOT.resolve("gradle/federation-qa.gradle"));

        assertTrue(manifest.contains("endpoint.local"));
        assertTrue(manifest.contains("endpoint.federated"));
        assertTrue(manifest.contains("endpoint.five-face-returns"));
        assertTrue(manifest.contains("endpoint.return-backpressure"));
        assertTrue(manifest.contains("endpoint.reject-mode-takeover"));
        assertTrue(qa.contains("verifyTaskEighteenEvidence"));
        assertTrue(qa.contains("federationTaskEighteenEvidenceSelfTest"));
        assertTrue(qa.contains("AE2F_ENDPOINT_MODE_TRACE"));
    }
}
