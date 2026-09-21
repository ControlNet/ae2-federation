package space.controlnet.ae2federation.test;

import java.util.Arrays;
import java.util.Map;
import net.minecraft.gametest.framework.GameTestHelper;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.observability.LevelObservabilityService;
import space.controlnet.ae2federation.observability.state.FabricStateDelta;
import space.controlnet.ae2federation.observability.state.FabricStateSnapshot;
import space.controlnet.ae2federation.observability.state.ObservationDeltaEnvelope;
import space.controlnet.ae2federation.observability.state.ObservationSession;
import space.controlnet.ae2federation.observability.state.ObservationSnapshotEnvelope;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.test.energy.NativeEnergyEvidence;

final class CrossFabricObservationProbe {
    private final GameTestHelper helper;
    private final RealObservationScene scene;
    private int phase;
    private long removalsBefore;

    CrossFabricObservationProbe(GameTestHelper helper) {
        this.helper = helper;
        scene = new RealObservationScene(helper, true);
        ObservationRuntimeEvidence.reset("observerejectcrossfabricedit");
        ObservationRuntimeEvidence.useHeadlessGameTestTransport();
    }

    void verify() {
        helper.assertTrue(scene.ready(), "Waiting for two real Fabrics: " + scene.status());
        if (phase == 0) {
            scene.mutateObservedState();
            phase = 1;
            helper.assertTrue(false, "Waiting for observed Processing state");
        }
        if (phase == 1) {
            helper.assertTrue(scene.openFirstMenu(), "Production Fabric A menu must open");
            phase = 2;
            helper.assertTrue(false, "Waiting for Fabric A menu snapshot");
        }
        if (phase == 2) {
            helper.assertTrue(scene.openSecondMenu(), "Production Fabric B menu must open");
            phase = 3;
            helper.assertTrue(false, "Waiting for Fabric B menu snapshot");
        }
        if (phase == 3) {
            attack();
            scene.closeFirstMenu();
            scene.closeSecondMenu();
            phase = 4;
            helper.assertTrue(false, "Waiting for production menu cleanup");
        }
        var subscriptions = LevelObservabilityService.get(helper.getLevel()).subscriptions();
        helper.assertValueEqual(subscriptions.activeCount(), 0, "Both real menus must release subscriptions");
        helper.assertValueEqual(subscriptions.removalCount() - removalsBefore, 2L,
                "Both production subscriptions must be removed exactly once");
        NativeEnergyEvidence.write("observerejectcrossfabricedit", 18, 1, Map.ofEntries(
                Map.entry("rejected", "true"), Map.entry("serverIssued", "true"),
                Map.entry("encoded", "true"), Map.entry("clientValueStable", "true"),
                Map.entry("clientBytesStable", "true"), Map.entry("serverAStable", "true"),
                Map.entry("serverBStable", "true"), Map.entry("reboundStateAbsent", "true"),
                Map.entry("subscriptionsStable", "true"), Map.entry("removalsStable", "true"),
                Map.entry("policyStable", "true"), Map.entry("topologyStable", "true"),
                Map.entry("activeBefore", "2"), Map.entry("activeAfter", "2"),
                Map.entry("activeClosed", "0"), Map.entry("removalDelta", "2"),
                Map.entry("fabrics", "2"), Map.entry("authorityReceipts", "2")));
        scene.close();
    }

