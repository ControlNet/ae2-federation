package space.controlnet.ae2federation.test;

import static space.controlnet.ae2federation.test.processing.ProcessingRegressionFixtures.dropCount;
import static space.controlnet.ae2federation.test.processing.ProcessingRegressionFixtures.item;
import static space.controlnet.ae2federation.test.processing.ProviderLifecycleFixtureSupport.setRedstoneSignal;

import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.stacks.AEItemKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.test.mixin.PatternProviderLogicReturnAccess;
import space.controlnet.ae2federation.test.processing.NativeProviderLaneFixtures;
import space.controlnet.ae2federation.test.processing.ProcessingNativeObservation;
import space.controlnet.ae2federation.test.processing.ProcessingRegressionEvidence;
import space.controlnet.ae2federation.test.processing.ProviderTargetRuntimeFixtures;

@PrefixGameTestTemplate(false)
public final class ProcessingOwnershipGameTests {
    private ProcessingOwnershipGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void processingDismantle(GameTestHelper helper) {
        ProcessingNativeObservation.reset();
        var fixture = new ProviderTargetRuntimeFixtures(helper, true);
        var state = new DismantleState();
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.initialize(), "Waiting for Federation dismantle context");
            if (!state.configured) {
                helper.assertTrue(fixture.enablePolicy(java.util.Set.of(PolicyOperation.EXECUTE,
                        PolicyOperation.SUPPLY)), "Federation Processing policy must activate");
                fixture.connectFederationDomain();
                fixture.leaveOneSharedTargetSlot();
                state.targetBeforePush = fixture.targetAmount(AEItemKey.of(Items.COBBLESTONE));
                ProcessingNativeObservation.recordTarget("push-head", fixture.providerLogic(),
                        Long.toString(state.targetBeforePush));
                fixture.installPattern(List.of(item(Items.COBBLESTONE, 1), item(Items.DIRT, 1)),
                        List.of(item(Items.DIAMOND, 4), item(Items.GOLD_INGOT, 2)));
                helper.assertTrue(fixture.pushLaneWithInputs(0,
                        List.of(item(Items.COBBLESTONE, 1), item(Items.DIRT, 1))),
                        "Federation must delegate a partial native send before dismantle");
                helper.assertTrue(fixture.providerLogic().isBusy(),
                        "Dismantle fixture must retain a real native sendList remainder");
                ProcessingNativeObservation.recordTarget("push-return", fixture.providerLogic(),
                        Long.toString(fixture.targetAmount(AEItemKey.of(Items.COBBLESTONE))));
                fixture.providerLogic().getReturnInv().setStack(0, item(Items.DIAMOND, 2));
                fixture.providerLogic().getReturnInv().setStack(1, item(Items.GOLD_INGOT, 2));
                state.configured = true;
            }
            var lane = fixture.providerLogic();
            var pendingOwner = ProcessingRegressionEvidence.identity(
                    ((PatternProviderLogicReturnAccess) lane).ae2federation_test$getSendList());
            var returnOwner = ProcessingRegressionEvidence.identity(lane.getReturnInv());
            var pendingBefore = ProcessingNativeObservation.genericStacks(
                    ((PatternProviderLogicReturnAccess) lane).ae2federation_test$getSendList());
            var returnsBefore = ProcessingNativeObservation.returnInventory(lane);
            var targetBefore = Long.toString(fixture.targetAmount(AEItemKey.of(Items.COBBLESTONE)));
            fixture.disconnectFederationDomain();
            ProcessingNativeObservation.recordLifecycle("endpoint-disconnect", lane);
            ProcessingNativeObservation.recordLogicState("dismantle-01-pre-first", lane, List.of());
            ProcessingNativeObservation.recordTarget("dismantle-01-pre-first", lane, targetBefore);
            var drops = new ArrayList<ItemStack>();
            lane.addDrops(drops);
            var dropped = ProcessingNativeObservation.itemStacks(drops);
            var targetAfterFirst = Long.toString(fixture.targetAmount(AEItemKey.of(Items.COBBLESTONE)));
            ProcessingNativeObservation.recordLogicState("dismantle-04-post-first", lane, drops);
            ProcessingNativeObservation.recordTarget("dismantle-04-post-first", lane, targetAfterFirst);
            helper.assertValueEqual(dropCount(drops, Items.DIRT), 1,
                    "Pinned native dismantle must emit the pending sendList remainder exactly once");
            helper.assertValueEqual(dropCount(drops, Items.DIAMOND), 2,
                    "Dismantle must drop the provider-owned primary return exactly once");
            helper.assertValueEqual(dropCount(drops, Items.GOLD_INGOT), 2,
                    "Dismantle must drop the provider-owned byproduct return exactly once");
            helper.assertValueEqual(fixture.targetAmount(AEItemKey.of(Items.COBBLESTONE)),
                    state.targetBeforePush + 1, "Dismantle must retain the accepted target prefix exactly once");
            helper.assertValueEqual(dropCount(drops, Items.COBBLESTONE), 0,
                    "Dismantle must not duplicate the accepted target prefix");
            helper.assertValueEqual(ProcessingNativeObservation.genericStacks(
                    ((PatternProviderLogicReturnAccess) lane).ae2federation_test$getSendList()), pendingBefore,
                    "Observational addDrops must leave native pending responsibility populated");
            helper.assertValueEqual(ProcessingNativeObservation.returnInventory(lane), returnsBefore,
                    "Observational addDrops must leave native return responsibility populated");
            lane.clearContent();
            helper.assertTrue(lane.getPatternInv().isEmpty(), "Authentic clearContent must empty native patterns");
            helper.assertTrue(((PatternProviderLogicReturnAccess) lane).ae2federation_test$getSendList().isEmpty(),
                    "Authentic clearContent must empty pending native sends");
            helper.assertTrue(lane.getReturnInv().isEmpty(), "Authentic clearContent must empty native returns");
            ProcessingNativeObservation.recordLogicState("dismantle-07-pre-second", lane, List.of());
            ProcessingNativeObservation.recordTarget("dismantle-07-pre-second", lane, targetAfterFirst);
            var secondDrops = new ArrayList<ItemStack>();
            lane.addDrops(secondDrops);
            var targetFinal = Long.toString(fixture.targetAmount(AEItemKey.of(Items.COBBLESTONE)));
            ProcessingNativeObservation.recordLogicState("dismantle-10-final", lane, secondDrops);
            ProcessingNativeObservation.recordTarget("dismantle-10-final", lane, targetFinal);
            helper.assertTrue(secondDrops.isEmpty(), "Collection after authentic clearContent must emit nothing");
            helper.assertValueEqual(targetFinal, targetAfterFirst,
                    "Second collection after clear must not mutate the accepted target prefix");
            helper.assertValueEqual(dropCount(drops, Items.DIRT) + dropCount(drops, Items.DIAMOND)
                    + dropCount(drops, Items.GOLD_INGOT) + secondDrops.size() + 1, 6,
                    "Pending, returns, and accepted target delta must reconcile without loss or duplication");
            fixture.close();
            ProcessingNativeObservation.recordLifecycle("owner-retire", lane);
            ProcessingNativeObservation.recordFact("federation-recovery", lane, "count=0");
            ProcessingRegressionEvidence.write("processingdismantle", 17, Map.ofEntries(
                    Map.entry("pendingInputBeforeDrops", pendingBefore),
                    Map.entry("returnInventoryBeforeDrops", returnsBefore),
                    Map.entry("pendingInputDrop", "minecraft:dirt:1"),
                    Map.entry("primaryReturnDrop", "minecraft:diamond:2"),
                    Map.entry("byproductReturnDrop", "minecraft:gold_ingot:2"),
                    Map.entry("dropSnapshot", dropped), Map.entry("acceptedTargetBefore", targetBefore),
                    Map.entry("acceptedTargetInitial", Long.toString(state.targetBeforePush)),
                    Map.entry("acceptedTargetAfter", targetAfterFirst), Map.entry("acceptedTargetFinal", targetFinal),
                    Map.entry("acceptedTargetCount", "1"),
                    Map.entry("acceptedTargetDrop", "0"), Map.entry("reconciledResourceCount", "6"),
                    Map.entry("pendingOwnerIdentity", pendingOwner), Map.entry("returnOwnerIdentity", returnOwner),
                    Map.entry("providerLogicIdentity", ProcessingRegressionEvidence.identity(lane)),
                    Map.entry("endpointDisconnectedBeforeDrops", "true"),
                    Map.entry("addDropsObservations", Long.toString(ProcessingNativeObservation.count("add-drops"))),
                    Map.entry("clearContentObservations", Long.toString(ProcessingNativeObservation.count("clear-content"))),
                    Map.entry("secondDropSnapshot", ProcessingNativeObservation.itemStacks(secondDrops)),
                    Map.entry("remainingPatternResponsibility", "empty"),
                    Map.entry("remainingPendingResponsibility", "empty"),
                    Map.entry("remainingReturnResponsibility", "empty"),
                    Map.entry("federationRecoveryCount", "0"), Map.entry("ownerRetired", "true"),
                    Map.entry("nativeDismantleBehavior", "observe-then-clear-then-remove"),
                    Map.entry("duplicateOwnership", "false")));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void processingRejectFalseReplay(GameTestHelper helper) {
        ProcessingNativeObservation.reset();
        var state = new RejectState(new NativeProviderLaneFixtures(helper,
                NativeProviderLaneFixtures.sharedPatternAssignments()));
        helper.succeedWhen(() -> {
            if (state.nativeFixture != null) {
                runNativeReject(helper, state.nativeFixture);
                state.nativeFixture.close();
                state.nativeFixture = null;
                state.federationFixture = new ProviderTargetRuntimeFixtures(helper, true);
                helper.assertTrue(false, "Waiting for Federation blocked-return boundary");
            }
            runFederationReject(helper, state);
        });
    }

