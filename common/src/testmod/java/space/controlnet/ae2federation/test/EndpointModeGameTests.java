package space.controlnet.ae2federation.test;

import java.util.LinkedHashMap;
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
import space.controlnet.ae2federation.ae2.processing.endpoint.EndpointMode;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.processing.claim.ClaimState;
import space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity;
import space.controlnet.ae2federation.processing.endpoint.EndpointModeGeneration;
import space.controlnet.ae2federation.processing.provider.ProviderId;
import space.controlnet.ae2federation.processing.provider.ProviderIdentity;
import space.controlnet.ae2federation.processing.provider.ProviderInstanceEpoch;
import space.controlnet.ae2federation.processing.provider.ProviderTargetState;
import space.controlnet.ae2federation.test.processing.ProviderTargetRuntimeFixtures;
import space.controlnet.ae2federation.test.processing.endpoint.EndpointModeEvidence;
import space.controlnet.ae2federation.test.processing.endpoint.EndpointModeFixtures;

@PrefixGameTestTemplate(false)
public final class EndpointModeGameTests {
    private EndpointModeGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 240, required = true, manualOnly = true)
    public static void endpointLocal(GameTestHelper helper) {
        var fixture = new EndpointModeFixtures(helper, false);
        var saved = new EndpointModeFixtures.SavedState[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for production Endpoint lifecycle");
            helper.assertTrue(fixture.bindLocal(), "One adjacent native Provider must activate Local mode");
            if (saved[0] == null) {
                fixture.installPattern();
                helper.assertTrue(fixture.binding().runtime().mode().orElseThrow()
                        instanceof EndpointModeGeneration.Local, "Local mode must have a typed generation");
                helper.assertTrue(fixture.nativePush(), "Native Provider must target Endpoint subnet storage");
                helper.assertValueEqual(fixture.subnetItemCount(), 1L, "Local input must reach the subnet Grid");
                helper.assertTrue(fixture.providerNode().getGrid() != fixture.endpointNode().getGrid(),
                        "Local source and subnet Grids must remain separate");
                var context = fixture.binding().runtime().itemReturnContext().orElseThrow();
                helper.assertTrue(context.owner().inventory() == fixture.provider().getLogic().getReturnInv(),
                        "Local return context must retain the exact Provider return inventory");
                var handler = fixture.itemCapability(Direction.NORTH);
                helper.assertTrue(handler != null, "Local item return capability must resolve");
                helper.assertTrue(handler.insertItem(0, new ItemStack(Items.IRON_INGOT), false).isEmpty(),
                        "Local return must delegate to the native adapter");
                helper.assertValueEqual(fixture.provider().getLogic().getReturnInv().getAmount(0), 1L,
                        "Native Provider return inventory must own the accepted item");
                saved[0] = fixture.reloadEndpoint();
                helper.assertTrue(false, "Waiting for restored production Endpoint lifecycle");
            }
            helper.assertValueEqual(fixture.endpointIdentity(), saved[0].identity(),
                    "Endpoint identity must survive save and load");
            helper.assertValueEqual(fixture.claimState(), saved[0].claim(),
                    "Endpoint Claim state must survive save and load");
            helper.assertTrue(fixture.binding().runtime().generation() > saved[0].generation(),
                    "Restored Local activation must advance the durable mode generation");
            helper.assertTrue(fixture.itemCapability(Direction.NORTH) != null,
                    "Restored Local mode must expose current native capabilities");
            EndpointModeEvidence.write("endpointlocal", 10, Map.ofEntries(
                    Map.entry("mode", "LOCAL"), Map.entry("verifiedUpstreams", "1"),
                    Map.entry("hubRequired", "false"), Map.entry("fabricRequired", "false"),
                    Map.entry("sourceGridSeparated", "true"), Map.entry("nativeInputAccepted", "true"),
                    Map.entry("nativeReturnOwner", "true"), Map.entry("endpointBuffer", "false"),
                    Map.entry("typedGeneration", "true"), Map.entry("productionLifecycle", "true"),
                    Map.entry("identityRestored", "true"), Map.entry("claimRestored", "true"),
                    Map.entry("generationAdvancedAfterLoad", "true")));
            fixture.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 240, required = true, manualOnly = true)
    public static void endpointFederated(GameTestHelper helper) {
        var owner = new ProviderIdentity(ProviderId.create(), new ProviderInstanceEpoch(7));
        var fixture = new ProviderTargetRuntimeFixtures(helper, true, owner);
        var saved = new ProviderTargetRuntimeFixtures.FederatedSavedState[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.initialize(), "Waiting for production Provider and Endpoint runtime");
            if (saved[0] == null) {
                helper.assertTrue(fixture.enablePolicy(java.util.Set.of(PolicyOperation.EXECUTE, PolicyOperation.SUPPLY)),
                        "Federated mode requires Processing Policy");
                fixture.connectFabric();
                helper.assertTrue(fixture.pushOnce(), "Authorized Claim must accept federated native input");
                helper.assertValueEqual(fixture.state(), ProviderTargetState.ACTIVE, "Federated target must be active");
                helper.assertTrue(fixture.endpointBinding().runtime().mode().orElseThrow()
                        instanceof EndpointModeGeneration.Federated,
                        "Federated mode must retain a typed Claim generation");
                var context = fixture.endpointBinding().runtime().itemReturnContext().orElseThrow();
                helper.assertTrue(context.owner().inventory() == fixture.nativeRemainderDestination(),
                        "Federated return context must retain the exact native Lane return inventory");
                var handler = fixture.endpointBinding().runtime().itemReturn(Direction.NORTH).orElseThrow();
                helper.assertTrue(handler.insertItem(0, new ItemStack(Items.GOLD_INGOT), false).isEmpty(),
                        "Federated return must delegate to the native Lane adapter");
                helper.assertValueEqual(fixture.providerLogic().getReturnInv().getAmount(0), 1L,
                        "The original native Lane must own the returned item");
                helper.assertTrue(fixture.sourceGrid() != fixture.targetGrid(), "Federated Grids must remain distinct");
                helper.assertValueEqual(fixture.nativeTargetLookupCount(), 2,
                        "Authorized input must use both native target lookups");
                helper.assertTrue(fixture.productionEndpointEntity().claimState() instanceof ClaimState.Owned owned
                        && owned.ownerIdentity().provider().equals(owner) && owned.epoch().value() == 1,
                        "Production Endpoint must own the exact non-default Provider");
                saved[0] = fixture.reloadProductionEndpoint();
                helper.assertTrue(!saved[0].runtimeReferencesSerialized(),
                        "Endpoint persistence must not serialize runtime object references");
                helper.assertTrue(false, "Waiting for restored production Endpoint lifecycle");
            }
            var restored = fixture.productionEndpointEntity();
            var claim = (ClaimState.Owned) restored.claimState();
            var observation = fixture.finishPersistenceObservation();
            helper.assertValueEqual(restored.endpointIdentity(), saved[0].endpoint(),
                    "Endpoint identity and instance epoch must survive native reload");
            helper.assertValueEqual(claim, saved[0].claim(), "Full owned Claim state must survive native reload");
            helper.assertValueEqual(claim.ownerIdentity().provider(), owner,
                    "Provider ID and non-default instance epoch must survive native reload");
            helper.assertTrue(fixture.endpointBinding().runtime().configuredMode() == EndpointMode.FEDERATED,
                    "Configured Federated mode must survive native reload");
            helper.assertTrue(fixture.endpointBinding().runtime().generation() > saved[0].generation(),
                    "Restored runtime activation must advance generation monotonically");
            helper.assertTrue(!EndpointModeEvidence.identity(fixture.endpointBinding()).equals(saved[0].bindingIdentity()),
                    "Reload must construct exactly one fresh binding rather than reuse the old object");
            helper.assertTrue(fixture.targetCapabilityIsCurrentBinding(),
                    "Reloaded target capability must expose the current production binding");
            helper.assertTrue(fixture.endpointBinding().runtime().inputStorage(Direction.NORTH).isPresent(),
                    "Reloaded Federated mode must expose current input capability");
            helper.assertTrue(fixture.pushLane(0), "Restored owned Claim must authorize fresh native input");
            helper.assertValueEqual(fixture.targetItemCount(), 2L,
                    "Post-reload native input must reach the same target Grid");
            helper.assertTrue(fixture.endpointBinding().runtime().itemReturn(Direction.NORTH).isPresent(),
                    "Post-reload authorization must install current native return capability");
            var facts = new LinkedHashMap<>(observation.evidenceFacts());
            facts.put("mode", "FEDERATED");
            facts.put("claimAuthorized", "true");
            facts.put("claimEpoch", Long.toString(claim.epoch().value()));
            facts.put("runtimeBinding", "true");
            facts.put("nativeInputAccepted", "true");
            facts.put("nativeTargetLookups", Integer.toString(fixture.nativeTargetLookupCount()));
            facts.put("nativeReturnOwner", "true");
            facts.put("sourceGridSeparated", Boolean.toString(fixture.sourceGrid() != fixture.targetGrid()));
            facts.put("endpointBuffer", "false");
            facts.put("targetCapabilityRestored", Boolean.toString(fixture.targetCapabilityIsCurrentBinding()));
            facts.put("postReloadNativeInputAccepted", "true");
            facts.put("runtimeReferencesSerialized", Boolean.toString(saved[0].runtimeReferencesSerialized()));
            EndpointModeEvidence.write("endpointfederated", 23, facts);
            fixture.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 240, required = true, manualOnly = true)
    public static void endpointFiveFaceReturns(GameTestHelper helper) {
        runLocal(helper, false, fixture -> {
            helper.assertTrue(fixture.bindLocal(), "Local return owner must bind");
            var facts = new LinkedHashMap<String, String>();
            for (var face : EndpointModeFixtures.LOGISTICS_FACES) {
                helper.assertTrue(fixture.exposedNode(face) == fixture.endpointNode()
                        && fixture.storageCapability(face) != null
                        && fixture.itemCapability(face) != null
                        && fixture.fluidCapability(face) != null, "Every logistics face must expose typed native paths");
                helper.assertTrue(fixture.itemCapability(face).insertItem(0,
                        new ItemStack(Items.IRON_INGOT), false).isEmpty(), "Item return must accept on every face");
                fixture.clearReturn(fixture.provider());
                helper.assertValueEqual(fixture.fluidCapability(face).fill(new FluidStack(Fluids.WATER, 125),
                        IFluidHandler.FluidAction.EXECUTE), 125, "Fluid return must accept on every face");
                fixture.clearReturn(fixture.provider());
                facts.put("face." + face.getSerializedName(), "node-storage-item-fluid");
            }
            var federation = EndpointBlockEntity.FEDERATION_FACE;
            helper.assertTrue(fixture.exposedNode(federation) == null && fixture.storageCapability(federation) == null
                    && fixture.itemCapability(federation) == null && fixture.fluidCapability(federation) == null,
                    "Federation face must expose no native logistics capability");
            facts.put("logisticsFaces", "5");
            facts.put("federationFaceExcluded", "true");
            facts.put("itemAdapter", "GenericStackItemStorage");
            facts.put("fluidAdapter", "GenericStackFluidStorage");
            facts.put("typedReturnContexts", "true");
            EndpointModeEvidence.write("endpointfivefacereturns", 16, facts);
        });
    }

    private static void runLocal(GameTestHelper helper, boolean secondProvider,
            java.util.function.Consumer<EndpointModeFixtures> assertions) {
        var fixture = new EndpointModeFixtures(helper, secondProvider);
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for native Endpoint fixture");
            assertions.accept(fixture);
            fixture.close();
        });
    }
}
