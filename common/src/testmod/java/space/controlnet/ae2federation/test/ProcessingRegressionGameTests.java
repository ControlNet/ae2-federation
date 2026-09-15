package space.controlnet.ae2federation.test;

import static space.controlnet.ae2federation.test.processing.ProcessingRegressionFixtures.fluid;
import static space.controlnet.ae2federation.test.processing.ProcessingRegressionFixtures.item;
import static space.controlnet.ae2federation.test.processing.ProcessingRegressionFixtures.sendList;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.test.processing.NativeProviderLaneFixtures;
import space.controlnet.ae2federation.test.processing.ProcessingNativeObservation;
import space.controlnet.ae2federation.test.processing.ProcessingRegressionEvidence;
import space.controlnet.ae2federation.test.processing.ProviderLifecycleFixtureSupport;
import space.controlnet.ae2federation.test.processing.ProviderTargetRuntimeFixtures;

@PrefixGameTestTemplate(false)
public final class ProcessingRegressionGameTests {
    private ProcessingRegressionGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void processingNativeDifferential(GameTestHelper helper) {
        ProcessingNativeObservation.reset();
        var state = new DifferentialState(new NativeProviderLaneFixtures(helper,
                NativeProviderLaneFixtures.sharedPatternAssignments()));
        helper.succeedWhen(() -> {
            if (state.nativeFixture != null) {
                var fixture = state.nativeFixture;
                helper.assertTrue(fixture.connectEnergy(), "Waiting for native reference Provider power");
                fixture.register();
                fixture.installPattern(0, List.of(item(Items.COBBLESTONE, 1)),
                        List.of(item(Items.DIAMOND, 4), item(Items.GOLD_INGOT, 2)));
                state.nativeLogic = ProcessingRegressionEvidence.identity(fixture.lane(0));
                helper.assertTrue(fixture.pushInputs(0, 0, List.of(item(Items.DIRT, 1))),
                        "Native Provider must send the actual holder key");
                helper.assertValueEqual(fixture.targetItemCount(Items.DIRT), 1,
                        "Native target must own the substituted input");
                helper.assertValueEqual(fixture.targetItemCount(Items.COBBLESTONE), 0,
                        "Native target must not receive the nominal Pattern key");
                helper.assertValueEqual(fixture.lane(0).getAvailablePatterns().getFirst().getOutputs().size(), 2,
                        "Native Pattern must retain primary output and byproduct");
                fixture.close();
                state.nativeFixture = null;
                state.federationFixture = new ProviderTargetRuntimeFixtures(helper, true);
                helper.assertTrue(false, "Waiting for Federation target lifecycle");
            }
            var fixture = state.federationFixture;
            helper.assertTrue(fixture.initialize(), "Waiting for Federation Provider and Endpoint");
            if (!state.federationConfigured) {
                helper.assertTrue(fixture.enablePolicy(java.util.Set.of(PolicyOperation.EXECUTE,
                        PolicyOperation.SUPPLY)), "Federation Processing policy must activate");
                fixture.connectFabric();
                fixture.installPattern(List.of(item(Items.COBBLESTONE, 1)),
                        List.of(item(Items.DIAMOND, 4), item(Items.GOLD_INGOT, 2)));
                state.federationConfigured = true;
            }
            state.federationLogic = ProcessingRegressionEvidence.identity(fixture.providerLogic());
            helper.assertTrue(fixture.pushLaneWithInputs(0, List.of(item(Items.DIRT, 1))),
                    "Federation must delegate the same actual holder key to AE2");
            helper.assertValueEqual(fixture.targetAmount(AEItemKey.of(Items.DIRT)), 1L,
                    "Federation target must own the substituted input");
            helper.assertValueEqual(fixture.targetAmount(AEItemKey.of(Items.COBBLESTONE)), 0L,
                    "Federation must not reconstruct nominal inputs");
            helper.assertTrue(!state.nativeLogic.equals(state.federationLogic),
                    "Native reference and Federation observations must identify independent logic objects");
            helper.assertValueEqual(ProcessingNativeObservation.count("push-head"), 2L,
                    "Both paths must enter authentic AE2 pushPattern");
            helper.assertValueEqual(ProcessingNativeObservation.count("push-return"), 2L,
                    "Both native pushes must return through authentic AE2 logic");
            ProcessingRegressionEvidence.write("processingnativedifferential", 12, Map.ofEntries(
                    Map.entry("nominalInput", "minecraft:cobblestone:1"),
                    Map.entry("actualInput", "minecraft:dirt:1"),
                    Map.entry("nativeActualAccepted", "1"), Map.entry("federationActualAccepted", "1"),
                    Map.entry("nativeNominalAccepted", "0"), Map.entry("federationNominalAccepted", "0"),
                    Map.entry("primaryOutput", "minecraft:diamond:4"),
                    Map.entry("byproduct", "minecraft:gold_ingot:2"),
                    Map.entry("nativeLogicIdentity", state.nativeLogic),
                    Map.entry("federationLogicIdentity", state.federationLogic),
                    Map.entry("nativeObjectsIndependent", "true"), Map.entry("pushEntries", "2"),
                    Map.entry("pushReturns", "2"), Map.entry("nativeAuthority", "PatternProviderLogic")));
            fixture.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void processingSharedCapacity(GameTestHelper helper) {
        ProcessingNativeObservation.reset();
        var state = new CapacityState(new NativeProviderLaneFixtures(helper,
                NativeProviderLaneFixtures.sharedPatternAssignments()));
        helper.succeedWhen(() -> {
            if (state.nativeFixture != null) {
                runNativeCapacity(helper, state);
                state.nativeFixture.close();
                state.nativeFixture = null;
                state.federationFixture = new ProviderTargetRuntimeFixtures(helper, true);
                helper.assertTrue(false, "Waiting for Federation mixed-return boundary");
            }
            runFederationCapacity(helper, state);
        });
    }

    private static void runNativeCapacity(GameTestHelper helper, CapacityState state) {
        var fixture = state.nativeFixture;
        helper.assertTrue(fixture.connectEnergy(), "Waiting for native shared-capacity Provider");
        fixture.register();
        fixture.leaveOneSharedTargetSlot();
        fixture.installPattern(0, List.of(item(Items.COBBLESTONE, 1), item(Items.DIRT, 1)),
                List.of(item(Items.DIAMOND, 1)));
        helper.assertTrue(fixture.pushInputs(0, 0, List.of(item(Items.COBBLESTONE, 1), item(Items.DIRT, 1))),
                "Pinned native Provider reports success after a nonzero simulation for each key");
        helper.assertValueEqual(fixture.targetItemCount(Items.COBBLESTONE), 1,
                "Shared final slot must own the accepted prefix");
        helper.assertValueEqual(fixture.targetItemCount(Items.DIRT), 0,
                "Second resource key must remain pending after shared capacity is consumed");
        helper.assertTrue(fixture.lane(0).isBusy(), "Native sendList must own the unaccepted suffix");
        var remainder = sendList(fixture.lane(0));
        helper.assertTrue(remainder.size() == 1 && remainder.getFirst().what().equals(AEItemKey.of(Items.DIRT))
                && remainder.getFirst().amount() == 1, "Native sendList must own exactly the dirt remainder");
        var beforeReplay = fixture.targetSnapshot();
        helper.assertTrue(!fixture.pushInputs(0, 0, List.of(item(Items.COBBLESTONE, 1), item(Items.DIRT, 1))),
                "Busy native context must reject full replay");
        helper.assertValueEqual(fixture.targetSnapshot(), beforeReplay,
                "Rejected replay must not duplicate the accepted prefix");
        ProviderLifecycleFixtureSupport.clearTarget(fixture);
        fixture.installPattern(0, List.of(item(Items.IRON_INGOT, 1), fluid(Fluids.WATER, 1000)),
                List.of(item(Items.GOLD_INGOT, 1)));
        var mixedBefore = fixture.targetSnapshot();
        helper.assertTrue(!fixture.pushInputs(1, 0, List.of(item(Items.IRON_INGOT, 1),
                fluid(Fluids.WATER, 1000))), "Unsupported mixed-fluid batch must reject normally");
        helper.assertValueEqual(fixture.targetSnapshot(), mixedBefore,
                "Normal mixed-fluid rejection must mutate no target state");
        state.nativeLogic = ProcessingRegressionEvidence.identity(fixture.lane(0));
        state.remainderOwner = ProcessingRegressionEvidence.identity(fixture.lane(0).getReturnInv());
    }

    private static void runFederationCapacity(GameTestHelper helper, CapacityState state) {
        var fixture = state.federationFixture;
        helper.assertTrue(fixture.initialize(), "Waiting for Federation return context");
        if (!state.federationConfigured) {
            helper.assertTrue(fixture.enablePolicy(java.util.Set.of(PolicyOperation.EXECUTE,
                    PolicyOperation.SUPPLY)), "Federation Processing policy must activate");
            fixture.connectFabric();
            helper.assertTrue(fixture.pushOnce(), "Federation must issue the authentic native return context");
            state.federationConfigured = true;
        }
        var inventory = fixture.providerLogic().getReturnInv();
        for (int slot = 0; slot < inventory.size() - 1; slot++) {
            inventory.setStack(slot, item(Items.SAND, 64));
        }
        var itemHandler = fixture.endpointBinding().runtime().itemReturn(Direction.NORTH).orElseThrow();
        var fluidHandler = fixture.endpointBinding().runtime().fluidReturn(Direction.NORTH).orElseThrow();
        helper.assertTrue(itemHandler.insertItem(8, new ItemStack(Items.GOLD_INGOT), true).isEmpty(),
                "Item simulation must see the shared native slot");
        helper.assertValueEqual(fluidHandler.fill(new FluidStack(Fluids.WATER, 1000),
                IFluidHandler.FluidAction.SIMULATE), 1000, "Fluid simulation must see the same shared native slot");
        helper.assertTrue(itemHandler.insertItem(8, new ItemStack(Items.GOLD_INGOT), false).isEmpty(),
                "First actual return must take shared native ownership");
        helper.assertValueEqual(fluidHandler.fill(new FluidStack(Fluids.WATER, 1000),
                IFluidHandler.FluidAction.EXECUTE), 0, "Caller must retain fluid after the shared slot is consumed");
        helper.assertValueEqual(inventory.getAmount(8), 1L, "Only one resource may own the final native slot");
        ProcessingRegressionEvidence.write("processingsharedcapacity", 16, Map.ofEntries(
                Map.entry("nativeLogicIdentity", state.nativeLogic),
                Map.entry("nativeRemainderOwnerIdentity", state.remainderOwner),
                Map.entry("nativePushAccepted", "true"), Map.entry("acceptedPrefix", "minecraft:cobblestone:1"),
                Map.entry("nativePendingRemainder", "minecraft:dirt:1"), Map.entry("falseReplayAccepted", "false"),
                Map.entry("mixedItem", "minecraft:iron_ingot:1"), Map.entry("mixedFluid", "minecraft:water:1000"),
                Map.entry("mixedRejectMutated", "false"), Map.entry("itemSimulationAccepted", "1"),
                Map.entry("fluidSimulationAccepted", "1000"), Map.entry("actualItemAccepted", "1"),
                Map.entry("actualFluidAccepted", "0"), Map.entry("fluidCallerRemainder", "1000"),
                Map.entry("sharedReturnInventoryIdentity", ProcessingRegressionEvidence.identity(inventory)),
                Map.entry("nativeLimitation", "nonzero-per-key-simulation-not-atomic"),
                Map.entry("sendRemainderObservations", Long.toString(
                        ProcessingNativeObservation.count("send-remainder")))));
        fixture.close();
    }

    private static final class DifferentialState {
        private NativeProviderLaneFixtures nativeFixture;
        private ProviderTargetRuntimeFixtures federationFixture;
        private boolean federationConfigured;
        private String nativeLogic;
        private String federationLogic;

        private DifferentialState(NativeProviderLaneFixtures nativeFixture) {
            this.nativeFixture = nativeFixture;
        }
    }

    private static final class CapacityState {
        private NativeProviderLaneFixtures nativeFixture;
        private ProviderTargetRuntimeFixtures federationFixture;
        private boolean federationConfigured;
        private String nativeLogic;
        private String remainderOwner;

        private CapacityState(NativeProviderLaneFixtures nativeFixture) {
            this.nativeFixture = nativeFixture;
        }
    }
}
