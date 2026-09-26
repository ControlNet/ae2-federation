package space.controlnet.ae2federation.test;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.processing.endpoint.EndpointItemReturnAttempt;
import space.controlnet.ae2federation.test.processing.endpoint.EndpointModeEvidence;
import space.controlnet.ae2federation.test.processing.endpoint.EndpointModeFixtures;

@PrefixGameTestTemplate(false)
public final class EndpointReturnGameTests {
    private EndpointReturnGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 240, required = true, manualOnly = true)
    public static void endpointReturnBackpressure(GameTestHelper helper) {
        var fixture = new EndpointModeFixtures(helper, true);
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for native Endpoint fixture");
            var duplicateOwnersRejected = !fixture.bindLocal();
            helper.assertTrue(duplicateOwnersRejected, "Two adjacent Providers must leave Local mode unbound");
            var candidate0 = fixture.provider().getLogic().getReturnInv();
            var candidate1 = fixture.secondProvider().getLogic().getReturnInv();
            candidate0.setStack(0, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1));
            candidate1.setStack(0, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 2));
            var candidate0Before = EndpointModeEvidence.observeInventory("endpointreturnbackpressure", 0, "before",
                    candidate0);
            var candidate1Before = EndpointModeEvidence.observeInventory("endpointreturnbackpressure", 1, "before",
                    candidate1);
            var noSinkHandler = fixture.itemCapability(Direction.NORTH);
            helper.assertTrue(noSinkHandler == null, "No permitted sink must expose no accepting handler");
            var noSink = EndpointItemReturnAttempt.insert(noSinkHandler, 0, new ItemStack(Items.IRON_INGOT, 10), false);
            var candidate0After = EndpointModeEvidence.observeInventory("endpointreturnbackpressure", 0, "after",
                    candidate0);
            var candidate1After = EndpointModeEvidence.observeInventory("endpointreturnbackpressure", 1, "after",
                    candidate1);
            helper.assertValueEqual(noSink.acceptedAmount(), 0, "No permitted sink must accept zero items");
            helper.assertValueEqual(noSink.remainder().getCount(), 10,
                    "Zero acceptance must return the complete caller-owned remainder");
            helper.assertTrue(!candidate0Before.identity().equals(candidate1Before.identity()),
                    "Two candidate Providers must retain distinct return inventories");
            helper.assertValueEqual(candidate0After.snapshot(), candidate0Before.snapshot(),
                    "No-sink return must not mutate the first candidate inventory");
            helper.assertValueEqual(candidate1After.snapshot(), candidate1Before.snapshot(),
                    "No-sink return must not mutate the second candidate inventory");
            fixture.removeSecondProvider();
            helper.assertTrue(fixture.binding().activateLocal(List.of()), "One remaining adjacent Provider must bind");
            var inventory = fixture.provider().getLogic().getReturnInv();
            for (int slot = 0; slot < inventory.size(); slot++) {
                inventory.setStack(slot, new GenericStack(AEItemKey.of(Items.IRON_INGOT), slot == 8 ? 60 : 64));
            }
            var handler = fixture.itemCapability(Direction.NORTH);
            var simulateBefore = inventory.getAmount(8);
            var simulated = EndpointItemReturnAttempt.insert(handler, 8, new ItemStack(Items.IRON_INGOT, 10), true);
            var simulateAfter = inventory.getAmount(8);
            helper.assertValueEqual(simulated.acceptedAmount(), 4, "Simulation must report native partial acceptance");
            helper.assertValueEqual(simulated.remainder().getCount(), 6,
                    "Simulation must report the native partial remainder");
            helper.assertValueEqual(inventory.getAmount(8), 60L, "Simulation must not mutate native ownership");
            var actual = EndpointItemReturnAttempt.insert(handler, 8, new ItemStack(Items.IRON_INGOT, 10), false);
            helper.assertValueEqual(actual.acceptedAmount(), 4, "Actual return must report native acceptance");
            helper.assertValueEqual(actual.remainder().getCount(), 6,
                    "Caller must retain the unaccepted partial remainder");
            helper.assertValueEqual(inventory.getAmount(8), 64L, "Only the native accepted amount may transfer");
            EndpointModeEvidence.write("endpointreturnbackpressure", 8, Map.ofEntries(
                    Map.entry("duplicateOwnersRejected", Boolean.toString(duplicateOwnersRejected)),
                    Map.entry("candidateOwnerCount", "2"),
                    Map.entry("candidate0InventoryIdentity", candidate0Before.identity()),
                    Map.entry("candidate1InventoryIdentity", candidate1Before.identity()),
                    Map.entry("candidate0SlotCount", Integer.toString(candidate0Before.slots())),
                    Map.entry("candidate1SlotCount", Integer.toString(candidate1Before.slots())),
                    Map.entry("candidate0SnapshotBefore", candidate0Before.snapshot()),
                    Map.entry("candidate0SnapshotAfter", candidate0After.snapshot()),
                    Map.entry("candidate1SnapshotBefore", candidate1Before.snapshot()),
                    Map.entry("candidate1SnapshotAfter", candidate1After.snapshot()),
                    Map.entry("candidate0Unchanged", Boolean.toString(
                            candidate0Before.snapshot().equals(candidate0After.snapshot()))),
                    Map.entry("candidate1Unchanged", Boolean.toString(
                            candidate1Before.snapshot().equals(candidate1After.snapshot()))),
                    Map.entry("noSinkHandlerPresent", Boolean.toString(noSinkHandler != null)),
                    Map.entry("noSinkRequested", Integer.toString(noSink.requestedAmount())),
                    Map.entry("noSinkAccepted", Integer.toString(noSink.acceptedAmount())),
                    Map.entry("noSinkRemainder", Integer.toString(noSink.remainder().getCount())),
                    Map.entry("callerRetained", Integer.toString(noSink.remainder().getCount())),
                    Map.entry("simulateRequested", Integer.toString(simulated.requestedAmount())),
                    Map.entry("simulateAccepted", Integer.toString(simulated.acceptedAmount())),
                    Map.entry("simulateRemainder", Integer.toString(simulated.remainder().getCount())),
                    Map.entry("simulateMutated", Boolean.toString(simulateBefore != simulateAfter)),
                    Map.entry("actualRequested", Integer.toString(actual.requestedAmount())),
                    Map.entry("actualAccepted", Integer.toString(actual.acceptedAmount())),
                    Map.entry("actualRemainder", Integer.toString(actual.remainder().getCount())),
                    Map.entry("nativeCapacity", Long.toString(inventory.getAmount(8)))));
            fixture.close();
        });
    }
}
