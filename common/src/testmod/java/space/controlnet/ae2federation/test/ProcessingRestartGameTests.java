package space.controlnet.ae2federation.test;

import static space.controlnet.ae2federation.test.processing.ProcessingRegressionFixtures.item;
import static space.controlnet.ae2federation.test.processing.ProcessingRegressionFixtures.sendList;

import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.stacks.AEItemKey;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.test.mixin.PatternProviderLogicReturnAccess;
import space.controlnet.ae2federation.test.processing.NativeProviderLaneFixtures;
import space.controlnet.ae2federation.test.processing.ProcessingNativeObservation;
import space.controlnet.ae2federation.test.processing.ProcessingRegressionEvidence;

@PrefixGameTestTemplate(false)
public final class ProcessingRestartGameTests {
    private ProcessingRestartGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void processingDisconnectRestart(GameTestHelper helper) {
        ProcessingNativeObservation.reset();
        var state = new RestartState(new NativeProviderLaneFixtures(helper,
                NativeProviderLaneFixtures.sharedPatternAssignments()));
        helper.succeedWhen(() -> {
            switch (state.phase) {
                case 0 -> saveAndDisposeOriginal(helper, state);
                case 1 -> loadDistinctOwner(helper, state);
                case 2 -> observeFirstDrain(helper, state);
                case 3 -> verifyNoSecondDrain(helper, state);
                default -> throw new IllegalStateException("Unknown restart phase " + state.phase);
            }
        });
    }