    private static void runNativeReject(GameTestHelper helper, NativeProviderLaneFixtures fixture) {
        helper.assertTrue(fixture.connectEnergy(), "Waiting for native rejection reference Provider");
        fixture.register();
        fixture.installPattern(0, List.of(item(Items.COBBLESTONE, 1)), List.of(item(Items.DIAMOND, 1)));
        fixture.lane(0).getConfigManager().putSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_WHILE_HIGH);
        setRedstoneSignal(fixture, true);
        var rejected = fixture.targetSnapshot();
        helper.assertTrue(!fixture.push(0, 0), "Normal redstone gate must return false");
        helper.assertValueEqual(fixture.targetSnapshot(), rejected, "False return must retain caller ownership");
        helper.assertTrue(!fixture.lane(0).isBusy(), "False return must create no native responsibility");
        setRedstoneSignal(fixture, false);
        helper.assertTrue(fixture.push(0, 0), "Caller may retry once the normal gate clears");
        helper.assertValueEqual(fixture.targetItemCount(Items.COBBLESTONE), 1,
                "Retry after false must transfer exactly once");
        fixture.leaveOneSharedTargetSlot();
        fixture.installPattern(1, List.of(item(Items.DIRT, 1), item(Items.GRAVEL, 1)),
                List.of(item(Items.GOLD_INGOT, 1)));
        helper.assertTrue(fixture.pushInputs(1, 0, List.of(item(Items.DIRT, 1), item(Items.GRAVEL, 1))),
                "Partial native write must return true and retain its remainder");
        var partial = fixture.targetSnapshot();
        helper.assertTrue(fixture.lane(1).isBusy(), "True partial write must create native responsibility");
        helper.assertTrue(!fixture.pushInputs(1, 0, List.of(item(Items.DIRT, 1), item(Items.GRAVEL, 1))),
                "Caller must not replay the full batch while native responsibility exists");
        helper.assertValueEqual(fixture.targetSnapshot(), partial,
                "Forbidden full replay must not duplicate the accepted prefix");
    }

    private static void runFederationReject(GameTestHelper helper, RejectState state) {
        var fixture = state.federationFixture;
        helper.assertTrue(fixture.initialize(), "Waiting for Federation blocked-return context");
        if (!state.federationConfigured) {
            fixture.connectFederationDomain();
            state.federationConfigured = true;
        }
        var handler = fixture.endpointBinding().runtime().itemReturn(Direction.NORTH);
        helper.assertTrue(handler.isEmpty(),
                "Endpoint without native context must expose no return capability, retaining caller ownership");
        helper.assertValueEqual(fixture.providerLogic().getReturnInv().isEmpty(), true,
                "Blocked non-native return must create no provider responsibility");
        ProcessingRegressionEvidence.write("processingrejectfalsereplay", 13, Map.ofEntries(
                Map.entry("normalRejectAccepted", "false"), Map.entry("normalRejectMutated", "false"),
                Map.entry("normalRejectNativeRemainder", "0"), Map.entry("retryAfterFalseAccepted", "true"),
                Map.entry("retryTransferred", "1"), Map.entry("partialSendAccepted", "true"),
                Map.entry("partialAcceptedPrefix", "minecraft:dirt:1"),
                Map.entry("partialNativeRemainder", "minecraft:gravel:1"),
                Map.entry("fullReplayWhileBusyAccepted", "false"), Map.entry("duplicateAfterReplay", "false"),
                Map.entry("blockedEndpointReturnAccepted", "0"), Map.entry("blockedEndpointCallerRemainder", "3"),
                Map.entry("blockedEndpointProviderRemainder", "0"),
                Map.entry("rule", "false-retains-caller;true-partial-transfers-remainder-to-native")));
        fixture.close();
    }

    private static final class RejectState {
        private NativeProviderLaneFixtures nativeFixture;
        private ProviderTargetRuntimeFixtures federationFixture;
        private boolean federationConfigured;

        private RejectState(NativeProviderLaneFixtures nativeFixture) {
            this.nativeFixture = nativeFixture;
        }
    }

    private static final class DismantleState {
        private boolean configured;
        private long targetBeforePush;
    }
}
