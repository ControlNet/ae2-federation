package space.controlnet.ae2federation.test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.domain.FederationDomainId;
import space.controlnet.ae2federation.domain.FederationDomainNodeEvidence;
import space.controlnet.ae2federation.domain.FederationDomainNodeId;
import space.controlnet.ae2federation.domain.FederationDomainPortEvidence;
import space.controlnet.ae2federation.domain.FederationDomainPortId;
import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.FederationDomainSourceId;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.observability.ObservationLimits;
import space.controlnet.ae2federation.observability.id.EndpointId;
import space.controlnet.ae2federation.observability.id.FlowId;
import space.controlnet.ae2federation.observability.id.LockId;
import space.controlnet.ae2federation.observability.id.MemberId;
import space.controlnet.ae2federation.observability.id.PolicyId;
import space.controlnet.ae2federation.observability.id.ProviderId;
import space.controlnet.ae2federation.observability.id.TaskId;
import space.controlnet.ae2federation.observability.state.EndpointState;
import space.controlnet.ae2federation.observability.state.LockState;
import space.controlnet.ae2federation.observability.state.PolicyState;
import space.controlnet.ae2federation.observability.state.ProviderState;
import space.controlnet.ae2federation.observability.state.TaskState;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.observability.meter.NativeTransportMeter;
import space.controlnet.ae2federation.observability.meter.OperationEventId;
import space.controlnet.ae2federation.observability.state.FederationDomainStateDelta;
import space.controlnet.ae2federation.observability.state.FederationDomainStateSnapshot;
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
import space.controlnet.ae2federation.observability.LevelObservabilityService;
import space.controlnet.ae2federation.test.policy.PolicyBridgeFixtures;
import space.controlnet.ae2federation.observability.ObservationSnapshotSink;

@PrefixGameTestTemplate(false)
public final class ObservabilityGameTests {
    private static final FederationDomainReference SCOPE = new FederationDomainReference(new FederationDomainId("physical:observe"), 3);

    private ObservabilityGameTests() {
    }

