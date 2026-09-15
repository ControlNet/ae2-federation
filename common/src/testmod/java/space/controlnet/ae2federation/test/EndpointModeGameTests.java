package space.controlnet.ae2federation.test;

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
import space.controlnet.ae2federation.ae2.processing.endpoint.EndpointMode;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.processing.claim.ClaimState;
import space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity;
import space.controlnet.ae2federation.processing.endpoint.EndpointItemReturnAttempt;
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

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 240, required = true, manualOnly = true)
    public static void endpointReturnBackpressure(GameTestHelper helper) {
        runLocal(helper, true, fixture -> {
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
            helper.assertTrue(noSinkHandler == null,
                    "No permitted sink must expose no accepting handler");
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
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 240, required = true, manualOnly = true)
    public static void endpointRejectModeTakeover(GameTestHelper helper) {
        var fixture = new ProviderTargetRuntimeFixtures(helper, true);
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.initialize(), "Waiting for production Provider and Endpoint runtime");
            helper.assertTrue(fixture.enablePolicy(java.util.Set.of(PolicyOperation.EXECUTE, PolicyOperation.SUPPLY)),
                    "Federated mode requires Processing Policy");
            fixture.connectFabric();
            helper.assertTrue(fixture.pushOnce(0), "The first native Lane must bind through production authorization");
            var oldContext = fixture.endpointBinding().runtime().itemReturnContext().orElse(null);
            helper.assertTrue(oldContext != null,
                    "The first authorized native Lane must install its immutable return context");
            var oldMode = oldContext.owner().mode();
            helper.assertTrue(fixture.endpointBinding().activateFederated(),
                    "Reactivation must issue a new Federated mode generation");
            var refreshedMode = fixture.endpointBinding().runtime().mode().orElseThrow();
            if (oldMode.equals(refreshedMode)) {
                throw new AssertionError("Federated reactivation reused a stale mode generation");
            }
            fixture.armAuthorizedReplay(0, 0);
            var staleTargetBefore = fixture.targetItemCount();
            var staleLaneZeroReturnBefore = fixture.nativeRemainderAmount(0, 0);
            var staleLaneOneReturnBefore = fixture.nativeRemainderAmount(1, 0);
            var staleMixinBefore = fixture.mixinLookupCount();
            var staleLookupBefore = fixture.nativeTargetLookupCount();
            var staleFoundBefore = fixture.nativeTargetFoundCount();
            var staleAccepted = fixture.pushLane(0);
            var staleReplay = fixture.finishAuthorizedReplay("stale");
            var staleMixinLookupDelta = fixture.mixinLookupCount() - staleMixinBefore;
            var staleNativeTargetLookupDelta = fixture.nativeTargetLookupCount() - staleLookupBefore;
            var staleNativeTargetsFoundDelta = fixture.nativeTargetFoundCount() - staleFoundBefore;
            var staleItemContextInstalled = !staleReplay.itemContextIdentityAfter().equals("0");
            var staleFluidContextInstalled = !staleReplay.fluidContextIdentityAfter().equals("0");
            var staleTargetDelta = fixture.targetItemCount() - staleTargetBefore;
            var staleLaneZeroReturnDelta = fixture.nativeRemainderAmount(0, 0) - staleLaneZeroReturnBefore;
            var staleLaneOneReturnDelta = fixture.nativeRemainderAmount(1, 0) - staleLaneOneReturnBefore;
            helper.assertTrue(!staleAccepted,
                    "A pre-cycle authorization must be rejected by the native cache/Mixin boundary");
            helper.assertTrue(staleReplay.mixinOwnerIdentity().equals(staleReplay.expectedLogicIdentity())
                    && staleReplay.cacheOwnerIdentity().equals(staleReplay.expectedLogicIdentity())
                    && staleReplay.cacheTargetFoundCount() == 0 && staleNativeTargetsFoundDelta == 1,
                    "Stale denial must observe but never accept the native candidate target");
            helper.assertTrue(!staleItemContextInstalled && !staleFluidContextInstalled,
                    "Stale authorization must not install fresh return contexts");
            helper.assertValueEqual(staleTargetDelta, 0L,
                    "Stale authorization must not mutate the native target");
            helper.assertTrue(staleLaneZeroReturnDelta == 0 && staleLaneOneReturnDelta == 0,
                    "Stale authorization must not mutate any native Lane return inventory");
            helper.assertTrue(fixture.pushOnce(1), "A second native Lane must resolve fresh production authorization");
            var newContext = fixture.endpointBinding().runtime().itemReturnContext().orElseThrow();
            if (newContext.owner().logic() != fixture.providerLogic(1)) {
                throw new AssertionError("Fresh authorization adopted unrelated native Lane logic");
            }
            fixture.armAuthorizedReplay(1, 0);
            var mismatchTargetBefore = fixture.targetItemCount();
            var mismatchLaneZeroReturnBefore = fixture.nativeRemainderAmount(0, 0);
            var mismatchLaneOneReturnBefore = fixture.nativeRemainderAmount(1, 0);
            var itemContextBeforeMismatch = fixture.endpointBinding().runtime().itemReturnContext().orElseThrow();
            var fluidContextBeforeMismatch = fixture.endpointBinding().runtime().fluidReturnContext().orElseThrow();
            var mismatchMixinBefore = fixture.mixinLookupCount();
            var mismatchLookupBefore = fixture.nativeTargetLookupCount();
            var mismatchFoundBefore = fixture.nativeTargetFoundCount();
            var mismatchAccepted = fixture.pushLane(0);
            var mismatchReplay = fixture.finishAuthorizedReplay("mismatch");
            var mismatchMixinLookupDelta = fixture.mixinLookupCount() - mismatchMixinBefore;
            var mismatchNativeTargetLookupDelta = fixture.nativeTargetLookupCount() - mismatchLookupBefore;
            var mismatchNativeTargetsFoundDelta = fixture.nativeTargetFoundCount() - mismatchFoundBefore;
            var mismatchItemContextChanged = fixture.endpointBinding().runtime().itemReturnContext().orElseThrow()
                    != itemContextBeforeMismatch;
            var mismatchFluidContextChanged = fixture.endpointBinding().runtime().fluidReturnContext().orElseThrow()
                    != fluidContextBeforeMismatch;
            var mismatchTargetDelta = fixture.targetItemCount() - mismatchTargetBefore;
            var mismatchLaneZeroReturnDelta = fixture.nativeRemainderAmount(0, 0) - mismatchLaneZeroReturnBefore;
            var mismatchLaneOneReturnDelta = fixture.nativeRemainderAmount(1, 0) - mismatchLaneOneReturnBefore;
            helper.assertTrue(!mismatchAccepted,
                    "Another Lane's package-issued provenance must be rejected by the bound cache entry");
            helper.assertTrue(mismatchReplay.mixinOwnerIdentity().equals(mismatchReplay.expectedLogicIdentity())
                    && mismatchReplay.cacheOwnerIdentity().equals(mismatchReplay.expectedLogicIdentity())
                    && mismatchReplay.cacheTargetFoundCount() == 0 && mismatchNativeTargetsFoundDelta == 0,
                    "Provenance denial must retain exact Mixin/cache identity and zero found targets");
            helper.assertTrue(!mismatchItemContextChanged && !mismatchFluidContextChanged,
                    "Mismatched provenance must not install fresh return contexts");
            helper.assertValueEqual(mismatchTargetDelta, 0L,
                    "Mismatched provenance must not mutate the native target");
            helper.assertTrue(mismatchLaneZeroReturnDelta == 0 && mismatchLaneOneReturnDelta == 0,
                    "Mismatched provenance must not mutate any native Lane return inventory");
            helper.assertTrue(oldContext.owner().logic() == fixture.providerLogic(0),
                    "Outstanding context must remain bound to its original native Lane");
            helper.assertTrue(newContext.owner().inventory() == fixture.nativeRemainderDestination(1),
                    "Fresh context must retain the exact authorized native Lane return inventory");
            helper.assertTrue(!oldMode.equals(newContext.owner().mode()), "Mode generations must remain distinct");
            helper.assertTrue(oldContext.capability().insertItem(0, new ItemStack(Items.IRON_INGOT), false).isEmpty(),
                    "Outstanding context must still delegate to its original native Lane");
            helper.assertTrue(newContext.capability().insertItem(0, new ItemStack(Items.GOLD_INGOT), false).isEmpty(),
                    "Fresh context must delegate only to its authorized native Lane");
            helper.assertValueEqual(fixture.providerLogic(0).getReturnInv().getAmount(0), 1L,
                    "Old return must remain with the first native Lane");
            helper.assertValueEqual(fixture.providerLogic(1).getReturnInv().getAmount(0), 1L,
                    "New return must remain with the second native Lane");
            EndpointModeEvidence.write("endpointrejectmodetakeover", 10, Map.ofEntries(
                    Map.entry("preCycleGeneration", Long.toString(staleReplay.authorizedGeneration())),
                    Map.entry("currentGeneration", Long.toString(refreshedMode.generation())),
                    Map.entry("staleExpectedLogicIdentity", staleReplay.expectedLogicIdentity()),
                    Map.entry("staleExpectedProvenanceIdentity", staleReplay.expectedProvenanceIdentity()),
                    Map.entry("staleActualProvenanceIdentity", staleReplay.actualProvenanceIdentity()),
                    Map.entry("staleAuthorizedGeneration", Long.toString(staleReplay.authorizedGeneration())),
                    Map.entry("staleResolverOwnerIdentity", staleReplay.resolverOwnerIdentity()),
                    Map.entry("staleResolverInvocationCount", Integer.toString(staleReplay.resolverInvocationCount())),
                    Map.entry("staleMixinOwnerIdentity", staleReplay.mixinOwnerIdentity()),
                    Map.entry("staleAuthorizationAccepted", Boolean.toString(staleAccepted)),
                    Map.entry("staleMixinLookupDelta", Integer.toString(staleMixinLookupDelta)),
                    Map.entry("staleObservedMixinLookupCount", Integer.toString(staleReplay.mixinLookupCount())),
                    Map.entry("staleCacheOwnerIdentity", staleReplay.cacheOwnerIdentity()),
                    Map.entry("staleObservedCacheLookupCount", Integer.toString(staleReplay.cacheLookupCount())),
                    Map.entry("staleCacheTargetFoundCount", Integer.toString(staleReplay.cacheTargetFoundCount())),
                    Map.entry("staleNativeTargetLookupDelta", Integer.toString(staleNativeTargetLookupDelta)),
                    Map.entry("staleNativeTargetsFoundDelta", Integer.toString(staleNativeTargetsFoundDelta)),
                    Map.entry("staleItemContextIdentityBefore", staleReplay.itemContextIdentityBefore()),
                    Map.entry("staleItemContextIdentityAfter", staleReplay.itemContextIdentityAfter()),
                    Map.entry("staleFluidContextIdentityBefore", staleReplay.fluidContextIdentityBefore()),
                    Map.entry("staleFluidContextIdentityAfter", staleReplay.fluidContextIdentityAfter()),
                    Map.entry("staleItemContextInstalled", Boolean.toString(staleItemContextInstalled)),
                    Map.entry("staleFluidContextInstalled", Boolean.toString(staleFluidContextInstalled)),
                    Map.entry("staleTargetMutation", Long.toString(staleTargetDelta)),
                    Map.entry("staleLane0ReturnMutation", Long.toString(staleLaneZeroReturnDelta)),
                    Map.entry("staleLane1ReturnMutation", Long.toString(staleLaneOneReturnDelta)),
                    Map.entry("mismatchExpectedLogicIdentity", mismatchReplay.expectedLogicIdentity()),
                    Map.entry("mismatchExpectedProvenanceIdentity", mismatchReplay.expectedProvenanceIdentity()),
                    Map.entry("mismatchActualProvenanceIdentity", mismatchReplay.actualProvenanceIdentity()),
                    Map.entry("mismatchAuthorizedGeneration", Long.toString(mismatchReplay.authorizedGeneration())),
                    Map.entry("mismatchResolverOwnerIdentity", mismatchReplay.resolverOwnerIdentity()),
                    Map.entry("mismatchResolverInvocationCount", Integer.toString(
                            mismatchReplay.resolverInvocationCount())),
                    Map.entry("mismatchMixinOwnerIdentity", mismatchReplay.mixinOwnerIdentity()),
                    Map.entry("mismatchAuthorizationAccepted", Boolean.toString(mismatchAccepted)),
                    Map.entry("mismatchMixinLookupDelta", Integer.toString(mismatchMixinLookupDelta)),
                    Map.entry("mismatchObservedMixinLookupCount", Integer.toString(mismatchReplay.mixinLookupCount())),
                    Map.entry("mismatchCacheOwnerIdentity", mismatchReplay.cacheOwnerIdentity()),
                    Map.entry("mismatchObservedCacheLookupCount", Integer.toString(mismatchReplay.cacheLookupCount())),
                    Map.entry("mismatchCacheTargetFoundCount", Integer.toString(
                            mismatchReplay.cacheTargetFoundCount())),
                    Map.entry("mismatchNativeTargetLookupDelta", Integer.toString(mismatchNativeTargetLookupDelta)),
                    Map.entry("mismatchNativeTargetsFoundDelta", Integer.toString(mismatchNativeTargetsFoundDelta)),
                    Map.entry("mismatchItemContextIdentityBefore", mismatchReplay.itemContextIdentityBefore()),
                    Map.entry("mismatchItemContextIdentityAfter", mismatchReplay.itemContextIdentityAfter()),
                    Map.entry("mismatchFluidContextIdentityBefore", mismatchReplay.fluidContextIdentityBefore()),
                    Map.entry("mismatchFluidContextIdentityAfter", mismatchReplay.fluidContextIdentityAfter()),
                    Map.entry("mismatchItemContextChanged", Boolean.toString(mismatchItemContextChanged)),
                    Map.entry("mismatchFluidContextChanged", Boolean.toString(mismatchFluidContextChanged)),
                    Map.entry("mismatchTargetMutation", Long.toString(mismatchTargetDelta)),
                    Map.entry("mismatchLane0ReturnMutation", Long.toString(mismatchLaneZeroReturnDelta)),
                    Map.entry("mismatchLane1ReturnMutation", Long.toString(mismatchLaneOneReturnDelta)),
                    Map.entry("staleGenerationAdopted", Boolean.toString(staleReplay.authorizedGeneration() == refreshedMode.generation())),
                    Map.entry("oldContextPreserved", Boolean.toString(
                            oldContext.owner().logic() == fixture.providerLogic(0))),
                    Map.entry("oldReturnOwnerStable", Boolean.toString(
                            oldContext.owner().inventory() == fixture.nativeRemainderDestination(0))),
                    Map.entry("newReturnOwnerExact", Boolean.toString(
                            newContext.owner().inventory() == fixture.nativeRemainderDestination(1))),
                    Map.entry("claimAuthorized", Boolean.toString(fixture.state() == ProviderTargetState.ACTIVE)),
                    Map.entry("generationDistinct", Boolean.toString(!oldMode.equals(refreshedMode))),
                    Map.entry("falseAcceptance", Boolean.toString(staleAccepted || mismatchAccepted)),
                    Map.entry("resourceGuessing", "false")));
            fixture.close();
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
