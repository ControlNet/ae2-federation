package space.controlnet.ae2federation.test;

import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.policy.PolicyActivationState;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyDelete;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.test.policy.PolicyBridgeFixtures;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;

@PrefixGameTestTemplate(false)
public final class PolicyRevisionGameTests {
    private PolicyRevisionGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 350, required = true, manualOnly = true)
    public static void policyRejectStaleEdit(GameTestHelper helper) {
        runWithBridge(helper, "policyrejectstaleedit", (fixtures, service, key) -> {
            var first = accepted(service.edit(new PolicyEdit(key, PolicyRevision.NONE, PolicyRule.storageDefaults())));
            var second = accepted(service.edit(new PolicyEdit(key, first,
                    PolicyRule.storageDefaults().withEnabled(false))));
            var stale = service.edit(new PolicyEdit(key, first, PolicyRule.storageDefaults()));
            helper.assertTrue(stale instanceof PolicyMutationResult.Rejected, "Older revision must be rejected");
            helper.assertValueEqual(service.revision(key), second, "Stale edit cannot advance authoritative revision");
            helper.assertValueEqual(service.activation(key, PolicyLifecycleGameTests.endpoints(fixtures,
                    space.controlnet.ae2federation.policy.BackendStatus.READY)), PolicyActivationState.OFF,
                    "Stale edit cannot reactivate a disabled rule");
            PolicyEvidence.write("policyrejectstaleedit", 7, Map.of("staleAccepted", "false",
                    "newerRevision", Long.toString(second.value()), "currentRevision", Long.toString(service.revision(key).value()),
                    "enabledAfterStale", "false", "activationAfterStale", "OFF", "casRequired", "true",
                    "authoritativeRevision", "true"));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void policyDeleteReconnect(GameTestHelper helper) {
        var fixtures = new PolicyBridgeFixtures(helper, new BlockPos(5, 3, 5));
        var phase = new int[1];
        var deletedRevision = new PolicyRevision[1];
        helper.succeedWhen(() -> {
            if (phase[0] == 0 && fixtures.networksSettled()) {
                fixtures.placeFirstBridge();
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for deletion fixture Federation Domain");
            }
            if (phase[0] == 1 && fixtures.firstBridgeReady()) {
                var service = PolicyService.get(helper.getLevel());
                var key = PolicyLifecycleGameTests.storageKey(fixtures);
                var configured = accepted(service.edit(new PolicyEdit(key, PolicyRevision.NONE,
                        PolicyRule.storageDefaults())));
                fixtures.removeFirstBridge();
                deletedRevision[0] = accepted(service.delete(new PolicyDelete(key, configured)));
                var stale = service.edit(new PolicyEdit(key, configured, PolicyRule.storageDefaults()));
                helper.assertTrue(stale instanceof PolicyMutationResult.Rejected, "Deleted rule must reject stale resurrection");
                fixtures.placeSecondBridge();
                phase[0] = 2;
                helper.assertTrue(false, "Waiting for reconnect after deletion");
            }
            if (phase[0] != 2 || !fixtures.secondBridgeReady()) {
                helper.assertTrue(false, "Waiting for replacement Federation Domain after deletion");
            }
            var service = PolicyService.get(helper.getLevel());
            var key = PolicyLifecycleGameTests.storageKey(fixtures);
            helper.assertValueEqual(service.activation(key, PolicyLifecycleGameTests.endpoints(fixtures,
                    space.controlnet.ae2federation.policy.BackendStatus.READY)), PolicyActivationState.UNCONFIGURED,
                    "Reconnect cannot resurrect a deleted rule");
            helper.assertValueEqual(service.revision(key), deletedRevision[0], "Tombstone revision must remain authoritative");
            helper.assertValueEqual(service.tombstoneCount(), 1, "Deletion protection must remain sparse and durable");
            PolicyEvidence.write("policydeletereconnect", 8, Map.of("deleted", "true", "staleAccepted", "false",
                    "resurrectedAfterReconnect", "false", "activationAfterReconnect", "UNCONFIGURED",
                    "tombstoneCount", "1", "tombstoneRevision", Long.toString(deletedRevision[0].value()),
                    "ttlEviction", "false", "lruEviction", "false"));
            fixtures.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 350, required = true, manualOnly = true)
    public static void policySparseScale(GameTestHelper helper) {
        runWithBridge(helper, "policysparsescale", (fixtures, service, key) -> {
            service.edit(new PolicyEdit(key, PolicyRevision.NONE, PolicyRule.storageDefaults()));
            var reverse = new PolicyKey(key.providerNetworkId(), key.consumerNetworkId(), PolicyCapability.CRAFTING);
            service.edit(new PolicyEdit(reverse, PolicyRevision.NONE,
                    PolicyRule.enabled(java.util.Set.of(space.controlnet.ae2federation.policy.PolicyOperation.REQUEST))));
            var absentQueries = 10_000;
            for (var index = 1; index <= absentQueries; index++) {
                var absent = new PolicyKey(new NetworkId(new UUID(1, index)), new NetworkId(new UUID(2, index)),
                        PolicyCapability.STORAGE);
                helper.assertTrue(service.configured(absent).isEmpty(), "Absent policy query cannot allocate a record");
            }
            helper.assertValueEqual(service.configuredCount(), 2, "Only explicitly configured directional rules may be stored");
            helper.assertValueEqual(service.tombstoneCount(), 0, "Sparse reads must not create tombstones");
            PolicyEvidence.write("policysparsescale", 6, Map.of("networkPairQueries", Integer.toString(absentQueries),
                    "storedConfigured", "2", "storedTombstones", "0", "allPairsAllocated", "false",
                    "worldScan", "false", "evictionPolicy", "none"));
        });
    }

    private static void runWithBridge(GameTestHelper helper, String testId, BridgeAssertion assertion) {
        var fixtures = new PolicyBridgeFixtures(helper, new BlockPos(5, 3, 5));
        var bridgePlaced = new boolean[1];
        helper.succeedWhen(() -> {
            if (!bridgePlaced[0] && fixtures.networksSettled()) {
                fixtures.placeFirstBridge();
                bridgePlaced[0] = true;
                helper.assertTrue(false, "Waiting for " + testId + " confirmed Federation Domain");
            }
            if (!fixtures.firstBridgeReady()) {
                helper.assertTrue(false, "Waiting for " + testId + " identities and Federation Domain");
            }
            assertion.verify(fixtures, PolicyService.get(helper.getLevel()), PolicyLifecycleGameTests.storageKey(fixtures));
            fixtures.close();
        });
    }

    private static PolicyRevision accepted(PolicyMutationResult result) {
        return ((PolicyMutationResult.Accepted) result).revision();
    }

    @FunctionalInterface
    private interface BridgeAssertion {
        void verify(PolicyBridgeFixtures fixtures, PolicyService service, PolicyKey key);
    }
}