    /** One accepted storage extraction through a Storage projection is one physical flow of its exact amount. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            timeoutTicks = 400)
    public static void observeNativeFlowOnce(GameTestHelper helper) {
        var fixtures = new PolicyBridgeFixtures(helper, new net.minecraft.core.BlockPos(5, 3, 5));
        fixtures.installStorageCells();
        var iron = appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT);
        var state = new int[1];
        helper.succeedWhen(() -> {
            if (state[0] == 0) {
                helper.assertTrue(fixtures.networksSettled(), "Waiting for storage networks");
                fixtures.placeFirstBridge();
                state[0] = 1;
            }
            var key = PolicyLifecycleGameTests.storageKey(fixtures);
            if (state[0] == 1) {
                helper.assertTrue(fixtures.firstBridgeReady(), "Waiting for the Bridge domain");
                var policies = space.controlnet.ae2federation.policy.PolicyService.get(helper.getLevel());
                policies.edit(new space.controlnet.ae2federation.policy.PolicyEdit(key, policies.revision(key),
                        space.controlnet.ae2federation.policy.PolicyRule.storageDefaults()));
                state[0] = 2;
            }
            var projection = space.controlnet.ae2federation.storage.mount.StorageMountService.get(helper.getLevel())
                    .projection(key);
            helper.assertTrue(projection != null, "Waiting for the Storage projection");
            GameTestChecks.once(helper, () -> {
                var source = new appeng.me.helpers.PlayerSource(helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE));
                // Stocked straight into the provider's own Grid, which no projection observes.
                fixtures.outerGrid().getStorageService().getInventory().insert(iron, 16,
                        appeng.api.config.Actionable.MODULATE, source);
                var registry = space.controlnet.ae2federation.domain.FederationDomainRegistryAccess.get(helper.getLevel());
                var shared = new java.util.HashSet<>(registry.federationdomainsFor(fixtures.mainNetwork()));
                shared.retainAll(registry.federationdomainsFor(fixtures.outerNetwork()));
                var scope = registry.federationDomain(shared.iterator().next()).orElseThrow().reference();
                var meter = LevelObservabilityService.get(helper.getLevel()).transportMeter();
                var topologyBefore = registry.snapshot().topologyRevision();
                var before = meter.window(scope).dataRevision();
                var beforeEvents = meter.window(scope).events().size();
                helper.assertValueEqual(projection.extract(iron, 8, appeng.api.config.Actionable.SIMULATE, source), 8L,
                        "Native simulation must report availability");
                helper.assertValueEqual(meter.window(scope).dataRevision(), before,
                        "Simulation must not create a physical flow");
                helper.assertValueEqual(projection.extract(iron, 8, appeng.api.config.Actionable.MODULATE, source), 8L,
                        "Native extraction must accept the items");
                var first = meter.window(scope);
                var repeatedObservation = meter.window(scope);
                helper.assertValueEqual(first.events().size() - beforeEvents, 1,
                        "One native acceptance must create one flow");
                helper.assertValueEqual(first.events().getLast().amount(), 8L,
                        "Flow amount must equal the exact accepted amount");
                helper.assertValueEqual(repeatedObservation.events().size(), first.events().size(),
                        "Repeated observation must not multiply physical flow");
                helper.assertValueEqual(registry.snapshot().topologyRevision(), topologyBefore,
                        "Data-only flow updates must not change topology revision");
                write("observenativeflowonce", 7, Map.ofEntries(Map.entry("accepted", "8"),
                        Map.entry("exactAmount", "8"), Map.entry("events", "1"),
                        Map.entry("simulationEvents", "0"), Map.entry("observationReads", "2"),
                        Map.entry("deduplicated", "true"), Map.entry("topologyStable", "true")));
            });
            fixtures.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            timeoutTicks = 400)
    public static void observeScopedSnapshot(GameTestHelper helper) {
        var scene = new RealObservationScene(helper, true);
        ObservationRuntimeEvidence.reset("observescopedsnapshot");
        ObservationRuntimeEvidence.useHeadlessGameTestTransport();
        helper.succeedWhen(() -> {
            helper.assertTrue(scene.ready(), "Waiting for two real Federation Domains: " + scene.status());
            helper.assertTrue(scene.openFirstMenu(), "Production Federation Domain policy menu must open for a real ServerPlayer");
            var snapshotReceipt = ObservationRuntimeEvidence.snapshot().stream()
                    .filter(receipt -> receipt.type().equals("snapshot")).findFirst().orElseThrow();
            var snapshot = LevelObservabilityService.get(helper.getLevel()).snapshot(scene.firstScope());
            helper.assertValueEqual(snapshot.scope(), scene.firstScope(), "Snapshot must retain selected Federation Domain");
            helper.assertValueEqual(snapshot.members().size(), 2, "Selected Federation Domain must contain its two real members");
            helper.assertTrue(snapshot.members().stream().noneMatch(member -> scene.secondNetworks().contains(
                    member.networkId())), "Snapshot must exclude the second real Federation Domain");
            helper.assertValueEqual(snapshot.members(), snapshot.members().stream()
                    .sorted(java.util.Comparator.comparing(member -> member.id().value())).toList(),
                    "Projected members must be canonical");
            helper.assertValueEqual(snapshotReceipt.player(), scene.player().getUUID().toString(),
                    "Snapshot receipt must bind the real menu player");
            helper.assertValueEqual(snapshot.providers().size(), 1, "Snapshot must include the real Provider");
            helper.assertValueEqual(LevelObservabilityService.get(helper.getLevel()).snapshot(scene.processingScope())
                    .endpoints().size(), 1, "The Endpoint's own domain snapshot must include the real Endpoint");
            helper.assertTrue(!snapshot.locks().isEmpty() && !snapshot.tasks().isEmpty(),
                    "Snapshot must include native Lane lock and task state");
            GameTestChecks.once(helper, () -> {
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
                                Map.entry("crossFederationDomain", "false"), Map.entry("domains", "2"),
                                Map.entry("sessionBound", "true"), Map.entry("providers", "1"),
                                Map.entry("endpoints", "1"), Map.entry("nonFlowDeltas", "2"),
                                Map.entry("processingSend", "1"), Map.entry("aggregateReturn", "1"),
                                Map.entry("topologyStable", "true")));
            });
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
            service.publish(new FederationDomainStateDelta(new FederationDomainStateSnapshot(scene.firstScope(), 1, 0, 1, List.of(),
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
        var accepted = new ObservationDeltaEnvelope(session, new FederationDomainStateDelta(emptySnapshot(6), 5, false));
        helper.assertTrue(state.applyDelta(ObservationPayloadProbe.roundTrip(accepted)),
                "Contiguous delta must apply");
        var stale = new ObservationDeltaEnvelope(session, new FederationDomainStateDelta(emptySnapshot(7), 5, false));
        helper.assertTrue(!state.applyDelta(ObservationPayloadProbe.roundTrip(stale)),
                "Replayed base revision must be rejected");
        helper.assertValueEqual(state.dataRevision(), 6L, "Rejected delta must not mutate state");
        helper.assertTrue(state.resnapshotRequired(), "Rejected delta must request resnapshot");
        helper.assertTrue(ObservationPayloadProbe.rejectsTrailing(stale), "Trailing payload data must be rejected");
        var otherSession = ObservationSession.create(session.playerId(), UUID.randomUUID(), SCOPE, 1);
        helper.assertTrue(!state.applyDelta(new ObservationDeltaEnvelope(otherSession,
                new FederationDomainStateDelta(emptySnapshot(8), 6, false))), "Wrong session delta must be rejected");
        write("observerejectstaledelta", 6, Map.of("revision", "6", "rejected", "true", "resnapshot", "true",
                "trailingRejected", "true", "wrongSessionRejected", "true"));
        helper.succeed();
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            timeoutTicks = 400)
    public static void observeRejectCrossFederationDomainEdit(GameTestHelper helper) {
        var probe = new CrossFederationDomainObservationProbe(helper);
        helper.succeedWhen(probe::verify);
    }

    /**
     * A domain of any size projects without throwing (domain size has no limit): each list keeps its first entries in
     * canonical order up to its bound and the payload stays under the vanilla clientbound ceiling. TEST-ONLY synthetic
     * topology: one node carries the native membership of every network.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true)
    public static void observeLargeDomainBounded(GameTestHelper helper) {
        var level = helper.getLevel();
        var extra = 44;
        var networks = java.util.stream.IntStream.range(0, ObservationLimits.MAX_MEMBERS + extra)
                .mapToObj(index -> new NetworkId(UUID.randomUUID())).toList();
        var hub = publishSyntheticHub(helper, networks);
        var service = LevelObservabilityService.get(level);
        var subscriptions = service.subscriptions();
        try {
            var scope = FederationDomainRegistryAccess.get(level).federationDomainOf(hub).orElseThrow().reference();
            var snapshot = service.snapshot(scope);
            helper.assertValueEqual(snapshot.members().size(), ObservationLimits.MAX_MEMBERS,
                    "A large domain must keep its members up to the bound");
            var expected = networks.stream().map(network -> MemberId.forNetwork(scope.federationDomainId(), network).value())
                    .sorted().limit(ObservationLimits.MAX_MEMBERS).toList();
            helper.assertValueEqual(snapshot.members().stream().map(member -> member.id().value()).toList(), expected,
                    "Kept members must be the first ones in canonical order");
            helper.assertValueEqual(snapshot.omitted().members(), extra, "The snapshot must count the omitted members");
            var envelope = new ObservationSnapshotEnvelope(ObservationSession.create(UUID.randomUUID(), UUID.randomUUID(),
                    scope, 1), snapshot);
            var size = ObservationPayloadProbe.encoded(envelope).length;
            helper.assertTrue(size <= ObservationLimits.MAX_PAYLOAD_BYTES, "Snapshot payload is oversized: " + size);
            helper.assertValueEqual(ObservationPayloadProbe.roundTrip(envelope).snapshot(), snapshot,
                    "The bounded snapshot and its omitted counts must survive the payload");
            // Every list over its bound, with the longest ids and strings the payload allows: the byte budget, not
            // only the count bounds, keeps it under the ceiling.
            var longScope = new FederationDomainReference(new FederationDomainId(
                    "physical:" + "9".repeat(ObservationLimits.MAX_ID_LENGTH - "physical:".length())), 1);
            var full = overfullSnapshot(longScope);
            var fullEnvelope = new ObservationSnapshotEnvelope(ObservationSession.create(UUID.randomUUID(),
                    UUID.randomUUID(), longScope, 1), full);
            var fullSize = ObservationPayloadProbe.encoded(fullEnvelope).length;
            helper.assertTrue(fullSize <= ObservationLimits.MAX_PAYLOAD_BYTES,
                    "A snapshot with every list over its bound is oversized: " + fullSize);
            helper.assertTrue(full.flows().size() < ObservationLimits.MAX_FLOWS
                    && full.omitted().flows() == ObservationLimits.MAX_FLOWS + OVERFULL_EXTRA - full.flows().size(),
                    "The byte budget must leave out flows the count bound kept: " + full.omitted());
            helper.assertValueEqual(ObservationPayloadProbe.roundTrip(fullEnvelope).snapshot(), full,
                    "A snapshot at the byte budget must survive the payload");
            // A subscribed large domain is projected by the tick sweep, which must not throw either.
            var subscription = subscriptions.subscribe(new CountingAuthority(level, scope), scope);
            service.sweep();
            helper.assertTrue(!subscription.closed(), "The sweep must keep the subscription");
            subscriptions.close(subscription);
        } finally {
            FederationDomainRegistryAccess.get(level).removeNode(hub);
        }
        write("observelargedomainbounded", 9, Map.of("members", Integer.toString(ObservationLimits.MAX_MEMBERS),
                "omittedMembers", Integer.toString(extra), "canonical", "true", "payloadBounded", "true",
                "byteBudget", "true", "sweep", "true"));
        helper.succeed();
    }

    private static final int OVERFULL_EXTRA = 10;

    /** Every list {@link #OVERFULL_EXTRA} entries over its bound, statuses and flow resources as long as allowed. */
    private static FederationDomainStateSnapshot overfullSnapshot(FederationDomainReference scope) {
        var domain = scope.federationDomainId();
        var status = "s".repeat(ObservationLimits.MAX_STRING_LENGTH);
        java.util.function.IntFunction<java.util.stream.IntStream> over =
                bound -> java.util.stream.IntStream.range(0, bound + OVERFULL_EXTRA);
        var members = over.apply(ObservationLimits.MAX_MEMBERS).mapToObj(index -> {
            var network = new NetworkId(new UUID(1, index));
            return new MemberState(scope, MemberId.forNetwork(domain, network), network, status);
        }).toList();
        var providers = over.apply(ObservationLimits.MAX_PROVIDERS).mapToObj(index -> new ProviderState(scope,
                ProviderId.of(domain, "provider-" + index), status)).toList();
        var endpoints = over.apply(ObservationLimits.MAX_ENDPOINTS).mapToObj(index -> new EndpointState(scope,
                EndpointId.of(domain, "endpoint-" + index), status)).toList();
        var policies = over.apply(ObservationLimits.MAX_POLICIES).mapToObj(index -> new PolicyState(scope,
                PolicyId.forKey(domain, new PolicyKey(new NetworkId(new UUID(2, index)), new NetworkId(new UUID(3, index)),
                        PolicyCapability.STORAGE)), status)).toList();
        var locks = over.apply(ObservationLimits.MAX_LOCKS).mapToObj(index -> new LockState(scope,
                LockId.of(domain, "lock-" + index), status)).toList();
        var tasks = over.apply(ObservationLimits.MAX_TASKS).mapToObj(index -> new TaskState(scope,
                TaskId.of(domain, "task-" + index), status)).toList();
        var flows = over.apply(ObservationLimits.MAX_FLOWS).mapToObj(index -> {
            var event = new UUID(4, index);
            return new FlowState(scope, FlowId.forEvent(domain, event), new OperationEventId(event), status,
                    ObservationLimits.MAX_RESOURCE_AMOUNT, ResourceUnit.ITEM, FlowState.Attribution.EXACT_OPERATION, false);
        }).toList();
        return FederationDomainStateSnapshot.bounded(scope, 1, 1, 1, members, providers, endpoints, policies, locks,
                tasks, flows);
    }

