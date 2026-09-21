package space.controlnet.ae2federation.test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.fabric.FabricId;
import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.observability.id.MemberId;
import space.controlnet.ae2federation.observability.meter.NativeTransportMeter;
import space.controlnet.ae2federation.observability.meter.OperationEventId;
import space.controlnet.ae2federation.observability.state.FabricStateDelta;
import space.controlnet.ae2federation.observability.state.FabricStateSnapshot;
import space.controlnet.ae2federation.observability.state.FlowState;
import space.controlnet.ae2federation.observability.state.MemberState;
import space.controlnet.ae2federation.observability.state.ObservationClientState;
import space.controlnet.ae2federation.observability.state.ObservationDeltaEnvelope;
import space.controlnet.ae2federation.observability.state.ObservationSession;
import space.controlnet.ae2federation.observability.state.ObservationSnapshotEnvelope;
import space.controlnet.ae2federation.observability.state.ResourceUnit;
import space.controlnet.ae2federation.observability.subscription.ObservationAuthority;
import space.controlnet.ae2federation.observability.subscription.ObservationSubscriptionService;
import space.controlnet.ae2federation.test.energy.NativeEnergyEvidence;
import space.controlnet.ae2federation.test.energy.DirectionalEnergyFixture;
import space.controlnet.ae2federation.observability.LevelObservabilityService;
import space.controlnet.ae2federation.test.policy.PolicyBridgeFixtures;
import space.controlnet.ae2federation.observability.ObservationSnapshotSink;

@PrefixGameTestTemplate(false)
public final class ObservabilityGameTests {
    private static final FabricReference SCOPE = new FabricReference(new FabricId("physical:observe"), 3);

