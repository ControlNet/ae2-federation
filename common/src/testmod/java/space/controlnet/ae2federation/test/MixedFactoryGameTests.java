package space.controlnet.ae2federation.test;

import appeng.api.stacks.AEItemKey;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.test.automation.NativeAutomationFixture;
import space.controlnet.ae2federation.test.automation.AutomationAuthorityObservation;
import space.controlnet.ae2federation.test.automation.AutomationNativeObservation;
import space.controlnet.ae2federation.test.crafting.NativeCraftingRequester;
import space.controlnet.ae2federation.test.crafting.ProviderCraftingOrder;
import space.controlnet.ae2federation.test.mixed.MixedFactoryEvidence;
import space.controlnet.ae2federation.test.mixed.MixedFactoryProfile;
import space.controlnet.ae2federation.test.mixed.MixedFactoryScene;
import space.controlnet.ae2federation.test.mixed.MixedFactoryBenchmarkState;

@PrefixGameTestTemplate(false)
public final class MixedFactoryGameTests {
    private MixedFactoryGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 6000, required = true, manualOnly = true)
    public static void mixedBenchmarkSmall(GameTestHelper helper) {
        var profile = MixedFactoryProfile.load();
        profile.verifyAxisVariations();
        helper.assertTrue(!Boolean.getBoolean("ae2federation.benchmarkEmpty")
                && profile.measuredIterations() > 0, "Mixed benchmark must request nonempty work");
        var benchmark = new MixedFactoryBenchmarkState(helper, profile);
        helper.succeedWhen(() -> {
            helper.assertTrue(benchmark.tick(), "Waiting for isolated native mixed runs: " + benchmark.progress());
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 2000, required = true, manualOnly = true)
    public static void mixedOverloadBackpressure(GameTestHelper helper) {
        var state = new OverloadState(helper);
        helper.succeedWhen(() -> state.tick(helper));
    }

    private static final class OverloadState {
        private final NativeAutomationFixture fixture;
        private NativeCraftingRequester requester;
        private ProviderCraftingOrder first;
        private ProviderCraftingOrder second;
        private int initialRejections;
        private int retries;
        private long peakInFlight;
        private long peakRegistry;
        private long sourceInitial;
        private long firstDeferredTick = -1;
        private long capacityReleaseTick = -1;
        private long retryTick = -1;
        private long firstCompletionTick = -1;
        private long secondSubmissionTick = -1;
        private long secondCompletionTick = -1;
        private int stage;

        private OverloadState(GameTestHelper helper) { fixture = new NativeAutomationFixture(helper); }

        private void tick(GameTestHelper helper) {
            helper.assertTrue(fixture.ready(), "Waiting for native overload topology");
            if (stage == 0) {
                fixture.binding().insertMaterials(8);
                sourceInitial = fixture.binding().physicalMaterialAmount();
                requester = new NativeCraftingRequester(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)),
                        fixture.binding().sourcePhysicalStorage(), fixture.binding().key().providerNetworkId(), null,
                        false);
                requester.connect(fixture.binding().sourceChest().getMainNode().getNode());
                AutomationNativeObservation.begin("mixedoverloadbackpressure");
                AutomationAuthorityObservation.begin("mixedoverloadbackpressure", fixture);
                AutomationAuthorityObservation.authorizeInterface(requester);
                stage = 1;
                helper.assertTrue(false, "Waiting for bounded native requester");
            }
            if (stage == 1) {
                helper.assertTrue(requester.isReady(fixture.binding().sourceChest().getMainNode().getNode()),
                        "Native bounded requester must join the provider Grid");
                var grid = fixture.binding().providerGrid();
                first = ProviderCraftingOrder.begin(helper.getLevel(), grid, requester.getActionableNode(),
                        AEItemKey.of(Items.STICK), 4);
                second = ProviderCraftingOrder.begin(helper.getLevel(), grid, requester.getActionableNode(),
                        AEItemKey.of(Items.STICK), 4);
                stage = 2;
            }
            if (stage == 2) {
                helper.assertTrue(first.completedPlan().isPresent() && second.completedPlan().isPresent(),
                        "Waiting for native overload plans");
                helper.assertTrue(first.submitTracked(requester), "First bounded order must submit");
                fixture.binding().suspendCpu();
                helper.assertFalse(second.submitTracked(requester), "Occupied native tracker slot must reject overload");
                initialRejections++;
                firstDeferredTick = helper.getLevel().getGameTime();
                helper.assertValueEqual(requester.uniqueNativeJobCount(), 1,
                        "Overload must retain exactly one native task");
                fixture.binding().resumeCpu();
                stage = 3;
            }
            peakInFlight = Math.max(peakInFlight, fixture.binding().busyCpuCount());
            // The jobs AE2 tracks for the requester: one at a time, whatever was asked for.
            peakRegistry = Math.max(peakRegistry, requester.getRequestedJobs().size());
            if (stage == 3) {
                helper.assertTrue(requester.observedDone() && requester.acceptedAmount() == 4
                                && requester.activeLink() == null,
                        "Waiting for first native order completion and tracker-slot retirement");
                firstCompletionTick = helper.getLevel().getGameTime();
                capacityReleaseTick = firstCompletionTick;
                helper.assertTrue(second.submitTracked(requester),
                        "Deferred second order must submit after native capacity is released");
                retries++;
                retryTick = helper.getLevel().getGameTime();
                secondSubmissionTick = retryTick;
                stage = 4;
                helper.assertTrue(false, "Waiting for deferred native order completion");
            }
            helper.assertTrue(requester.observedDone() && requester.acceptedAmount() == 8,
                    "Deferred second native order must complete exactly once");
            secondCompletionTick = helper.getLevel().getGameTime();
            var nativeWork = AutomationNativeObservation.snapshot();
            helper.assertValueEqual(nativeWork.trackerSubmissions(), 2,
                    "Exactly two native submissions must serve two logical orders");
            helper.assertValueEqual(nativeWork.jobIds().size(), 2,
                    "Each logical order must own a distinct native job");
            var details = new java.util.TreeMap<String, String>();
            var amountPerOrder = 4L;
            var sourceFinal = fixture.binding().physicalMaterialAmount();
            var submissions = nativeWork.trackerSubmissions();
            details.put("logicalCompletions", Long.toString(requester.acceptedAmount() / amountPerOrder));
            details.put("initialDeferred", Integer.toString(initialRejections));
            details.put("retries", Integer.toString(retries));
            details.put("nativeSubmissions", Integer.toString(nativeWork.trackerSubmissions()));
            details.put("uniqueJobIds", Integer.toString(nativeWork.jobIds().size()));
            details.put("jobIds", nativeWork.joinedJobs());
            details.put("peakInFlight", Long.toString(peakInFlight));
            details.put("hardPeakBound", Integer.toString(fixture.binding().sourceService().getCpus().size()));
            details.put("queueGrowth", Long.toString(Math.max(0, peakRegistry - peakInFlight)));
            details.put("silentDrops", Long.toString(requester.acceptedAmount() / amountPerOrder - submissions));
            details.put("sourceInitial", Long.toString(sourceInitial));
            details.put("sourceConsumed", Long.toString(sourceInitial - sourceFinal));
            details.put("sourceFinal", Long.toString(sourceFinal));
            details.put("resultAccepted", Long.toString(requester.acceptedAmount()));
            details.put("resultPhysical", Long.toString(fixture.binding().physicalOutputAmount()));
            details.put("completionWindowBound", Long.toString(secondCompletionTick - firstDeferredTick));
            details.put("firstDeferredTick", Long.toString(firstDeferredTick));
            details.put("capacityReleaseTick", Long.toString(capacityReleaseTick));
            details.put("retryTick", Long.toString(retryTick));
            details.put("firstCompletionTick", Long.toString(firstCompletionTick));
            details.put("secondSubmissionTick", Long.toString(secondSubmissionTick));
            details.put("secondCompletionTick", Long.toString(secondCompletionTick));
            details.put("registryPeak", Long.toString(peakRegistry));
            details.put("registryFinal", Integer.toString(requester.getRequestedJobs().size()));
            details.put("linkOwnersFinal", Integer.toString(requester.activeLink() == null ? 0 : 1));
            MixedFactoryEvidence.writeNegative("mixedoverloadbackpressure", details.size(), details);
            AutomationNativeObservation.close();
            AutomationAuthorityObservation.close();
            requester.close();
            fixture.close();
        }
    }
}
