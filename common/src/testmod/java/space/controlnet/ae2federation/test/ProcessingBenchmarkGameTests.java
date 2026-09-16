package space.controlnet.ae2federation.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.test.processing.GeneratedProcessingFactoryScene;
import space.controlnet.ae2federation.test.processing.NativeProviderLaneFixtures;
import space.controlnet.ae2federation.test.processing.ProcessingBenchmarkEvidence;
import space.controlnet.ae2federation.test.processing.ProcessingBenchmarkObservation;
import space.controlnet.ae2federation.test.processing.ProcessingBenchmarkProfile;
import space.controlnet.ae2federation.test.processing.ProcessingBenchmarkResult;

@PrefixGameTestTemplate(false)
public final class ProcessingBenchmarkGameTests {
    private static final int ASSERTIONS = 40;

    private ProcessingBenchmarkGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 4000, required = true, manualOnly = true)
    public static void processingBenchmarkSmall(GameTestHelper helper) {
        var profile = ProcessingBenchmarkProfile.load();
        helper.assertTrue(!Boolean.getBoolean("ae2federation.benchmarkEmpty")
                && profile.measuredInputCallsPerScene() > 0,
                "Benchmark profile must request nonzero Processing work");
        ProcessingBenchmarkObservation.reset();
        var state = new BenchmarkState(helper, profile);
        helper.succeedWhen(() -> state.run(helper));
    }

    private static void verifyScene(GameTestHelper helper, ProcessingBenchmarkProfile profile,
            ProcessingBenchmarkResult result, int expectedGridCount) {
        helper.assertValueEqual(result.physicalPatterns(), profile.physicalPatterns(),
                "Generated physical Pattern count must match the profile");
        helper.assertValueEqual(result.gridCount(), expectedGridCount,
                "Generated runtime Grid count must match its comparison topology");
        helper.assertValueEqual(result.logicalPatterns(), profile.logicalPatterns(),
                "Generated logical Pattern count must come from live mappings");
        helper.assertValueEqual(result.logicalLanes(), profile.logicalLanes(),
                "Generated logical Lane count must match the profile");
        helper.assertValueEqual(result.providerEntries(), profile.providerEntries(),
                "Runtime native Provider entries must match the profile");
        helper.assertValueEqual(result.cpuLimit(), profile.cpuLimit(),
                "Runtime native CPU count must match the profile");
        helper.assertTrue(result.observation().nativeInputCalls() > 0,
                "Native CPU execution must reach authentic Processing providers");
        helper.assertTrue(result.observation().rejectedPushes() >= profile.rejectedAttemptsPerScene(),
                "Planner-driven rejection window must exercise target backpressure");
        helper.assertValueEqual(result.rejectedTargetMutation(), 0L,
                "Rejected targets must accept zero mutation");
        helper.assertTrue(result.observation().peakRetainedResponsibility()
                <= profile.primaryOutputUnits() + profile.byproductOutputUnits(),
                "Retained responsibility must remain bounded");
        helper.assertValueEqual(result.observation().finalRetainedResponsibility(), 0L,
                "Measured caller/native responsibility must drain");
        helper.assertValueEqual(result.deliveredPrimary(), (long) profile.plannedOutputUnits(),
                "Delivered primary output must reconcile");
        helper.assertValueEqual(result.deliveredByproduct(),
                (long) profile.byproductOutputUnits() * profile.measurementSamples(),
                "Delivered byproduct output must reconcile");
        helper.assertValueEqual(result.observation().plannerCalls(), (long) profile.measurementSamples(),
                "Every measured sample must use a native planner calculation");
        helper.assertValueEqual(result.observation().submittedJobs(), (long) profile.measurementSamples(),
                "Every measured sample must submit a native CPU job");
        helper.assertTrue(result.observation().cpuBusyTicks() > 0,
                "The native CPU must be observed busy");
        helper.assertValueEqual(result.observation().machineDispatches(), result.observation().machineCompletions(),
                "Every controlled-machine dispatch must complete");
        helper.assertValueEqual(result.observation().reloadCount(), 1L,
                "Requester state must reload once during the replay");
        helper.assertTrue(result.deterministicReload(), "Saved seeded replay must regenerate identically");
        helper.assertValueEqual(result.originalLinkId(), result.reloadedLinkId(),
                "Native crafting UUID must survive reload");
        helper.assertValueEqual(result.observation().finalReturnInventoryUnits(), 0L,
                "Native return inventory must drain");
        helper.assertValueEqual(result.observation().finalSendQueueUnits(), 0L,
                "Native send queue must drain");
        helper.assertTrue(java.util.Arrays.stream(result.timingSamples()).allMatch(sample -> sample > 0),
                "Every environment-sensitive timing sample must be positive");
    }

    private static final class BenchmarkState {
        private final ProcessingBenchmarkProfile profile;
        private NativeProviderLaneFixtures identitySeed;
        private GeneratedProcessingFactoryScene scene;
        private ProcessingBenchmarkResult nativeLarge;
        private ProcessingBenchmarkResult federationLarge;
        private ProcessingBenchmarkResult nativeSmall;
        private ProcessingBenchmarkResult federationSmall;
        private int stage;

        private BenchmarkState(GameTestHelper helper, ProcessingBenchmarkProfile profile) {
            this.profile = profile;
            identitySeed = new NativeProviderLaneFixtures(helper,
                    NativeProviderLaneFixtures.sharedPatternAssignments());
        }

        private void run(GameTestHelper helper) {
            if (identitySeed != null) {
                helper.assertTrue(identitySeed.connectEnergy(), "Waiting for benchmark source identity seed");
                ProcessingBenchmarkObservation.select(ProcessingBenchmarkObservation.Scene.NATIVE);
                identitySeed.register();
                identitySeed.installPattern(0,
                        java.util.List.of(space.controlnet.ae2federation.test.processing.ProcessingRegressionFixtures
                                .item(net.minecraft.world.item.Items.COBBLESTONE, 1)),
                        java.util.List.of(space.controlnet.ae2federation.test.processing.ProcessingRegressionFixtures
                                .item(net.minecraft.world.item.Items.DIAMOND, 1)));
                helper.assertTrue(identitySeed.pushInputs(0, 0,
                        java.util.List.of(space.controlnet.ae2federation.test.processing.ProcessingRegressionFixtures
                                .item(net.minecraft.world.item.Items.COBBLESTONE, 1))),
                        "Benchmark source identity seed must enter authentic native Processing");
                identitySeed.close();
                identitySeed = null;
                helper.assertTrue(false, "Waiting for benchmark source identity handoff");
                return;
            }
            if (scene == null) {
                scene = new GeneratedProcessingFactoryScene(helper, variantProfile(), stage % 2 == 0
                        ? ProcessingBenchmarkObservation.Scene.FEDERATION : ProcessingBenchmarkObservation.Scene.NATIVE);
                helper.assertTrue(false, "Waiting one tick between independent benchmark scenes");
                return;
            }
            helper.assertTrue(scene.ready(), "Waiting for generated Processing factory scene: " + scene.readiness());
            if (scene.resultOrNull() == null && !scene.started()) {
                scene.start();
            }
            if (!scene.tick()) {
                helper.assertTrue(false, "Waiting for generated Processing factory replay: " + scene.progress());
            }
            var result = scene.result();
            var variant = variantProfile();
            verifyScene(helper, variant, result,
                    scene.kind() == ProcessingBenchmarkObservation.Scene.NATIVE ? 1 : profile.gridCount());
            store(result);
            scene.close();
            scene = null;
            stage++;
            if (stage < 4) {
                helper.assertTrue(false, "Waiting for next Processing benchmark comparison scene");
                return;
            }
            helper.assertValueEqual(nativeLarge.acceptedInput(), federationLarge.acceptedInput(),
                    "Native and Federation accepted input resources must be equal");
            helper.assertValueEqual(nativeSmall.acceptedInput(), federationSmall.acceptedInput(),
                    "Native and Federation small-variant input resources must be equal");
            helper.assertValueEqual(nativeLarge.acceptedInput(), nativeSmall.acceptedInput(),
                    "Large and small variants must transport equal input volume");
            helper.assertValueEqual(nativeLarge.deliveredPrimary() + nativeLarge.deliveredByproduct(),
                    federationLarge.deliveredPrimary() + federationLarge.deliveredByproduct(),
                    "Native and Federation delivered output resources must be equal");
            ProcessingBenchmarkEvidence.write(profile, nativeLarge, federationLarge, nativeSmall, federationSmall,
                    ASSERTIONS);
            ProcessingBenchmarkObservation.finish();
        }

        private ProcessingBenchmarkProfile variantProfile() {
            return stage < 2 ? profile.largeVariant() : profile.smallVariant();
        }

        private void store(ProcessingBenchmarkResult result) {
            switch (stage) {
                case 0 -> federationLarge = result;
                case 1 -> nativeLarge = result;
                case 2 -> federationSmall = result;
                case 3 -> nativeSmall = result;
                default -> throw new IllegalStateException("Unexpected Processing benchmark scene stage");
            }
        }
    }
}