    /**
     * Traffic every tick moves only the data revision: the sweep rebuilds and publishes the subscribed domain's
     * projection at most once per {@link ObservationSubscriptionService#DATA_REFRESH_TICKS} for it.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            timeoutTicks = 200)
    public static void observeTrafficCoalesced(GameTestHelper helper) {
        var level = helper.getLevel();
        var hub = publishSyntheticHub(helper, List.of(new NetworkId(UUID.randomUUID()), new NetworkId(UUID.randomUUID())));
        var scope = FederationDomainRegistryAccess.get(level).federationDomainOf(hub).orElseThrow().reference();
        var service = LevelObservabilityService.get(level);
        var authority = new CountingAuthority(level, scope);
        var subscription = service.subscriptions().subscribe(authority, scope);
        var ticks = 60;
        var recorded = new int[1];
        helper.onEachTick(() -> {
            if (recorded[0] < ticks) {
                service.recordAccepted(List.of(scope), OperationEventId.create(), "minecraft:iron_ingot", 1,
                        ResourceUnit.ITEM, FlowState.Attribution.EXACT_OPERATION);
                recorded[0]++;
            }
        });
        helper.runAfterDelay(ticks + 5, () -> {
            try {
                helper.assertValueEqual(recorded[0], ticks, "Every tick must have carried traffic");
                helper.assertTrue(authority.deltas > 0, "Traffic must still reach the subscriber");
                // One rebuild per refresh period over the traffic, plus the first one and a tick of slack.
                helper.assertTrue(authority.deltas <= ticks / ObservationSubscriptionService.DATA_REFRESH_TICKS + 2,
                        "Data-only changes must be coalesced, got " + authority.deltas + " deltas in " + ticks + " ticks");
            } finally {
                service.subscriptions().close(subscription);
                FederationDomainRegistryAccess.get(level).removeNode(hub);
            }
            write("observetrafficcoalesced", 3, Map.of("ticks", Integer.toString(ticks),
                    "deltas", Integer.toString(authority.deltas), "coalesced", "true"));
            helper.succeed();
        });
    }

    /** TEST-ONLY: a synthetic node at this test's origin whose native ports make {@code networks} one domain. */
    private static FederationDomainNodeId publishSyntheticHub(GameTestHelper helper, List<NetworkId> networks) {
        var node = new FederationDomainNodeId("ae2federation_test:synthetic_observation_hub",
                helper.absolutePos(net.minecraft.core.BlockPos.ZERO).asLong());
        var ports = new java.util.TreeMap<String, FederationDomainPortEvidence>();
        for (var index = 0; index < networks.size(); index++) {
            var name = "native-" + index;
            ports.put(name, new FederationDomainPortEvidence.Native(
                    FederationDomainSourceId.nativePort(new FederationDomainPortId(node, name)), networks.get(index)));
        }
        FederationDomainRegistryAccess.get(helper.getLevel()).upsertNode(new FederationDomainNodeEvidence(node, ports));
        return node;
    }

