package space.controlnet.ae2federation.test;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.crafting.terminal.NativeTerminalAdapter;
import space.controlnet.ae2federation.test.automation.NativeAutomationFixture;
import space.controlnet.ae2federation.test.automation.AutomationAuthorityObservation;
import space.controlnet.ae2federation.test.automation.AutomationNativeObservation;
import space.controlnet.ae2federation.test.crafting.NativeCraftingRequester;
import space.controlnet.ae2federation.test.mixed.MixedFactoryEvidence;
import space.controlnet.ae2federation.test.mixed.MixedFactoryObservation;
import space.controlnet.ae2federation.test.mixed.MixedFactoryProfile;
import space.controlnet.ae2federation.test.mixed.MixedFactoryScene;
import space.controlnet.ae2federation.test.mixed.MixedFactoryBenchmarkState;
import space.controlnet.ae2federation.test.crafting.TerminalNativeObservation;
import space.controlnet.ae2federation.test.processing.ProcessingNativeObservation;
import space.controlnet.ae2federation.crafting.binding.CraftingBindingService;

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
            timeoutTicks = 1000, required = true, manualOnly = true)
    public static void mixedRejectEmptyOrders(GameTestHelper helper) {
        var fixture = new NativeAutomationFixture(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for native mixed empty-order authority");
            AutomationNativeObservation.begin("mixedrejectemptyorders");
            AutomationAuthorityObservation.begin("mixedrejectemptyorders", fixture);
            TerminalNativeObservation.begin("mixedrejectemptyorders");
            ProcessingNativeObservation.reset();
            var before = AutomationNativeObservation.snapshot();
            var terminalBefore = TerminalNativeObservation.snapshot();
            var processingBefore = ProcessingNativeObservation.snapshot();
            var registry = CraftingBindingService.get(helper.getLevel());
            var requestCountBefore = registry.nativeRequestCount();
            var linkCountBefore = registry.nativeLinkOwnerCount();
            var inFlightBefore = fixture.binding().busyCpuCount();
            var sourceBefore = fixture.physicalSourceTotal();
            var consumerBefore = fixture.physicalConsumerTotal();
            var terminal = NativeTerminalAdapter.discover(helper.getLevel(), fixture.binding().consumerGrid(),
                    fixture.binding().key().providerNetworkId(), IActionSource.empty()).orElseThrow();
            try {
                terminal.begin(AEItemKey.of(Items.STICK), 0);
                throw new IllegalStateException("Native terminal accepted an empty mixed order");
            } catch (IllegalArgumentException expected) {
                helper.assertValueEqual(expected.getMessage(), "Native terminal amount must be positive",
                        "Native terminal must reject empty orders at its boundary");
            }
            terminal.close();
            var after = AutomationNativeObservation.snapshot();
            var terminalAfter = TerminalNativeObservation.snapshot();
            var processingAfter = ProcessingNativeObservation.snapshot();
            var sourceAfter = fixture.physicalSourceTotal();
            var consumerAfter = fixture.physicalConsumerTotal();
            var requestCountAfter = registry.nativeRequestCount();
            var linkCountAfter = registry.nativeLinkOwnerCount();
            var inFlightAfter = fixture.binding().busyCpuCount();
            var details = new java.util.TreeMap<String, String>();
            details.put("rejected", "true");
            details.put("amount", "0");
            details.put("queueGrowth", Integer.toString(requestCountAfter - requestCountBefore));
            details.put("plannerDelta", Integer.toString(terminalAfter.beginCalls() - terminalBefore.beginCalls()));
            details.put("trackerCallDelta", Integer.toString(after.trackerCalls() - before.trackerCalls()));
            details.put("nativeSubmissionDelta", Integer.toString(
                    after.trackerSubmissions() - before.trackerSubmissions()));
            details.put("projectionCallDelta", Integer.toString(
                    after.storageInsertCalls() + after.storageExtractCalls()
                            - before.storageInsertCalls() - before.storageExtractCalls()));
            details.put("projectionQuantityDelta", Long.toString(
                    after.storageInserted() + after.storageExtracted()
                            - before.storageInserted() - before.storageExtracted()));
            details.put("handlerCallDelta", Integer.toString(processingAfter.size() - processingBefore.size()));
            details.put("handlerQuantityDelta", Long.toString(Math.abs(sourceAfter - sourceBefore)));
            details.put("interfaceWorkDelta", Long.toString(Math.abs(consumerAfter - consumerBefore)));
            details.put("busWorkDelta", Integer.toString(after.importWork() + after.exportWork()
                    - before.importWork() - before.exportWork()));
            details.put("inFlightDelta", Long.toString(inFlightAfter - inFlightBefore));
            details.put("waitingDelta", Integer.toString(linkCountAfter - linkCountBefore));
            details.put("sourceInventoryDelta", Long.toString(sourceAfter - sourceBefore));
            details.put("consumerInventoryDelta", Long.toString(consumerAfter - consumerBefore));
            MixedFactoryEvidence.writeNegative("mixedrejectemptyorders", details.size(), details);
            AutomationNativeObservation.close();
            AutomationAuthorityObservation.close();
            TerminalNativeObservation.close();
            fixture.close();
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
        private space.controlnet.ae2federation.crafting.terminal.NativeTerminalRequest first;
        private space.controlnet.ae2federation.crafting.terminal.NativeTerminalRequest second;
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
                var terminal = NativeTerminalAdapter.discover(helper.getLevel(), fixture.binding().consumerGrid(),
                        fixture.binding().key().providerNetworkId(), IActionSource.empty()).orElseThrow();
                first = terminal.begin(AEItemKey.of(Items.STICK), 4).orElseThrow();
                second = terminal.begin(AEItemKey.of(Items.STICK), 4).orElseThrow();
                terminal.close();
                stage = 2;
            }
            if (stage == 2) {
                helper.assertTrue(first.completedPlan().isPresent() && second.completedPlan().isPresent(),
                        "Waiting for native overload plans");
                helper.assertTrue(first.submitTracked(0, requester, requester::handleCrafting).isPresent(),
                        "First bounded order must submit");
                fixture.binding().suspendCpu();
                helper.assertTrue(second.submitTracked(0, requester, requester::handleCrafting).isEmpty(),
                        "Occupied native tracker slot must reject overload");
                initialRejections++;
                firstDeferredTick = helper.getLevel().getGameTime();
                helper.assertValueEqual(requester.uniqueNativeJobCount(), 1,
                        "Overload must retain exactly one native task");
                fixture.binding().resumeCpu();
                stage = 3;
            }
            peakInFlight = Math.max(peakInFlight, fixture.binding().busyCpuCount());
            var registry = CraftingBindingService.get(helper.getLevel());
            peakRegistry = Math.max(peakRegistry, registry.nativeRequestCount());
            if (stage == 3) {
                helper.assertTrue(requester.observedDone() && requester.acceptedAmount() == 4
                                && requester.activeLink() == null,
                        "Waiting for first native order completion and tracker-slot retirement");
                firstCompletionTick = helper.getLevel().getGameTime();
                capacityReleaseTick = firstCompletionTick;
                helper.assertTrue(second.submitTracked(0, requester, requester::handleCrafting).isPresent(),
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
            details.put("registryFinal", Integer.toString(registry.nativeRequestCount()));
            details.put("linkOwnersFinal", Integer.toString(registry.nativeLinkOwnerCount()));
            MixedFactoryEvidence.writeNegative("mixedoverloadbackpressure", details.size(), details);
            AutomationNativeObservation.close();
            AutomationAuthorityObservation.close();
            requester.close();
            fixture.close();
        }
    }
}
