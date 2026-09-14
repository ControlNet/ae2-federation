package space.controlnet.ae2federation.test;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.persistence.PolicySavedData;
import space.controlnet.ae2federation.policy.BackendStatus;
import space.controlnet.ae2federation.policy.PolicyActivationState;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyRuntimeEndpoints;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.test.policy.PolicyBridgeFixtures;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;
import space.controlnet.ae2federation.test.policy.PolicyRestartState;

@PrefixGameTestTemplate(false)
public final class PolicyLifecycleGameTests {
    private PolicyLifecycleGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 500, required = true, manualOnly = true)
    public static void policyLifecycleMatrix(GameTestHelper helper) {
        switch (System.getProperty("ae2federation.policyPhase", "")) {
            case "prepare" -> prepareRestart(helper);
            case "verify" -> verifyLifecycle(helper);
            default -> helper.fail("Unknown Policy restart phase");
        }
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void policyNewBridgeRestore(GameTestHelper helper) {
        var fixtures = new PolicyBridgeFixtures(helper, new BlockPos(5, 3, 5));
        var phase = new int[1];
        var revision = new PolicyRevision[1];
        var firstIdentity = new int[1];
        helper.succeedWhen(() -> {
            if (phase[0] == 0 && fixtures.networksSettled()) {
                fixtures.placeFirstBridge();
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for first confirmed direct Fabric");
            }
            if (phase[0] == 1 && fixtures.firstBridgeReady()) {
                var key = storageKey(fixtures);
                revision[0] = accepted(PolicyService.get(helper.getLevel()).edit(
                        new PolicyEdit(key, PolicyRevision.NONE, PolicyRule.storageDefaults())));
                firstIdentity[0] = fixtures.firstBridgeIdentity();
                fixtures.removeFirstBridge();
                helper.assertValueEqual(activation(helper, fixtures, key), PolicyActivationState.DISCONNECTED,
                        "Removing the old Bridge must disconnect without deleting Policy");
                fixtures.placeSecondBridge();
                phase[0] = 2;
                helper.assertTrue(false, "Waiting for replacement Bridge Fabric");
            }
            if (phase[0] != 2 || !fixtures.secondBridgeReady()) {
                helper.assertTrue(false, "Waiting for replacement Bridge identities to settle");
            }
            var key = storageKey(fixtures);
            helper.assertTrue(firstIdentity[0] != fixtures.secondBridgeIdentity(), "Replacement must be a new Bridge object");
            helper.assertValueEqual(activation(helper, fixtures, key), PolicyActivationState.ACTIVE,
                    "Identity-keyed Policy must activate through a new confirmed Fabric");
            helper.assertValueEqual(PolicyService.get(helper.getLevel()).revision(key), revision[0],
                    "Fabric replacement must not rewrite Policy revision");
            PolicyEvidence.write("policynewbridgerestore", 8, Map.of("disconnectedAfterRemoval", "true",
                    "newBridgeObject", "true", "restoredState", "ACTIVE", "revisionPreserved", "true",
                    "keyedByNetworkIds", "true", "fabricIdPersisted", "false", "reexportDefault", "false",
                    "nativeGridJoin", "false"));
            fixtures.close();
        });
    }

    private static void prepareRestart(GameTestHelper helper) {
        var fixtures = new PolicyBridgeFixtures(helper, new BlockPos(5, 3, 5));
        var bridgePlaced = new boolean[1];
        helper.succeedWhen(() -> {
            if (!bridgePlaced[0] && fixtures.networksSettled()) {
                fixtures.placeFirstBridge();
                bridgePlaced[0] = true;
                helper.assertTrue(false, "Waiting for restart preparation Fabric");
            }
            if (!fixtures.firstBridgeReady()) {
                helper.assertTrue(false, "Waiting for confirmed restart preparation Fabric");
            }
            var key = storageKey(fixtures);
            var revision = accepted(PolicyService.get(helper.getLevel()).edit(
                    new PolicyEdit(key, PolicyRevision.NONE, PolicyRule.storageDefaults())));
            var processId = ProcessHandle.current().pid();
            new PolicyRestartState(key, revision, processId).write();
            PolicyEvidence.trace("policylifecyclematrix", "prepareProcessId", Long.toString(processId));
            fixtures.close();
        });
    }

    private static void verifyLifecycle(GameTestHelper helper) {
        var restart = PolicyRestartState.read();
        var restored = PolicySavedData.get(helper.getLevel()).configured(restart.key()).orElseThrow();
        var fixtures = new PolicyBridgeFixtures(helper, new BlockPos(5, 3, 5));
        var phase = new int[1];
        helper.succeedWhen(() -> {
            if (phase[0] == 0 && fixtures.networksSettled()) {
                fixtures.placeFirstBridge();
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for lifecycle matrix Fabric");
            }
            if (phase[0] != 1 || !fixtures.firstBridgeReady()) {
                helper.assertTrue(false, "Waiting for confirmed lifecycle matrix Fabric");
            }
            var service = PolicyService.get(helper.getLevel());
            var key = storageKey(fixtures);
            helper.assertValueEqual(service.activation(key, endpoints(fixtures, BackendStatus.READY)),
                    PolicyActivationState.UNCONFIGURED, "Absent rule must remain unconfigured");
            var offRevision = accepted(service.edit(new PolicyEdit(key, PolicyRevision.NONE,
                    PolicyRule.storageDefaults().withEnabled(false))));
            helper.assertValueEqual(service.activation(key, endpoints(fixtures, BackendStatus.READY)),
                    PolicyActivationState.OFF, "Disabled rule must remain off");
            var enabledRevision = accepted(service.edit(new PolicyEdit(key, offRevision, PolicyRule.storageDefaults())));
            helper.assertValueEqual(service.activation(key, endpoints(fixtures, BackendStatus.UNREADY)),
                    PolicyActivationState.BACKEND_UNREADY, "Confirmed topology cannot hide backend unavailability");
            helper.assertValueEqual(service.activation(key, endpoints(fixtures, BackendStatus.READY)),
                    PolicyActivationState.ACTIVE, "Settled identities and confirmed common Fabric must activate");
            fixtures.removeFirstBridge();
            helper.assertValueEqual(service.activation(key, endpoints(fixtures, BackendStatus.READY)),
                    PolicyActivationState.DISCONNECTED, "Fabric removal must disconnect immediately");
            var verifyProcessId = ProcessHandle.current().pid();
            helper.assertTrue(verifyProcessId != restart.prepareProcessId(), "Restart phases must use distinct JVM processes");
            helper.assertValueEqual(restored.revision(), restart.revision(), "Policy revision must survive restart");
            PolicyEvidence.write("policylifecyclematrix", 12, Map.ofEntries(
                    Map.entry("unconfiguredState", "UNCONFIGURED"), Map.entry("offState", "OFF"),
                    Map.entry("disconnectedState", "DISCONNECTED"), Map.entry("backendUnreadyState", "BACKEND_UNREADY"),
                    Map.entry("activeState", "ACTIVE"), Map.entry("restartRestored", "true"),
                    Map.entry("prepareProcessId", Long.toString(restart.prepareProcessId())),
                    Map.entry("verifyProcessId", Long.toString(verifyProcessId)),
                    Map.entry("processesDistinct", "true"), Map.entry("persistedRevision", Long.toString(restored.revision().value())),
                    Map.entry("runtimeStatePersisted", "false"), Map.entry("finalRevision", Long.toString(enabledRevision.value()))));
            fixtures.close();
        });
    }

    static PolicyKey storageKey(PolicyBridgeFixtures fixtures) {
        return new PolicyKey(fixtures.mainNetwork(), fixtures.outerNetwork(), PolicyCapability.STORAGE);
    }

    static PolicyRuntimeEndpoints endpoints(PolicyBridgeFixtures fixtures, BackendStatus backend) {
        return new PolicyRuntimeEndpoints(fixtures.mainGrid(), fixtures.outerGrid(), backend);
    }

    static PolicyActivationState activation(GameTestHelper helper, PolicyBridgeFixtures fixtures, PolicyKey key) {
        return PolicyService.get(helper.getLevel()).activation(key, endpoints(fixtures, BackendStatus.READY));
    }

    static PolicyRevision accepted(PolicyMutationResult result) {
        return ((PolicyMutationResult.Accepted) result).revision();
    }
}
