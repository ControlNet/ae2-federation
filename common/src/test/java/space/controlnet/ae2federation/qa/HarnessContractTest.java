package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class HarnessContractTest {
    private static final Path REPOSITORY_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void taskEightRegistersExactlyFourNativeProofsWhenManifestIsLoaded() throws IOException {
        var manifest = Files.readString(REPOSITORY_ROOT.resolve("tests/scenarios/manifest.json"));
        var actual = new java.util.TreeSet<String>();
        var entries = java.util.regex.Pattern.compile("\\{[^{}]*\"id\":\"(storage-proof\\.[^\"]+)\"[^{}]*}")
                .matcher(manifest);
        while (entries.find()) {
            assertTrue(entries.group().contains("\"backend\":\"gametest\""));
            assertTrue(java.util.regex.Pattern.compile("\"assertions\":[1-9][0-9]*").matcher(entries.group()).find());
            assertTrue(actual.add(entries.group(1)), "Task 8 cases must be unique");
        }
        assertEquals(java.util.Set.of("storage-proof.native-projection", "storage-proof.four-domain-diamond",
                "storage-proof.reject-loop", "storage-proof.opaque-alias"), actual);
    }

    @Test
    void taskEightConsumerRequiresNativeMountCorrelationWhenEvidenceIsConsumed() throws IOException {
        var script = Files.readString(REPOSITORY_ROOT.resolve("gradle/federation-qa.gradle"));
        assertTrue(script.contains("verifyTaskEightEvidence(attempt"));
        assertTrue(script.contains("AE2F_STORAGE_NATIVE_MOUNT"));
        assertTrue(script.contains("AE2F_STORAGE_NATIVE_DELEGATE"));
        assertTrue(script.contains("AE2F_STORAGE_PROVIDER_MOUNT"));
        assertTrue(script.contains("federationTaskEightEvidenceSelfTest"));
    }

    @Test
    void taskEightProvenanceRequiresProductionOwnedMountRegistration() throws IOException {
        var provenance = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/ae2/storage/NativeStorageProvenance.java"));
        var provider = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/ae2/storage/NativeStorageProvider.java"));
        var gameTests = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/StorageProofGameTests.java"));
        assertTrue(provenance.contains("provider.mountInventories"));
        assertTrue(provenance.contains("node.getService(IStorageProvider.class)"));
        assertTrue(provenance.contains("qualifiedMounts"));
        assertTrue(provenance.contains("nativeSources"));
        assertTrue(provenance.contains("OPAQUE_ALIAS"));
        assertTrue(provider.contains("mountRoute"));
        assertTrue(provider.contains("mountProjection"));
        assertFalse(provider.contains("mountNative"));
        assertFalse(provider.contains("mountOpaque"));
        assertFalse(gameTests.contains("registerAlias"));
        assertFalse(gameTests.contains("registerOpaque"));
        assertTrue(gameTests.contains("sources.getFirst().priority(), 40"));
    }

    @Test
    void manifestMapsNativeSmokeToRegisteredGameTest() throws IOException {
        var manifest = REPOSITORY_ROOT.resolve("tests/scenarios/manifest.json");

        assertTrue(Files.isRegularFile(manifest), "scenario manifest must exist");
        var content = Files.readString(manifest);
        assertTrue(content.contains("harness.native-smoke"));
        assertTrue(content.contains("\"testId\":\"harnessnativesmoke\""));
        assertTrue(content.contains("ae2federation_test:harness_native_smoke"));
    }

    @Test
    void harnessDispatchesFromManifestAndRequiresStructuredNativeEvidence() throws IOException {
        var script = Files.readString(REPOSITORY_ROOT.resolve("gradle/federation-qa.gradle"));

        assertTrue(script.contains("testCase.testId"), "GameTest dispatch must use the manifest testId");
        assertTrue(script.contains("testCase.structure"), "native evidence must be checked against the manifest structure");
        assertTrue(script.contains("federationNativeEvidenceFile"), "the child must emit a structured evidence artifact");
        assertTrue(script.contains("verifyPersistedReport"), "persisted result reports must have a reusable verifier");
    }

    @Test
    void benchmarkPerformsAndReconcilesModulatedWork() throws IOException {
        var source = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/FederationGameTests.java"));

        assertFalse(source.contains("Actionable.SIMULATE"), "benchmark work must not be simulation-only");
        assertTrue(source.contains("benchmarkInserted"), "benchmark evidence must account for inserted items");
        assertTrue(source.contains("benchmarkExtracted"), "benchmark evidence must reconcile extracted items");
    }

    @Test
    void productionJarExcludesDevTestmod() throws IOException {
        var jar = REPOSITORY_ROOT.resolve("neoforge-1.21.1/build/libs/ae2federation-0.1.0-dev.jar");

        assertTrue(Files.isRegularFile(jar), "production jar baseline must exist");
        try (var zip = new java.util.zip.ZipFile(jar.toFile())) {
            var testmodEntries = zip.stream()
                    .filter(entry -> entry.getName().contains("ae2federation/test"))
                    .count();
            assertEquals(0, testmodEntries, "production jar must exclude dev testmod classes");
        }
    }

    @Test
    void manifestRegistersTaskFourNativeCases() throws IOException {
        var content = Files.readString(REPOSITORY_ROOT.resolve("tests/scenarios/manifest.json"));

        assertTrue(content.contains("identity.replace-all-access"));
        assertTrue(content.contains("identity.restart"));
        assertTrue(content.contains("identity.ambiguous-split"));
        assertTrue(content.contains("identity.copied-node"));
        assertTrue(content.contains("\"testId\":\"identityrestart\""));
    }

    @Test
    void sourceIdentityExcludesDynamicOmoRecords() throws IOException {
        var script = Files.readString(REPOSITORY_ROOT.resolve("gradle/federation-qa.gradle"));

        assertTrue(script.contains(":(exclude).omo/**"),
                "dynamic evidence, session, knowledge, and completion records must not stale product source identity");
    }

    @Test
    void taskFourPersistedConsumerParsesNativeSemantics() throws IOException {
        var script = Files.readString(REPOSITORY_ROOT.resolve("gradle/federation-qa.gradle"));

        assertTrue(script.contains("verifyTaskFourEvidence"));
        assertTrue(script.contains("copied-live-identity"));
        assertTrue(script.contains("recoveredNetworkId"));
        assertTrue(script.contains("Task 4 restart traces do not prove two-process native reconstruction"));
        assertTrue(script.contains("declaredNativeNames.toSet() != expectedArtifactNames"));
        assertTrue(script.contains("requireNativeObjectIdentity"));
        assertTrue(script.contains("federationTaskFourIdentityFactsSelfTest"));
    }

    @Test
    void replaceAllAccessUsesDistinctFederationAttachments() throws IOException {
        var source = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/IdentityGameTests.java"));

        assertTrue(source.contains("FederationIdentityAccessAttachment.attach(node)"));
        assertTrue(source.contains("activeCount(node), 0"));
        assertTrue(source.contains("Waiting one tick with no Federation access attachment"));
        assertTrue(!source.contains("Blocks.STONE"));
        assertTrue(!source.contains("runAfterDelay"));
    }

    @Test
    void manifestRegistersExactTaskFiveNativeCases() throws IOException {
        var content = Files.readString(REPOSITORY_ROOT.resolve("tests/scenarios/manifest.json"));

        assertTrue(content.contains("ports.bridge-cable-device"));
        assertTrue(content.contains("ports.router-six-grids"));
        assertTrue(content.contains("ports.router-repeated-grid"));
        assertTrue(content.contains("ports.reject-floating-node"));
        assertTrue(content.contains("ports.reject-cross-grid-join"));
        assertTrue(content.contains("\"testId\":\"portsbridgecabledevice\""));
    }

    @Test
    void taskFivePersistedConsumerRejectsForgedTopologySemantics() throws IOException {
        var script = Files.readString(REPOSITORY_ROOT.resolve("gradle/federation-qa.gradle"));

        assertTrue(script.contains("verifyTaskFiveEvidence"));
        assertTrue(script.contains("Task 5 native artifact set is duplicated, missing, or substituted"));
        assertTrue(script.contains("Task 5 repeated Grid identity or face ownership is invalid"));
        assertTrue(script.contains("Task 5 floating boundary node was accepted"));
        assertTrue(script.contains("Task 5 runtime trace identity mismatch"));
        assertTrue(script.contains("replacedAccepted"));
        assertTrue(script.contains("distinct-grid-substitution"));
        assertTrue(script.contains("Task 5 cross-grid join was accepted"));
        assertTrue(script.contains("federationTaskFiveEvidenceSelfTest"));
    }

    @Test
    void manifestRegistersExactTaskSixNativeCases() throws IOException {
        var content = Files.readString(REPOSITORY_ROOT.resolve("tests/scenarios/manifest.json"));

        assertTrue(content.contains("lane.native-three-way"));
        assertTrue(content.contains("lane.pattern-subsets"));
        assertTrue(content.contains("lane.single-drop-owner"));
        assertTrue(content.contains("lane.reject-local-fallback"));
        assertTrue(content.contains("lane.native-reload"));
        assertTrue(content.contains("\"testId\":\"lanenativethreeway\""));
    }

    @Test
    void taskSixCompatibilityBoundaryKeepsNativeOwnership() throws IOException {
        var composition = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/ae2/processing/NativeProviderLaneComposition.java"));
        var lane = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/ae2/processing/NativeProviderLane.java"));

        assertTrue(composition.contains("addGlobalCraftingProvider"));
        assertTrue(composition.contains("refreshGlobalCraftingProvider"));
        assertTrue(composition.contains("removeGlobalCraftingProvider"));
        assertFalse(composition.contains("refreshNodeCraftingProvider"));
        assertTrue(composition.contains("captured.provider() != lane"));
        assertTrue(composition.contains("nativeTickerInvocations"));
        assertTrue(lane.contains("extends PatternProviderLogic"));
        assertTrue(lane.contains("super.pushPattern"));
        assertTrue(lane.contains("super.writeToNBT"));
        assertTrue(lane.contains("super.readFromNBT"));
    }

    @Test
    void taskSixPersistedConsumerRejectsReboundLaneFabrications() throws IOException {
        var script = Files.readString(REPOSITORY_ROOT.resolve("gradle/federation-qa.gradle"));

        assertTrue(script.contains("verifyTaskSixEvidence"));
        assertTrue(script.contains("Task 6 native artifact set is duplicated, missing, or substituted"));
        assertTrue(script.contains("Task 6 runtime trace mismatch"));
        assertTrue(script.contains("singleDropOwner"));
        assertTrue(script.contains("localFallbackAccepted"));
        assertTrue(script.contains("publishedProviderContexts"));
        assertTrue(script.contains("publishedProvidersMatchLanes"));
        assertTrue(script.contains("nativeTickerDelegatesInvoked"));
        assertTrue(script.contains("publishedProvidersAfterClose"));
        assertTrue(script.contains("adjacentMachineCandidate"));
        assertTrue(script.contains("federationTaskSixEvidenceSelfTest"));
    }

    @Test
    void manifestRegistersExactTaskSevenNativeCases() throws IOException {
        var content = Files.readString(REPOSITORY_ROOT.resolve("tests/scenarios/manifest.json"));

        assertTrue(content.contains("endpoint.local-native"));
        assertTrue(content.contains("endpoint.five-face-item-fluid"));
        assertTrue(content.contains("endpoint.reject-two-upstreams"));
        assertTrue(content.contains("endpoint.reject-capability-loop"));
        assertTrue(content.contains("endpoint.mode-isolation"));
        assertTrue(content.contains("\"testId\":\"endpointlocalnative\""));
    }

    @Test
    void taskSevenCompatibilityBoundarySeparatesNativeTargetAndReturnCapabilities() throws IOException {
        var source = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/ae2/processing/endpoint/EndpointCapabilityComposition.java"));
        var provider = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/ae2/processing/endpoint/NativeLocalProvider.java"));

        assertTrue(source.contains("EndpointCallContext"));
        assertTrue(source.contains("PatternProviderReturnInventory"));
        assertTrue(source.contains("MEStorage"));
        assertTrue(source.contains("providerPosition().relative(candidate.targetSide()).equals(endpointPosition)"),
                "Local ownership must require a physically adjacent Provider position");
        assertTrue(source.contains("provider.getLogic() != candidate.logic()"),
                "Local ownership must bind the physical native Provider block entity to its exact logic");
        assertTrue(source.contains("GridHelper.getExposedNode(level, endpointPosition, face)"),
                "Each allowed face must resolve through the native Endpoint integration surface");
        assertTrue(provider.contains("BlockPos providerPosition"),
                "Provider identity must carry its native world position for adjacency validation");
        assertFalse(source.contains("ItemStack.isSame"));
        assertFalse(source.contains("stocking"));
    }

    @Test
    void taskSevenPersistedConsumerRejectsReboundEndpointFabrications() throws IOException {
        var script = Files.readString(REPOSITORY_ROOT.resolve("gradle/federation-qa.gradle"));

        assertTrue(script.contains("verifyTaskSevenEvidence"));
        assertTrue(script.contains("AE2F_ENDPOINT_TRACE"));
        var taskSixStart = script.indexOf("verifyTaskSixEvidence =");
        var taskSevenStart = script.indexOf("verifyTaskSevenEvidence =");
        var persistedStart = script.indexOf("verifyPersistedReport =");
        var taskSixVerifier = script.substring(taskSixStart, taskSevenStart);
        var taskSevenVerifier = script.substring(taskSevenStart, persistedStart);
        assertFalse(taskSixVerifier.contains("AE2F_ENDPOINT_NATIVE_ENTRY"),
                "Task 7 native-entry parsing must not be misplaced in Task 6");
        assertTrue(taskSevenVerifier.contains("AE2F_ENDPOINT_NATIVE_ENTRY"),
                "Task 7 verifier must independently consume Mixin native-entry facts");
        assertTrue(taskSevenVerifier.contains("nativePushOwner"));
        assertTrue(taskSevenVerifier.contains("nativeTargetOwner"));
        assertTrue(script.contains("task07-push-owner-identity"));
        assertTrue(script.contains("task07-target-owner-identity"));
        assertTrue(script.contains("Task 7 native artifact set is duplicated, missing, or substituted"));
        assertTrue(script.contains("Task 7 accepted two Local upstream Providers"));
        assertTrue(script.contains("Task 7 accepted a capability input/return loop"));
        assertTrue(script.contains("Task 7 Local/Federated mode isolation failed"));
        assertTrue(script.contains("federationTaskSevenEvidenceSelfTest"));
    }
}