    private static void saveAndDisposeOriginal(GameTestHelper helper, RestartState state) {
        var fixture = state.fixture;
        helper.assertTrue(fixture.connectEnergy(), "Waiting for original native Provider owner");
        fixture.register();
        fixture.leaveOneSharedTargetSlot();
        fixture.installPattern(0, List.of(item(Items.COBBLESTONE, 1), item(Items.DIRT, 1)),
                List.of(item(Items.DIAMOND, 4), item(Items.GOLD_INGOT, 2)));
        var lane = fixture.lane(0);
        lane.getConfigManager().putSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_UNTIL_RESULT);
        helper.assertTrue(fixture.pushInputs(0, 0, List.of(item(Items.COBBLESTONE, 1), item(Items.DIRT, 1))),
                "Original native owner must accept a partial-send batch");
        ((PatternProviderLogicReturnAccess) (Object) lane).ae2federation_test$onStackReturned(item(Items.DIAMOND, 2));
        lane.getReturnInv().setStack(0, item(Items.DIAMOND, 2));
        lane.getReturnInv().setStack(1, item(Items.GOLD_INGOT, 2));
        fixture.removeConfiguredTarget();
        state.originalOwner = ProcessingRegressionEvidence.identity(lane);
        state.originalNode = ProcessingRegressionEvidence.identity(fixture.managedNode());
        state.originalSendOwner = ProcessingRegressionEvidence.identity(
                ((PatternProviderLogicReturnAccess) (Object) lane).ae2federation_test$getSendList());
        state.originalReturnOwner = ProcessingRegressionEvidence.identity(lane.getReturnInv());
        state.pendingBefore = ProcessingNativeObservation.genericStacks(sendList(lane));
        state.returnsBefore = ProcessingNativeObservation.returnInventory(lane);
        ProcessingNativeObservation.recordFact("lifecycle-generation", lane, "generation=1");
        ProcessingNativeObservation.recordLifecycle("managed-node", fixture.managedNode());
        fixture.composition().writeToNBT(state.saved, helper.getLevel().registryAccess());
        ProcessingNativeObservation.recordFact("serialized-state", lane,
                "gridReference=" + state.saved.toString().toLowerCase(java.util.Locale.ROOT).contains("grid"));
        ProcessingNativeObservation.recordLifecycle("owner-dispose", lane);
        fixture.close();
        state.fixture = new NativeProviderLaneFixtures(helper, NativeProviderLaneFixtures.sharedPatternAssignments());
        state.fixture.removeConfiguredTarget();
        state.fixture.composition().readFromNBT(state.saved, helper.getLevel().registryAccess());
        state.phase = 1;
        helper.assertTrue(false, "Waiting for reconstructed native Provider owner");
    }

    private static void loadDistinctOwner(GameTestHelper helper, RestartState state) {
        var fixture = state.fixture;
        helper.assertTrue(fixture.connectEnergy(), "Waiting for reconstructed native Provider owner");
        fixture.register();
        var lane = fixture.lane(0);
        state.restoredOwner = ProcessingRegressionEvidence.identity(lane);
        state.restoredNode = ProcessingRegressionEvidence.identity(fixture.managedNode());
        state.restoredSendOwner = ProcessingRegressionEvidence.identity(
                ((PatternProviderLogicReturnAccess) (Object) lane).ae2federation_test$getSendList());
        state.restoredReturnOwner = ProcessingRegressionEvidence.identity(lane.getReturnInv());
        state.pendingAfterLoad = ProcessingNativeObservation.genericStacks(sendList(lane));
        state.returnsAfterLoad = ProcessingNativeObservation.returnInventory(lane);
        ProcessingNativeObservation.recordFact("lifecycle-generation", lane, "generation=2");
        ProcessingNativeObservation.recordLifecycle("managed-node", fixture.managedNode());
        helper.assertTrue(!state.originalOwner.equals(state.restoredOwner),
                "Restart must construct a distinct PatternProviderLogic owner");
        helper.assertTrue(!state.originalNode.equals(state.restoredNode),
                "Restart must construct a distinct managed-node owner");
        helper.assertTrue(!state.originalSendOwner.equals(state.restoredSendOwner),
                "Restart must construct a distinct sendList collection");
        helper.assertTrue(!state.originalReturnOwner.equals(state.restoredReturnOwner),
                "Restart must construct a distinct return inventory");
        helper.assertValueEqual(state.pendingAfterLoad, state.pendingBefore,
                "Persisted send responsibility must survive owner reconstruction");
        helper.assertValueEqual(state.returnsAfterLoad, state.returnsBefore,
                "Persisted return responsibility must survive owner reconstruction");
        helper.assertValueEqual(lane.getUnlockStack().amount(), 2L,
                "Persisted primary unlock remainder must survive owner reconstruction");
        helper.assertValueEqual(lane.getCraftingLockedReason(), LockCraftingMode.LOCK_UNTIL_RESULT,
                "Persisted result lock must survive owner reconstruction");
        helper.assertValueEqual(ProcessingNativeObservation.count("nbt-write"), 3L,
                "Every original native Lane must execute real NBT write");
        helper.assertValueEqual(ProcessingNativeObservation.count("nbt-read"), 3L,
                "Every reconstructed native Lane must execute real NBT read");
        fixture.restoreConfiguredTarget();
        state.phase = 2;
        helper.assertTrue(false, "Waiting for restored responsibility to drain");
    }

    private static void observeFirstDrain(GameTestHelper helper, RestartState state) {
        var lane = state.fixture.lane(0);
        helper.assertTrue(!lane.isBusy(), "Reconnected target must drain restored native responsibility");
        helper.assertValueEqual(state.fixture.targetItemCount(Items.DIRT), 1,
                "Restored remainder must transfer exactly once after reconnect");
        ProcessingNativeObservation.recordTarget("reconnect-drain", lane,
                Long.toString(state.fixture.targetItemCount(Items.DIRT)));
        state.phase = 3;
        helper.assertTrue(false, "Waiting one native tick to exclude a second drain");
    }

    private static void verifyNoSecondDrain(GameTestHelper helper, RestartState state) {
        var fixture = state.fixture;
        var lane = fixture.lane(0);
        helper.assertValueEqual(fixture.targetItemCount(Items.DIRT), 1,
                "A second native tick must not replay restored responsibility");
        helper.assertTrue(!lane.isBusy(), "Drained restored owner must retain no replayable send responsibility");
        ProcessingNativeObservation.recordTarget("second-drain", lane,
                Long.toString(fixture.targetItemCount(Items.DIRT)));
        ProcessingRegressionEvidence.write("processingdisconnectrestart", 16, Map.ofEntries(
                Map.entry("lifecycleGenerationBefore", "1"), Map.entry("lifecycleGenerationAfter", "2"),
                Map.entry("originalOwnerDisposed", "true"), Map.entry("originalLogicIdentity", state.originalOwner),
                Map.entry("restoredLogicIdentity", state.restoredOwner), Map.entry("originalNodeIdentity", state.originalNode),
                Map.entry("restoredNodeIdentity", state.restoredNode),
                Map.entry("sendListIdentityBefore", state.originalSendOwner),
                Map.entry("sendListIdentityAfter", state.restoredSendOwner),
                Map.entry("returnInventoryIdentityBefore", state.originalReturnOwner),
                Map.entry("returnInventoryIdentityAfter", state.restoredReturnOwner),
                Map.entry("pendingInputBefore", state.pendingBefore),
                Map.entry("pendingInputAfterLoad", state.pendingAfterLoad),
                Map.entry("returnInventoryBefore", state.returnsBefore),
                Map.entry("returnInventoryAfterLoad", state.returnsAfterLoad),
                Map.entry("unlockRemainderAfterLoad", "minecraft:diamond:2"),
                Map.entry("lockAfterLoad", "LOCK_UNTIL_RESULT"), Map.entry("reconnectDrainCount", "1"),
                Map.entry("secondDrainCount", "0"), Map.entry("duplicateAfterReconnect", "false"),
                Map.entry("nbtWriteObservations", Long.toString(ProcessingNativeObservation.count("nbt-write"))),
                Map.entry("nbtReadObservations", Long.toString(ProcessingNativeObservation.count("nbt-read")))));
        fixture.close();
    }

    private static final class RestartState {
        private NativeProviderLaneFixtures fixture;
        private final CompoundTag saved = new CompoundTag();
        private int phase;
        private String originalOwner;
        private String restoredOwner;
        private String originalNode;
        private String restoredNode;
        private String originalSendOwner;
        private String restoredSendOwner;
        private String originalReturnOwner;
        private String restoredReturnOwner;
        private String pendingBefore;
        private String pendingAfterLoad;
        private String returnsBefore;
        private String returnsAfterLoad;

        private RestartState(NativeProviderLaneFixtures fixture) {
            this.fixture = fixture;
        }
    }
}