    private ObservabilityGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true)
    public static void observeNativeFlowOnce(GameTestHelper helper) {
        var fixture = new DirectionalEnergyFixture(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for native directional energy fixture");
            fixture.enable();
            fixture.charge(16);
            var scope = fixture.binding().revision().fabrics().iterator().next();
            var meter = LevelObservabilityService.get(helper.getLevel()).transportMeter();
            var topologyBefore = space.controlnet.ae2federation.fabric.FabricRegistryAccess.get(helper.getLevel())
                    .snapshot().topologyRevision();
            var before = meter.window(scope).dataRevision();
            var beforeEvents = meter.window(scope).events().size();
            helper.assertTrue(fixture.simulate(8) > 0, "Native simulation must report availability");
            helper.assertValueEqual(meter.window(scope).dataRevision(), before,
                    "Simulation must not create a physical flow");
            helper.assertTrue(fixture.extract(8) > 0, "Native extraction must accept energy");
            var first = meter.window(scope);
            var repeatedObservation = meter.window(scope);
            helper.assertValueEqual(first.events().size() - beforeEvents, 1,
                    "One native acceptance must create one flow");
            helper.assertValueEqual(first.events().getLast().amount(), 8_000_000_000L,
                    "Flow amount must equal exact accepted nano-AE");
            helper.assertValueEqual(repeatedObservation.events().size(), first.events().size(),
                    "Repeated observation must not multiply physical flow");
            var topologyAfter = space.controlnet.ae2federation.fabric.FabricRegistryAccess.get(helper.getLevel())
                    .snapshot().topologyRevision();
            helper.assertValueEqual(topologyAfter, topologyBefore,
                    "Data-only flow updates must not change topology revision");
            write("observenativeflowonce", 7, Map.ofEntries(Map.entry("accepted", "8"),
                    Map.entry("exactNano", "8000000000"), Map.entry("events", "1"),
                    Map.entry("simulationEvents", "0"), Map.entry("observationReads", "2"),
                    Map.entry("deduplicated", "true"), Map.entry("topologyStable", "true")));
            fixture.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            timeoutTicks = 400)
    public static void observeScopedSnapshot(GameTestHelper helper) {
        var scene = new RealObservationScene(helper, true);
        ObservationRuntimeEvidence.reset("observescopedsnapshot");
        ObservationRuntimeEvidence.useHeadlessGameTestTransport();
        helper.succeedWhen(() -> {
            helper.assertTrue(scene.ready(), "Waiting for two real Fabrics: " + scene.status());
            helper.assertTrue(scene.openFirstMenu(), "Production Fabric policy menu must open for a real ServerPlayer");
            var snapshotReceipt = ObservationRuntimeEvidence.snapshot().stream()
                    .filter(receipt -> receipt.type().equals("snapshot")).findFirst().orElseThrow();
            var snapshot = LevelObservabilityService.get(helper.getLevel()).snapshot(scene.firstScope());
            helper.assertValueEqual(snapshot.scope(), scene.firstScope(), "Snapshot must retain selected Fabric");
            helper.assertValueEqual(snapshot.members().size(), 2, "Selected Fabric must contain its two real members");
            helper.assertTrue(snapshot.members().stream().noneMatch(member -> scene.secondNetworks().contains(
                    member.networkId())), "Snapshot must exclude the second real Fabric");
            helper.assertValueEqual(snapshot.members(), snapshot.members().stream()
                    .sorted(java.util.Comparator.comparing(member -> member.id().value())).toList(),
                    "Projected members must be canonical");
            helper.assertValueEqual(snapshotReceipt.player(), scene.player().getUUID().toString(),
                    "Snapshot receipt must bind the real menu player");
            helper.assertValueEqual(snapshot.providers().size(), 1, "Snapshot must include the real Provider");
            helper.assertValueEqual(snapshot.endpoints().size(), 1, "Snapshot must include the real Endpoint");
            helper.assertTrue(!snapshot.locks().isEmpty() && !snapshot.tasks().isEmpty(),
                    "Snapshot must include native Lane lock and task state");
            var topology = snapshot.topologyRevision();
            scene.mutateObservedState();
            LevelObservabilityService.get(helper.getLevel()).sweep();
            var updated = LevelObservabilityService.get(helper.getLevel()).snapshot(scene.firstScope());
            helper.assertValueEqual(updated.topologyRevision(), topology,
                    "Data-only changes must not advance topology revision");
            helper.assertValueEqual(updated.policies().size(), 1, "Policy addition must publish without reopening");
            helper.assertTrue(updated.tasks().stream().anyMatch(task -> task.status().contains("return-buffered")),
                    "Aggregate return state must publish without reopening");
            helper.assertTrue(updated.flows().stream().anyMatch(flow -> flow.attribution()
                    == FlowState.Attribution.EXACT_OPERATION), "Processing send must report exact accepted quantity");
            helper.assertTrue(updated.flows().stream().anyMatch(flow -> flow.attribution()
                    == FlowState.Attribution.AGGREGATE_LANE_RETURN && !flow.exactBatchCompletion()),
                    "Aggregate return must never claim exact Batch completion");
            helper.assertTrue(scene.removePolicy(), "Production policy removal must succeed");
            LevelObservabilityService.get(helper.getLevel()).sweep();
            var removed = LevelObservabilityService.get(helper.getLevel()).snapshot(scene.firstScope());
            helper.assertTrue(removed.policies().isEmpty(), "Policy removal must publish without reopening");
            helper.assertTrue(ObservationRuntimeEvidence.snapshot().stream()
                    .filter(receipt -> receipt.type().equals("delta")).count() >= 2,
                    "Independent packet receipts must observe non-flow additions and removals");
            write("observescopedsnapshot", 16,
                    Map.ofEntries(Map.entry("members", "2"), Map.entry("canonical", "true"),
                            Map.entry("crossFabric", "false"), Map.entry("fabrics", "2"),
                            Map.entry("sessionBound", "true"), Map.entry("providers", "1"),
                            Map.entry("endpoints", "1"), Map.entry("nonFlowDeltas", "2"),
                            Map.entry("processingSend", "1"), Map.entry("aggregateReturn", "1"),
                            Map.entry("topologyStable", "true")));
            scene.closeFirstMenu();
            scene.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true)
    public static void observeCloseCleanup(GameTestHelper helper) {
        ObservationRuntimeEvidence.reset("observeclosecleanup");
        ObservationRuntimeEvidence.useHeadlessGameTestTransport();
        var scene = new RealObservationScene(helper);
        var phase = new int[1];
        var removalsBeforeClose = new long[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(scene.ready(), "Waiting for real menu context");
            var service = LevelObservabilityService.get(helper.getLevel()).subscriptions();
            if (phase[0] == 0) {
                helper.assertTrue(scene.openFirstMenu(), "Production menu must open");
                helper.assertValueEqual(service.activeCount(), 1, "Real menu must own one subscription");
                removalsBeforeClose[0] = service.removalCount();
                scene.closeFirstMenu();
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for menu close cleanup");
            }
            helper.assertValueEqual(service.activeCount(), 0, "Closed menu must release ownership");
            helper.assertValueEqual(service.removalCount(), removalsBeforeClose[0] + 1,
                    "Cleanup must remove the subscription exactly once");
            service.publish(new FabricStateDelta(new FabricStateSnapshot(scene.firstScope(), 1, 0, 1, List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of()), 0, false));
            helper.assertValueEqual(service.activeCount(), 0, "Late native event must not recreate ownership");
            write("observeclosecleanup", 5,
                    Map.of("active", "0", "removals", "1", "idempotent", "true", "lateDelivery", "false"));
            scene.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true)
    public static void observeRejectStaleDelta(GameTestHelper helper) {
        var session = ObservationSession.create(UUID.randomUUID(), UUID.randomUUID(), SCOPE, 1);
        var state = new ObservationClientState(session);
        state.applySnapshot(ObservationPayloadProbe.roundTrip(new ObservationSnapshotEnvelope(session,
                emptySnapshot(5))));
        var accepted = new ObservationDeltaEnvelope(session, new FabricStateDelta(emptySnapshot(6), 5, false));
        helper.assertTrue(state.applyDelta(ObservationPayloadProbe.roundTrip(accepted)),
                "Contiguous delta must apply");
        var stale = new ObservationDeltaEnvelope(session, new FabricStateDelta(emptySnapshot(7), 5, false));
        helper.assertTrue(!state.applyDelta(ObservationPayloadProbe.roundTrip(stale)),
                "Replayed base revision must be rejected");
        helper.assertValueEqual(state.dataRevision(), 6L, "Rejected delta must not mutate state");
        helper.assertTrue(state.resnapshotRequired(), "Rejected delta must request resnapshot");
        helper.assertTrue(ObservationPayloadProbe.rejectsTrailing(stale), "Trailing payload data must be rejected");
        var otherSession = ObservationSession.create(session.playerId(), UUID.randomUUID(), SCOPE, 1);
        helper.assertTrue(!state.applyDelta(new ObservationDeltaEnvelope(otherSession,
                new FabricStateDelta(emptySnapshot(8), 6, false))), "Wrong session delta must be rejected");
        write("observerejectstaledelta", 6, Map.of("revision", "6", "rejected", "true", "resnapshot", "true",
                "trailingRejected", "true", "wrongSessionRejected", "true"));
        helper.succeed();
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            timeoutTicks = 400)
    public static void observeRejectCrossFabricEdit(GameTestHelper helper) {
        var probe = new CrossFabricObservationProbe(helper);
        helper.succeedWhen(probe::verify);
    }

    private static MemberState member(String value) {
        var network = new NetworkId(UUID.fromString(value));
        return new MemberState(SCOPE, MemberId.forNetwork(SCOPE.fabricId(), network), network, "online");
    }

    private static FabricStateSnapshot emptySnapshot(long dataRevision) {
        return new FabricStateSnapshot(SCOPE, 9, 4, dataRevision, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of());
    }

    private static void write(String testId, int assertions, Map<String, String> facts) {
        NativeEnergyEvidence.write(testId, assertions, 1, facts);
    }

    private record TestAuthority(UUID playerId, UUID sessionId, FabricReference scope, boolean current)
            implements ObservationAuthority {
    }

}
