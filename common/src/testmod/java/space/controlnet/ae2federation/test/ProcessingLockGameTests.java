package space.controlnet.ae2federation.test;

import static space.controlnet.ae2federation.test.processing.ProcessingRegressionFixtures.injectReturns;
import static space.controlnet.ae2federation.test.processing.ProcessingRegressionFixtures.item;
import static space.controlnet.ae2federation.test.processing.ProviderLifecycleFixtureSupport.setRedstoneSignal;

import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.helpers.externalstorage.GenericStackInv;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.test.processing.NativeProviderLaneFixtures;
import space.controlnet.ae2federation.test.processing.ProcessingNativeObservation;
import space.controlnet.ae2federation.test.processing.ProcessingRegressionEvidence;

@PrefixGameTestTemplate(false)
public final class ProcessingLockGameTests {
    private ProcessingLockGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 240, required = true, manualOnly = true)
    public static void processingLockIsolation(GameTestHelper helper) {
        ProcessingNativeObservation.reset();
        var fixture = new NativeProviderLaneFixtures(helper, NativeProviderLaneFixtures.sharedPatternAssignments());
        var registered = new boolean[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.connectEnergy(), "Waiting for native lock reference Provider");
            if (!registered[0]) {
                fixture.register();
                registered[0] = true;
            }
            fixture.installPattern(0, List.of(item(Items.COBBLESTONE, 1)),
                    List.of(item(Items.DIAMOND, 4), item(Items.GOLD_INGOT, 2)));
            var lane0 = fixture.lane(0);
            var lane1 = fixture.lane(1);
            var lane2 = fixture.lane(2);
            lane0.getConfigManager().putSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_WHILE_HIGH);
            setRedstoneSignal(fixture, true);
            var lockedBefore = fixture.targetSnapshot();
            helper.assertTrue(!fixture.push(0, 0), "Redstone-locked native Lane must reject");
            helper.assertValueEqual(fixture.targetSnapshot(), lockedBefore,
                    "Redstone rejection must mutate no target state");
            helper.assertTrue(!lane0.isBusy(), "Redstone rejection must create no native remainder");
            helper.assertTrue(fixture.push(1, 0), "Unlocked Lane B must progress independently");
            helper.assertTrue(fixture.push(2, 0), "Unlocked Lane C must progress independently");
            helper.assertValueEqual(fixture.targetItemCount(Items.COBBLESTONE), 2,
                    "Only the two unlocked Lanes may transfer input");

            lane1.getConfigManager().putSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_UNTIL_RESULT);
            helper.assertTrue(fixture.push(1, 0), "Result-locked Lane must accept its next native batch");
            helper.assertValueEqual(lane1.getCraftingLockedReason(), LockCraftingMode.LOCK_UNTIL_RESULT,
                    "Native Lane must wait for its primary output");
            var returnInventory = lane1.getReturnInv();
            returnInventory.setStack(0, item(Items.GOLD_INGOT, 2));
            returnInventory.setStack(1, item(Items.DIAMOND, 4));
            var before = ProcessingRegressionEvidence.snapshot(returnInventory);
            var destination = new GenericStackInv(null, 2);
            destination.setStack(0, item(Items.GOLD_INGOT, 63));
            destination.setStack(1, item(Items.DIAMOND, 62));
            helper.assertTrue(injectReturns(lane1, destination),
                    "Native return inventory must deliver the accepted byproduct and primary prefix");
            helper.assertValueEqual(destination.getAmount(0), 64L, "One byproduct must be accepted");
            helper.assertValueEqual(destination.getAmount(1), 64L, "Two primary outputs must be accepted");
            helper.assertValueEqual(lane1.getUnlockStack().amount(), 2L,
                    "Partial primary output must leave the exact native unlock remainder");
            helper.assertValueEqual(lane1.getCraftingLockedReason(), LockCraftingMode.LOCK_UNTIL_RESULT,
                    "Byproduct plus partial primary output must not fabricate completion");
            var partial = ProcessingRegressionEvidence.snapshot(returnInventory);
            ProcessingNativeObservation.recordLogicState("lock-partial", lane1, List.of());
            destination.clear();
            helper.assertTrue(injectReturns(lane1, destination), "Remaining native returns must deliver");
            helper.assertValueEqual(lane1.getCraftingLockedReason(), LockCraftingMode.NONE,
                    "Only complete primary quantity may release the native Lane lock");
            ProcessingNativeObservation.recordLogicState("lock-complete", lane1, List.of());
            helper.assertTrue(lane0.getCraftingLockedReason() == LockCraftingMode.LOCK_WHILE_HIGH
                    && lane2.getCraftingLockedReason() == LockCraftingMode.NONE,
                    "Result return must not unlock another Lane");
            ProcessingRegressionEvidence.write("processinglockisolation", 15, Map.ofEntries(
                    Map.entry("lockedLaneIdentity", ProcessingRegressionEvidence.identity(lane0)),
                    Map.entry("resultLaneIdentity", ProcessingRegressionEvidence.identity(lane1)),
                    Map.entry("freeLaneIdentity", ProcessingRegressionEvidence.identity(lane2)),
                    Map.entry("redstoneRejectAccepted", "false"), Map.entry("redstoneRejectMutated", "false"),
                    Map.entry("redstoneRejectRemainder", "0"), Map.entry("independentLanesProgressed", "2"),
                    Map.entry("primaryOutput", "minecraft:diamond:4"), Map.entry("byproduct", "minecraft:gold_ingot:2"),
                    Map.entry("returnSnapshotBefore", before), Map.entry("returnSnapshotPartial", partial),
                    Map.entry("primaryAcceptedPrefix", "2"), Map.entry("primaryNativeRemainder", "2"),
                    Map.entry("byproductAcceptedPrefix", "1"), Map.entry("byproductNativeRemainder", "1"),
                    Map.entry("lockAfterPartial", "LOCK_UNTIL_RESULT"), Map.entry("lockAfterComplete", "NONE"),
                    Map.entry("crossLaneUnlock", "false"), Map.entry("returnInjectObservations",
                            Long.toString(ProcessingNativeObservation.count("return-inject")))));
            fixture.close();
        });
    }
}