    private static MemberState member(String value) {
        var network = new NetworkId(UUID.fromString(value));
        return new MemberState(SCOPE, MemberId.forNetwork(SCOPE.federationDomainId(), network), network, "online");
    }

    private static FederationDomainStateSnapshot emptySnapshot(long dataRevision) {
        return new FederationDomainStateSnapshot(SCOPE, 9, 4, dataRevision, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of());
    }

    private static void write(String testId, int assertions, Map<String, String> facts) {
        NativeEnergyEvidence.write(testId, assertions, 1, facts);
    }

    /** Accepts every delta and counts it; current while its domain generation is. */
    private static final class CountingAuthority implements ObservationAuthority {
        private final UUID playerId = UUID.randomUUID();
        private final UUID sessionId = UUID.randomUUID();
        private final net.minecraft.server.level.ServerLevel level;
        private final FederationDomainReference scope;
        private int deltas;

        private CountingAuthority(net.minecraft.server.level.ServerLevel level, FederationDomainReference scope) {
            this.level = level;
            this.scope = scope;
        }

        @Override
        public UUID playerId() {
            return playerId;
        }

        @Override
        public UUID sessionId() {
            return sessionId;
        }

        @Override
        public FederationDomainReference scope() {
            return scope;
        }

        @Override
        public boolean current() {
            return FederationDomainRegistryAccess.get(level).isCurrent(scope);
        }

        @Override
        public boolean deliver(ObservationDeltaEnvelope delta) {
            deltas++;
            return true;
        }
    }

    private record TestAuthority(UUID playerId, UUID sessionId, FederationDomainReference scope, boolean current)
            implements ObservationAuthority {
    }

}
