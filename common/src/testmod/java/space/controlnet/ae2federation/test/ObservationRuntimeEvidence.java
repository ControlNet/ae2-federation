package space.controlnet.ae2federation.test;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.observability.ObservationRuntimeReceiptSink;
import space.controlnet.ae2federation.observability.ObservationSnapshotSink;
import space.controlnet.ae2federation.observability.ObservationDeltaSink;
import space.controlnet.ae2federation.observability.ObservationSessionLifecycleSink;
import space.controlnet.ae2federation.observability.state.FlowState;
import space.controlnet.ae2federation.observability.state.ObservationDeltaEnvelope;
import space.controlnet.ae2federation.observability.state.ObservationClientState;
import space.controlnet.ae2federation.observability.state.ObservationClientStates;
import space.controlnet.ae2federation.observability.state.ObservationSession;
import space.controlnet.ae2federation.observability.state.ObservationSnapshotEnvelope;

public final class ObservationRuntimeEvidence {
    private static final Logger LOGGER = LoggerFactory.getLogger(ObservationRuntimeEvidence.class);
    private static final List<Receipt> RECEIPTS = new ArrayList<>();
    private static final List<ObservationSnapshotEnvelope> SNAPSHOTS = new ArrayList<>();
    private static ObservationClientStates clientStates = new ObservationClientStates();
    private static String testId = "";

    private ObservationRuntimeEvidence() {
    }

    public static void register() {
        ObservationRuntimeReceiptSink.register(new ObservationRuntimeReceiptSink.Listener() {
            @Override
            public void subscription(ObservationSession session,
                    ObservationRuntimeReceiptSink.SubscriptionEvent event) {
                record(new Receipt("subscription", session.playerId().toString(), session.menuSessionId().toString(),
                        session.scope(), event.name(), session.subscriptionGeneration(), 0,
                        session.subscriptionId().value().toString(), session.nonce().toString(), false));
            }

            @Override
            public void snapshot(ServerPlayer player, ObservationSnapshotEnvelope envelope) {
                var snapshot = envelope.snapshot();
                recordSnapshot(envelope);
                record(new Receipt("snapshot", player.getUUID().toString(), envelope.session().menuSessionId().toString(),
                        snapshot.scope(), "delivered", snapshot.dataRevision(), snapshot.members().size(),
                        counts(snapshot), "", false));
            }

            @Override
            public void delta(ServerPlayer player, ObservationDeltaEnvelope envelope) {
                var delta = envelope.delta();
                record(new Receipt("delta", player.getUUID().toString(), envelope.session().menuSessionId().toString(),
                        delta.scope(), "delivered", delta.dataRevision(), delta.baseDataRevision(),
                        counts(delta.replacement()), "",
                        delta.resnapshotRequired()));
            }

            @Override
            public void flow(FabricReference scope, FlowState flow) {
                record(new Receipt("flow", "", "", scope, flow.eventId().value().toString(), flow.amount(), 0,
                        flow.resource(), flow.attribution().name(), flow.exactBatchCompletion()));
            }
        });
    }

    public static synchronized void reset(String selectedTestId) {
        testId = selectedTestId;
        RECEIPTS.clear();
        SNAPSHOTS.clear();
        clientStates = new ObservationClientStates();
    }

    public static void useHeadlessGameTestTransport() {
        ObservationSessionLifecycleSink.register((player, session) -> clientStates.open(session),
                (player, session) -> clientStates.close(session));
        ObservationSnapshotSink.register((player, snapshot) -> clientStates.apply(ObservationPayloadProbe.roundTrip(snapshot)));
        ObservationDeltaSink.register((player, delta) -> clientStates.apply(ObservationPayloadProbe.roundTrip(delta)));
    }

    public static synchronized List<Receipt> snapshot() {
        return List.copyOf(RECEIPTS);
    }

    public static synchronized List<ObservationSnapshotEnvelope> snapshotEnvelopes() {
        return List.copyOf(SNAPSHOTS);
    }

    public static synchronized java.util.Optional<ObservationClientState> clientState(ObservationSession session) {
        return clientStates.state(session);
    }

    public static synchronized boolean applyEncoded(ObservationDeltaEnvelope envelope) {
        return clientStates.apply(ObservationPayloadProbe.roundTrip(envelope));
    }

    public static void authority(AuthorityReceipt authority) {
        var session = authority.session();
        record(new Receipt("authority", session.playerId().toString(), session.menuSessionId().toString(),
                session.scope(), session.subscriptionId().value().toString(), session.subscriptionGeneration(),
                authority.activeSubscriptions(), session.nonce().toString(), authority.label(), false));
    }

    public static void rejection(RejectionReceipt rejection) {
        var session = rejection.first();
        var result = rejection.result();
        var facts = "rejected=" + result.rejected() + ",valueStable=" + result.valueStable()
                + ",bytesStable=" + result.bytesStable() + ",serverAStable=" + result.serverAStable()
                + ",serverBStable=" + result.serverBStable() + ",reboundAbsent=" + result.reboundAbsent()
                + ",subscriptionsStable=" + result.subscriptionsStable() + ",removalsStable="
                + result.removalsStable() + ",policyStable=" + result.policyStable() + ",topologyStable="
                + result.topologyStable();
        record(new Receipt("rejection", session.playerId().toString(), session.menuSessionId().toString(),
                session.scope(), "rejected", rejection.activeSubscriptions(), rejection.removalCount(), facts,
                rejection.second().subscriptionId().value().toString(), false));
    }

    private static synchronized void recordSnapshot(ObservationSnapshotEnvelope envelope) {
        SNAPSHOTS.add(envelope);
    }

    private static synchronized void record(Receipt receipt) {
        if (testId.isEmpty()) {
            return;
        }
        RECEIPTS.add(receipt);
        LOGGER.info("AE2F_OBSERVATION_RECEIPT testId={} type={} player={} menu={} scope={} event={} value={} "
                        + "secondary={} resource={} attribution={} exactBatch={}",
                testId, receipt.type(), receipt.player(), receipt.menu(), receipt.scope(), receipt.event(),
                receipt.value(), receipt.secondary(), receipt.resource(), receipt.attribution(), receipt.exactBatch());
    }

    private static String counts(space.controlnet.ae2federation.observability.state.FabricStateSnapshot snapshot) {
        return "members=" + snapshot.members().size() + ",providers=" + snapshot.providers().size()
                + ",endpoints=" + snapshot.endpoints().size() + ",policies=" + snapshot.policies().size()
                + ",locks=" + snapshot.locks().size() + ",tasks=" + snapshot.tasks().size()
                + ",flows=" + snapshot.flows().size();
    }

    public record AuthorityReceipt(ObservationSession session, String label, int activeSubscriptions,
            long removalCount) {
    }

    public record RejectionReceipt(ObservationSession first, ObservationSession second, int activeSubscriptions,
            long removalCount, RejectionResult result) {
    }

    public record RejectionResult(boolean rejected, boolean valueStable, boolean bytesStable, boolean serverAStable,
            boolean serverBStable, boolean reboundAbsent, boolean subscriptionsStable, boolean removalsStable,
            boolean policyStable, boolean topologyStable) {
    }

    public record Receipt(String type, String player, String menu, FabricReference scope, String event, long value,
            long secondary, String resource, String attribution, boolean exactBatch) {
    }
}
