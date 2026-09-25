package space.controlnet.ae2federation.test;

import appeng.api.stacks.AEItemKey;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.processing.provider.ProviderTargetState;
import space.controlnet.ae2federation.test.processing.ProviderRuntimeReplayControl;
import space.controlnet.ae2federation.test.processing.ProviderTargetRuntimeFixtures;
import space.controlnet.ae2federation.test.processing.endpoint.EndpointModeEvidence;

@PrefixGameTestTemplate(false)
public final class EndpointAuthorizationGameTests {
    private EndpointAuthorizationGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 240, required = true, manualOnly = true)
    public static void endpointRejectModeTakeover(GameTestHelper helper) {
        var fixture = new ProviderTargetRuntimeFixtures(helper, true);
        helper.succeedWhen(() -> runTakeoverAssertions(helper, fixture));
    }

    private static void runTakeoverAssertions(GameTestHelper helper, ProviderTargetRuntimeFixtures fixture) {
        helper.assertTrue(fixture.initialize(), "Waiting for production Provider and Endpoint runtime");
        helper.assertTrue(fixture.enablePolicy(java.util.Set.of(PolicyOperation.EXECUTE, PolicyOperation.SUPPLY)),
                "Federated mode requires Processing Policy");
        fixture.connectFederationDomain();
        helper.assertTrue(fixture.pushOnce(0), "The first native Lane must bind through production authorization");
        var oldContext = fixture.endpointBinding().runtime().itemReturnContext().orElseThrow();
        var oldMode = oldContext.owner().mode();
        fixture.fillTarget(AEItemKey.of(Items.COBBLESTONE));
        var rejectedTargetBefore = fixture.targetSnapshot();
        helper.assertTrue(!fixture.pushLane(1), "A different valid Lane must reject against a saturated target");
        helper.assertTrue(fixture.endpointBinding().runtime().itemReturnContext().orElseThrow() == oldContext,
                "A rejected Lane must not replace the outstanding return owner");
        helper.assertValueEqual(fixture.targetSnapshot(), rejectedTargetBefore,
                "A rejected Lane must not mutate the Endpoint target");
        helper.assertValueEqual(fixture.nativeRemainderAmount(1, 0), 0L,
                "A rejected Lane must create no alternate return responsibility");
        fixture.clearTarget(AEItemKey.of(Items.COBBLESTONE));
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
        var staleTargetDelta = fixture.targetItemCount() - staleTargetBefore;
        var staleLaneZeroReturnDelta = fixture.nativeRemainderAmount(0, 0) - staleLaneZeroReturnBefore;
        var staleLaneOneReturnDelta = fixture.nativeRemainderAmount(1, 0) - staleLaneOneReturnBefore;
        helper.assertTrue(!staleAccepted, "A pre-cycle authorization must be rejected");
        helper.assertTrue(staleReplay.mixinOwnerIdentity().equals(staleReplay.expectedLogicIdentity())
                && staleReplay.cacheOwnerIdentity().equals(staleReplay.expectedLogicIdentity())
                && staleReplay.cacheTargetFoundCount() == 0 && staleNativeTargetsFoundDelta == 1,
                "Stale denial must observe but never accept the native target");
        helper.assertTrue(staleReplay.itemContextIdentityAfter().equals("0")
                && staleReplay.fluidContextIdentityAfter().equals("0"),
                "Stale authorization must not install return contexts");
        helper.assertTrue(staleTargetDelta == 0 && staleLaneZeroReturnDelta == 0 && staleLaneOneReturnDelta == 0,
                "Stale authorization must mutate no target or Lane return inventory");

        helper.assertTrue(fixture.pushOnce(1), "A second native Lane must resolve fresh authorization");
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
        var mismatchTargetDelta = fixture.targetItemCount() - mismatchTargetBefore;
        var mismatchLaneZeroReturnDelta = fixture.nativeRemainderAmount(0, 0) - mismatchLaneZeroReturnBefore;
        var mismatchLaneOneReturnDelta = fixture.nativeRemainderAmount(1, 0) - mismatchLaneOneReturnBefore;
        helper.assertTrue(!mismatchAccepted, "Another Lane's provenance must be rejected");
        helper.assertTrue(mismatchReplay.mixinOwnerIdentity().equals(mismatchReplay.expectedLogicIdentity())
                && mismatchReplay.cacheOwnerIdentity().equals(mismatchReplay.expectedLogicIdentity())
                && mismatchReplay.cacheTargetFoundCount() == 0 && mismatchNativeTargetsFoundDelta == 0,
                "Provenance denial must retain exact identities and zero found targets");
        helper.assertTrue(fixture.endpointBinding().runtime().itemReturnContext().orElseThrow()
                == itemContextBeforeMismatch && fixture.endpointBinding().runtime().fluidReturnContext().orElseThrow()
                == fluidContextBeforeMismatch, "Mismatched provenance must not replace return contexts");
        helper.assertTrue(mismatchTargetDelta == 0 && mismatchLaneZeroReturnDelta == 0
                && mismatchLaneOneReturnDelta == 0, "Mismatched provenance must mutate no owned resources");
        helper.assertTrue(oldContext.owner().logic() == fixture.providerLogic(0)
                && newContext.owner().inventory() == fixture.nativeRemainderDestination(1),
                "Each return context must retain its exact authorized Lane");
        helper.assertTrue(oldContext.capability().insertItem(0, new ItemStack(Items.IRON_INGOT), false).isEmpty(),
                "Outstanding context must delegate to its original Lane");
        helper.assertTrue(newContext.capability().insertItem(0, new ItemStack(Items.GOLD_INGOT), false).isEmpty(),
                "Fresh context must delegate to its authorized Lane");
        helper.assertValueEqual(fixture.providerLogic(0).getReturnInv().getAmount(0), 1L,
                "Old return must remain with the first Lane");
        helper.assertValueEqual(fixture.providerLogic(1).getReturnInv().getAmount(0), 1L,
                "New return must remain with the second Lane");
        writeEvidence(fixture, oldContext.owner().logic() == fixture.providerLogic(0),
                oldContext.owner().inventory() == fixture.nativeRemainderDestination(0),
                newContext.owner().inventory() == fixture.nativeRemainderDestination(1), refreshedMode.generation(),
                !oldMode.equals(refreshedMode), staleAccepted, staleReplay,
                staleMixinLookupDelta, staleNativeTargetLookupDelta, staleNativeTargetsFoundDelta, staleTargetDelta,
                staleLaneZeroReturnDelta, staleLaneOneReturnDelta, mismatchAccepted, mismatchReplay,
                mismatchMixinLookupDelta, mismatchNativeTargetLookupDelta, mismatchNativeTargetsFoundDelta,
                mismatchTargetDelta, mismatchLaneZeroReturnDelta, mismatchLaneOneReturnDelta);
        fixture.close();
    }

    private static void writeEvidence(ProviderTargetRuntimeFixtures fixture, boolean oldContextPreserved,
            boolean oldReturnOwnerStable, boolean newReturnOwnerExact, long generation, boolean generationDistinct,
            boolean staleAccepted, ProviderRuntimeReplayControl.ReplayTrace stale, int staleMixinDelta,
            int staleLookupDelta, int staleFoundDelta, long staleTargetDelta, long staleLane0Delta,
            long staleLane1Delta, boolean mismatchAccepted, ProviderRuntimeReplayControl.ReplayTrace mismatch,
            int mismatchMixinDelta, int mismatchLookupDelta, int mismatchFoundDelta, long mismatchTargetDelta,
            long mismatchLane0Delta, long mismatchLane1Delta) {
        EndpointModeEvidence.write("endpointrejectmodetakeover", 10, Map.ofEntries(
                Map.entry("preCycleGeneration", Long.toString(stale.authorizedGeneration())),
                Map.entry("currentGeneration", Long.toString(generation)),
                Map.entry("staleExpectedLogicIdentity", stale.expectedLogicIdentity()),
                Map.entry("staleExpectedProvenanceIdentity", stale.expectedProvenanceIdentity()),
                Map.entry("staleActualProvenanceIdentity", stale.actualProvenanceIdentity()),
                Map.entry("staleAuthorizedGeneration", Long.toString(stale.authorizedGeneration())),
                Map.entry("staleResolverOwnerIdentity", stale.resolverOwnerIdentity()),
                Map.entry("staleResolverInvocationCount", Integer.toString(stale.resolverInvocationCount())),
                Map.entry("staleMixinOwnerIdentity", stale.mixinOwnerIdentity()),
                Map.entry("staleAuthorizationAccepted", Boolean.toString(staleAccepted)),
                Map.entry("staleMixinLookupDelta", Integer.toString(staleMixinDelta)),
                Map.entry("staleObservedMixinLookupCount", Integer.toString(stale.mixinLookupCount())),
                Map.entry("staleCacheOwnerIdentity", stale.cacheOwnerIdentity()),
                Map.entry("staleObservedCacheLookupCount", Integer.toString(stale.cacheLookupCount())),
                Map.entry("staleCacheTargetFoundCount", Integer.toString(stale.cacheTargetFoundCount())),
                Map.entry("staleNativeTargetLookupDelta", Integer.toString(staleLookupDelta)),
                Map.entry("staleNativeTargetsFoundDelta", Integer.toString(staleFoundDelta)),
                Map.entry("staleItemContextIdentityBefore", stale.itemContextIdentityBefore()),
                Map.entry("staleItemContextIdentityAfter", stale.itemContextIdentityAfter()),
                Map.entry("staleFluidContextIdentityBefore", stale.fluidContextIdentityBefore()),
                Map.entry("staleFluidContextIdentityAfter", stale.fluidContextIdentityAfter()),
                Map.entry("staleItemContextInstalled", Boolean.toString(!stale.itemContextIdentityAfter().equals("0"))),
                Map.entry("staleFluidContextInstalled", Boolean.toString(!stale.fluidContextIdentityAfter().equals("0"))),
                Map.entry("staleTargetMutation", Long.toString(staleTargetDelta)),
                Map.entry("staleLane0ReturnMutation", Long.toString(staleLane0Delta)),
                Map.entry("staleLane1ReturnMutation", Long.toString(staleLane1Delta)),
                Map.entry("mismatchExpectedLogicIdentity", mismatch.expectedLogicIdentity()),
                Map.entry("mismatchExpectedProvenanceIdentity", mismatch.expectedProvenanceIdentity()),
                Map.entry("mismatchActualProvenanceIdentity", mismatch.actualProvenanceIdentity()),
                Map.entry("mismatchAuthorizedGeneration", Long.toString(mismatch.authorizedGeneration())),
                Map.entry("mismatchResolverOwnerIdentity", mismatch.resolverOwnerIdentity()),
                Map.entry("mismatchResolverInvocationCount", Integer.toString(mismatch.resolverInvocationCount())),
                Map.entry("mismatchMixinOwnerIdentity", mismatch.mixinOwnerIdentity()),
                Map.entry("mismatchAuthorizationAccepted", Boolean.toString(mismatchAccepted)),
                Map.entry("mismatchMixinLookupDelta", Integer.toString(mismatchMixinDelta)),
                Map.entry("mismatchObservedMixinLookupCount", Integer.toString(mismatch.mixinLookupCount())),
                Map.entry("mismatchCacheOwnerIdentity", mismatch.cacheOwnerIdentity()),
                Map.entry("mismatchObservedCacheLookupCount", Integer.toString(mismatch.cacheLookupCount())),
                Map.entry("mismatchCacheTargetFoundCount", Integer.toString(mismatch.cacheTargetFoundCount())),
                Map.entry("mismatchNativeTargetLookupDelta", Integer.toString(mismatchLookupDelta)),
                Map.entry("mismatchNativeTargetsFoundDelta", Integer.toString(mismatchFoundDelta)),
                Map.entry("mismatchItemContextIdentityBefore", mismatch.itemContextIdentityBefore()),
                Map.entry("mismatchItemContextIdentityAfter", mismatch.itemContextIdentityAfter()),
                Map.entry("mismatchFluidContextIdentityBefore", mismatch.fluidContextIdentityBefore()),
                Map.entry("mismatchFluidContextIdentityAfter", mismatch.fluidContextIdentityAfter()),
                Map.entry("mismatchItemContextChanged", Boolean.toString(
                        !mismatch.itemContextIdentityBefore().equals(mismatch.itemContextIdentityAfter()))),
                Map.entry("mismatchFluidContextChanged", Boolean.toString(
                        !mismatch.fluidContextIdentityBefore().equals(mismatch.fluidContextIdentityAfter()))),
                Map.entry("mismatchTargetMutation", Long.toString(mismatchTargetDelta)),
                Map.entry("mismatchLane0ReturnMutation", Long.toString(mismatchLane0Delta)),
                Map.entry("mismatchLane1ReturnMutation", Long.toString(mismatchLane1Delta)),
                Map.entry("staleGenerationAdopted", Boolean.toString(stale.authorizedGeneration() == generation)),
                Map.entry("oldContextPreserved", Boolean.toString(oldContextPreserved)),
                Map.entry("oldReturnOwnerStable", Boolean.toString(oldReturnOwnerStable)),
                Map.entry("newReturnOwnerExact", Boolean.toString(newReturnOwnerExact)),
                Map.entry("claimAuthorized", Boolean.toString(fixture.state() == ProviderTargetState.ACTIVE)),
                Map.entry("generationDistinct", Boolean.toString(generationDistinct)),
                Map.entry("falseAcceptance", Boolean.toString(staleAccepted || mismatchAccepted)),
                Map.entry("resourceGuessing", "false")));
    }
}
