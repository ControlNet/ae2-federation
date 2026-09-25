package space.controlnet.ae2federation.test;

import java.util.Map;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.processing.claim.ClaimEpoch;
import space.controlnet.ae2federation.processing.claim.ClaimRequest;
import space.controlnet.ae2federation.processing.claim.ClaimResult;
import space.controlnet.ae2federation.processing.claim.ClaimStateCodec;
import space.controlnet.ae2federation.processing.claim.EndpointIdentity;
import space.controlnet.ae2federation.processing.claim.EndpointOwnerIdentity;
import space.controlnet.ae2federation.processing.provider.ProviderFace;
import space.controlnet.ae2federation.processing.provider.ProviderIdentity;
import space.controlnet.ae2federation.processing.provider.ProviderNodeWiring;
import space.controlnet.ae2federation.processing.provider.ProviderTargetState;
import space.controlnet.ae2federation.test.processing.ProviderTargetRuntimeFixtures;
import space.controlnet.ae2federation.test.processing.claim.ProviderClaimEvidence;

@PrefixGameTestTemplate(false)
public final class ProviderClaimGameTests {
    private ProviderClaimGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void providerOrientation(GameTestHelper helper) {
        runFixture(helper, fixture -> {
            var wiring = fixture.runtime().wiring();
            helper.assertValueEqual(wiring.orientation().nativeFaces().size(), 5,
                    "Exactly five native faces must remain");
            helper.assertTrue(wiring.exposedNode(Direction.EAST) == null,
                    "Federation face must expose no native node");
            var nodes = wiring.orientation().nativeFaces().stream()
                    .map(face -> wiring.exposedNode(ProviderNodeWiring.direction(face))).collect(java.util.stream.Collectors.toSet());
            helper.assertValueEqual(nodes.size(), 1, "Five native faces must expose one physical node");
            helper.assertTrue(nodes.iterator().next() != null, "Native faces must expose the source node");
            helper.assertTrue(wiring.identity().equals(fixture.providerIdentity()),
                    "Provider identity must be authoritative");
            helper.assertValueEqual(fixture.bindingCount(), 3, "Production runtime must bind every native Lane");
            helper.assertTrue(fixture.enablePolicy(Set.of(PolicyOperation.EXECUTE, PolicyOperation.SUPPLY)),
                    "Processing Policy must be configured");
            fixture.connectFederationDomain();
            helper.assertTrue(fixture.pushOnce(), "Authorized production binding must accept the native push");
            helper.assertValueEqual(fixture.state(), ProviderTargetState.ACTIVE,
                    "Authorized native target must be active");
            helper.assertValueEqual(fixture.targetItemCount(), 1L,
                    "Native target must receive the exact Provider input");
            helper.assertValueEqual(fixture.mixinLookupCount(), 2,
                    "Native push must resolve the bound target for simulation and modulation");
            helper.assertValueEqual(fixture.nativeTargetLookupCount(), 2,
                    "Authorized Mixin lookup must resolve the native target for both insertion phases");
            ProviderClaimEvidence.write("providerorientation", 12, Map.ofEntries(
                    Map.entry("nativeFaces", "5"), Map.entry("nativeNodes", "1"),
                    Map.entry("federationNativeNode", "false"), Map.entry("providerIdentityStable", "true"),
                    Map.entry("nativeGrid", "present"), Map.entry("runtimeBinding", "true"),
                    Map.entry("endpointCapability", "true"), Map.entry("authorizationState", "ACTIVE"),
                    Map.entry("mixinLookups", "2"), Map.entry("nativeTargetLookups", "2"),
                    Map.entry("targetMutation", "1")));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void claimCompete(GameTestHelper helper) {
        runFixture(helper, fixture -> {
            var owner = new EndpointOwnerIdentity(fixture.providerIdentity());
            var competitor = new EndpointOwnerIdentity(ProviderIdentity.create());
            var loser = fixture.claims().compareAndSet(new ClaimRequest(fixture.endpointIdentity(),
                    fixture.claims().state().epoch(), competitor));
            helper.assertTrue(loser instanceof ClaimResult.Rejected,
                    "Current-epoch competitor must not steal ownership");
            helper.assertValueEqual(fixture.claims().state().owner().orElseThrow(), owner,
                    "Winner must remain authoritative");
            helper.assertValueEqual(fixture.claims().state().epoch(), new ClaimEpoch(1),
                    "Claim epoch must remain one");
            var restored = ClaimStateCodec.load(ClaimStateCodec.save(fixture.claims().state()));
            helper.assertValueEqual(restored, fixture.claims().state(), "Owned Claim identity and epoch must persist");
            fixture.useClaimEpoch(new ClaimEpoch(2));
            helper.assertTrue(!fixture.push(), "Stale Claim epoch must deny the native Lane target");
            helper.assertValueEqual(fixture.state(), ProviderTargetState.CLAIM_MISMATCH,
                    "Stale epoch must fail at Claim authorization");
            helper.assertValueEqual(fixture.mixinLookupCount(), 1, "Claim denial must enter the bound Mixin lookup");
            helper.assertValueEqual(fixture.nativeTargetLookupCount(), 0,
                    "Claim denial must not call native target lookup or fallback");
            ProviderClaimEvidence.write("claimcompete", 8, Map.ofEntries(
                    Map.entry("winners", "1"), Map.entry("competitorAccepted", "false"),
                    Map.entry("claimEpoch", "1"), Map.entry("persistedOwner", "true"),
                    Map.entry("atomicCas", "true"), Map.entry("runtimeBinding", "true"),
                    Map.entry("authorizationState", "CLAIM_MISMATCH"), Map.entry("mixinLookups", "1"),
                    Map.entry("nativeTargetLookups", "0")));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void claimOfflineOwner(GameTestHelper helper) {
        runFixture(helper, fixture -> {
            var owner = fixture.claims().state().owner().orElseThrow();
            fixture.claims().withOnline(false);
            fixture.unbindEndpoint();
            helper.assertTrue(!fixture.push(), "Offline Endpoint must deny the bound native target");
            helper.assertValueEqual(fixture.state(), ProviderTargetState.NATIVE_TARGET_UNAVAILABLE,
                    "Missing Endpoint capability must fail closed");
            helper.assertValueEqual(fixture.claims().state().owner().orElseThrow(), owner,
                    "Offline owner must remain authoritative");
            helper.assertValueEqual(fixture.claims().state().epoch(), new ClaimEpoch(1),
                    "Offline state must not advance Claim epoch");
            helper.assertValueEqual(fixture.mixinLookupCount(), 1, "Offline denial must enter the bound Mixin lookup");
            helper.assertValueEqual(fixture.nativeTargetLookupCount(), 0,
                    "Offline denial must not search a fallback target");
            ProviderClaimEvidence.write("claimofflineowner", 6, Map.ofEntries(
                    Map.entry("ownerPreserved", "true"), Map.entry("autoSteal", "false"),
                    Map.entry("ttlExpiry", "false"), Map.entry("claimEpoch", "1"),
                    Map.entry("offline", "true"), Map.entry("runtimeBinding", "true"),
                    Map.entry("authorizationState", "NATIVE_TARGET_UNAVAILABLE"),
                    Map.entry("capabilityLookups", "2"), Map.entry("mixinLookups", "1"),
                    Map.entry("nativeTargetLookups", "0")));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void claimOverlap(GameTestHelper helper) {
        runFixture(helper, fixture -> {
            helper.assertTrue(fixture.enablePolicy(Set.of(PolicyOperation.EXECUTE, PolicyOperation.SUPPLY)),
                    "Processing Policy must be configured before overlap");
            fixture.connectFederationDomain();
            fixture.overlapWith(EndpointIdentity.create());
            helper.assertTrue(!fixture.push(), "Overlapping Endpoint target domain must deny native dispatch");
            helper.assertValueEqual(fixture.state(), ProviderTargetState.OVERLAPPING_SUBNET,
                    "Overlap must pause the affected Lane");
            helper.assertValueEqual(fixture.targetItemCount(), 0L, "Overlap denial must not mutate the target");
            helper.assertValueEqual(fixture.mixinLookupCount(), 1, "Overlap denial must enter the Mixin lookup");
            helper.assertValueEqual(fixture.nativeTargetLookupCount(), 0,
                    "Overlap denial must not call native target lookup or retarget");
            ProviderClaimEvidence.write("claimoverlap", 6, Map.ofEntries(
                    Map.entry("initialDomains", "2"), Map.entry("mergedDomains", "1"),
                    Map.entry("pausedLanes", "2"), Map.entry("retargeted", "false"),
                    Map.entry("overlapDetected", "true"), Map.entry("runtimeBinding", "true"),
                    Map.entry("authorizationState", "OVERLAPPING_SUBNET"), Map.entry("mixinLookups", "1"),
                    Map.entry("nativeTargetLookups", "0")));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void providerRotatePending(GameTestHelper helper) {
        runFixture(helper, fixture -> {
            var identity = fixture.providerIdentity();
            var claim = fixture.claims().state();
            var remainder = fixture.nativeRemainderDestination();
            fixture.rotate(ProviderFace.NORTH);
            helper.assertTrue(!fixture.push(), "Unsettled rotation must deny native target dispatch");
            helper.assertValueEqual(fixture.runtime().wiring().identity(), identity,
                    "Rotation must preserve Provider identity");
            helper.assertValueEqual(fixture.claims().state(), claim, "Rotation must preserve Claim identity and epoch");
            helper.assertTrue(fixture.nativeRemainderDestination() == remainder,
                    "Rotation must preserve native remainder destination object");
            helper.assertValueEqual(fixture.state(), ProviderTargetState.ROTATION_PENDING,
                    "Rotation must pause target activation");
            helper.assertTrue(!fixture.runtime().wiring().settled(),
                    "Physical observations must remain unsettled after rotation");
            helper.assertValueEqual(fixture.mixinLookupCount(), 1, "Rotation denial must enter the bound Mixin lookup");
            helper.assertValueEqual(fixture.nativeTargetLookupCount(), 0,
                    "Rotation denial must not retarget or search fallback positions");
            ProviderClaimEvidence.write("providerrotatepending", 8, Map.ofEntries(
                    Map.entry("providerIdentityStable", "true"), Map.entry("claimStable", "true"),
                    Map.entry("claimEpochStable", "true"), Map.entry("remainderOwnerStable", "true"),
                    Map.entry("rotationPending", "true"), Map.entry("retargeted", "false"),
                    Map.entry("runtimeBinding", "true"), Map.entry("authorizationState", "ROTATION_PENDING"),
                    Map.entry("mixinLookups", "1"), Map.entry("nativeTargetLookups", "0")));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void providerRejectSameGrid(GameTestHelper helper) {
        runFixture(helper, fixture -> {
            helper.assertTrue(!fixture.push(), "Missing Processing Policy must deny native dispatch");
            helper.assertValueEqual(fixture.state(), ProviderTargetState.POLICY_DENIED,
                    "Missing Policy must fail closed");
            helper.assertTrue(fixture.enablePolicy(Set.of(PolicyOperation.EXECUTE)),
                    "Incomplete Processing Policy must be configured");
            helper.assertTrue(!fixture.push(), "Policy without SUPPLY must deny native dispatch");
            helper.assertValueEqual(fixture.state(), ProviderTargetState.POLICY_DENIED,
                    "Missing SUPPLY must fail closed");
            helper.assertTrue(fixture.enablePolicy(Set.of(PolicyOperation.EXECUTE, PolicyOperation.SUPPLY)),
                    "Complete Processing Policy must be configured");
            helper.assertTrue(!fixture.push(), "Missing common Federation Domain must deny native dispatch");
            helper.assertValueEqual(fixture.state(), ProviderTargetState.FEDERATION_DOMAIN_DISCONNECTED,
                    "Disconnected Federation Domain must fail closed");
            var capabilityLookups = fixture.capabilityLookupCount();
            fixture.useUnloadedTarget();
            helper.assertTrue(!fixture.push(), "Unloaded Endpoint position must deny native dispatch");
            helper.assertValueEqual(fixture.state(), ProviderTargetState.ENDPOINT_OFFLINE,
                    "Unloaded Endpoint must fail before capability lookup");
            helper.assertValueEqual(fixture.capabilityLookupCount(), capabilityLookups,
                    "Unloaded target must not query its capability or force-load");
            fixture.restoreTargetRequest();
            fixture.joinSourceAndTargetGrids();
            helper.assertTrue(!fixture.push(), "Same native Grid must deny target activation");
            helper.assertValueEqual(fixture.state(), ProviderTargetState.SAME_SOURCE_GRID,
                    "Same native Grid must fail before Policy/Federation Domain activation");
            helper.assertValueEqual(fixture.targetItemCount(), 0L, "Every denied route must leave target unchanged");
            helper.assertValueEqual(fixture.mixinLookupCount(), 5, "Every denial must enter the bound Mixin lookup");
            helper.assertValueEqual(fixture.nativeTargetLookupCount(), 0,
                    "Denied routes must never call native target lookup or fallback");
            ProviderClaimEvidence.write("providerrejectsamegrid", 17, Map.ofEntries(
                    Map.entry("sameGridAccepted", "false"), Map.entry("separatedAccepted", "true"),
                    Map.entry("offlineRetargeted", "false"), Map.entry("targetMutation", "0"),
                    Map.entry("runtimeBinding", "true"), Map.entry("policyDeniedAttempts", "2"),
                    Map.entry("federationDomainDeniedAttempts", "1"), Map.entry("unloadedDeniedAttempts", "1"),
                    Map.entry("unloadedCapabilityLookupDelta", "0"), Map.entry("sameGridDeniedAttempts", "1"),
                    Map.entry("mixinLookups", "5"), Map.entry("nativeTargetLookups", "0")));
        });
    }

    private static void runFixture(GameTestHelper helper,
            java.util.function.Consumer<ProviderTargetRuntimeFixtures> assertions) {
        var fixture = new ProviderTargetRuntimeFixtures(helper);
        helper.succeedWhen(() -> {
            if (!fixture.initialize()) {
                helper.assertTrue(false, "Waiting for settled production Provider and Endpoint target bindings: "
                        + fixture.status());
            }
            assertions.accept(fixture);
            fixture.close();
        });
    }
}