    private void attack() {
        var service = LevelObservabilityService.get(helper.getLevel());
        var subscriptions = service.subscriptions();
        var firstEnvelope = envelope(scene.player().getUUID().toString(), scene.firstScope());
        var secondEnvelope = envelope(scene.secondPlayer().getUUID().toString(), scene.secondScope());
        var firstSession = firstEnvelope.session();
        var secondSession = secondEnvelope.session();
        helper.assertValueEqual(firstSession.scope(), scene.firstScope(), "Fabric A session must be server-issued");
        helper.assertValueEqual(secondSession.scope(), scene.secondScope(), "Fabric B session must be server-issued");
        helper.assertTrue(FabricRegistryAccess.get(helper.getLevel()).isCurrent(firstSession.scope()),
                "Fabric A generation must be current");
        helper.assertTrue(FabricRegistryAccess.get(helper.getLevel()).isCurrent(secondSession.scope()),
                "Fabric B generation must be current");

        var firstState = ObservationRuntimeEvidence.clientState(firstSession).orElseThrow();
        var secondState = ObservationRuntimeEvidence.clientState(secondSession).orElseThrow();
        var clientBefore = firstState.snapshot().orElseThrow();
        var secondClientBefore = secondState.snapshot().orElseThrow();
        helper.assertTrue(!clientBefore.members().isEmpty() && !clientBefore.providers().isEmpty()
                && !clientBefore.endpoints().isEmpty() && !clientBefore.policies().isEmpty()
                && !clientBefore.locks().isEmpty() && !clientBefore.tasks().isEmpty(),
                "Fabric A baseline must contain the full live projection");
        helper.assertTrue(!secondClientBefore.members().isEmpty(), "Fabric B baseline must contain projected records");

        var encodedBefore = ObservationPayloadProbe.encoded(new ObservationSnapshotEnvelope(firstSession, clientBefore));
        var serverABefore = service.snapshot(scene.firstScope());
        var serverBBefore = service.snapshot(scene.secondScope());
        var registryRevision = FabricRegistryAccess.get(helper.getLevel()).snapshot().topologyRevision();
        var policy = PolicyService.get(helper.getLevel());
        var configuredPolicies = policy.configuredCount();
        var tombstones = policy.tombstoneCount();
        helper.assertValueEqual(subscriptions.activeCount(), 2, "Both production menus must own subscriptions");
        removalsBefore = subscriptions.removalCount();
        ObservationRuntimeEvidence.authority(new ObservationRuntimeEvidence.AuthorityReceipt(
                firstSession, "A", subscriptions.activeCount(), removalsBefore));
        ObservationRuntimeEvidence.authority(new ObservationRuntimeEvidence.AuthorityReceipt(
                secondSession, "B", subscriptions.activeCount(), removalsBefore));

        var rebound = new ObservationSession(firstSession.playerId(), firstSession.menuSessionId(),
                secondSession.subscriptionId(), secondSession.subscriptionGeneration(), secondSession.scope(),
                secondSession.nonce());
        var replacement = nextRevision(secondClientBefore);
        var attack = new ObservationDeltaEnvelope(rebound,
                new FabricStateDelta(replacement, secondClientBefore.dataRevision(), false));
        var rejected = !ObservationRuntimeEvidence.applyEncoded(attack);

        var clientAfter = firstState.snapshot().orElseThrow();
        var secondClientAfter = secondState.snapshot().orElseThrow();
        var valueStable = clientAfter.equals(clientBefore) && secondClientAfter.equals(secondClientBefore);
        var bytesStable = Arrays.equals(encodedBefore,
                ObservationPayloadProbe.encoded(new ObservationSnapshotEnvelope(firstSession, clientAfter)));
        var serverAStable = service.snapshot(scene.firstScope()).equals(serverABefore);
        var serverBStable = service.snapshot(scene.secondScope()).equals(serverBBefore);
        var reboundAbsent = ObservationRuntimeEvidence.clientState(rebound).isEmpty();
        var subscriptionsStable = subscriptions.activeCount() == 2;
        var removalsStable = subscriptions.removalCount() == removalsBefore;
        var policyStable = policy.configuredCount() == configuredPolicies && policy.tombstoneCount() == tombstones
                && serverABefore.policyRevision() == service.snapshot(scene.firstScope()).policyRevision()
                && serverBBefore.policyRevision() == service.snapshot(scene.secondScope()).policyRevision();
        var topologyStable = registryRevision == FabricRegistryAccess.get(helper.getLevel()).snapshot().topologyRevision();
        helper.assertTrue(rejected && valueStable && bytesStable && serverAStable && serverBStable && reboundAbsent
                && subscriptionsStable && removalsStable && policyStable && topologyStable,
                "Encoded cross-Fabric rebound must reject without client or server mutation");
        ObservationRuntimeEvidence.rejection(new ObservationRuntimeEvidence.RejectionReceipt(firstSession, secondSession,
                subscriptions.activeCount(), subscriptions.removalCount(), new ObservationRuntimeEvidence.RejectionResult(
                        rejected, valueStable, bytesStable, serverAStable, serverBStable, reboundAbsent,
                        subscriptionsStable, removalsStable, policyStable, topologyStable)));
    }

    private ObservationSnapshotEnvelope envelope(String playerId, space.controlnet.ae2federation.fabric.FabricReference scope) {
        return ObservationRuntimeEvidence.snapshotEnvelopes().stream()
                .filter(candidate -> candidate.session().playerId().toString().equals(playerId)
                        && candidate.session().scope().equals(scope))
                .findFirst().orElseThrow();
    }

    private static FabricStateSnapshot nextRevision(FabricStateSnapshot snapshot) {
        return new FabricStateSnapshot(snapshot.scope(), snapshot.topologyRevision(), snapshot.policyRevision(),
                Math.incrementExact(snapshot.dataRevision()), snapshot.members(), snapshot.providers(), snapshot.endpoints(),
                snapshot.policies(), snapshot.locks(), snapshot.tasks(), snapshot.flows());
    }
}
