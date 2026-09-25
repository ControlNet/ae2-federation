package space.controlnet.ae2federation.test;

import io.netty.buffer.Unpooled;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import space.controlnet.ae2federation.client.menu.FederationDomainPolicyAction;
import space.controlnet.ae2federation.client.menu.FederationDomainPolicyActionRequest;
import space.controlnet.ae2federation.domain.FederationDomainId;
import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.neoforge.network.FederationDomainPolicyActionPayload;
import space.controlnet.ae2federation.neoforge.network.FederationDomainStateSnapshotPayload;
import space.controlnet.ae2federation.observability.ObservationLimits;
import space.controlnet.ae2federation.observability.meter.NativeTransportMeter;
import space.controlnet.ae2federation.observability.meter.OperationEventId;
import space.controlnet.ae2federation.observability.state.FederationDomainStateDelta;
import space.controlnet.ae2federation.observability.state.FederationDomainStateSnapshot;
import space.controlnet.ae2federation.observability.state.FlowState;
import space.controlnet.ae2federation.observability.state.ObservationClientStates;
import space.controlnet.ae2federation.observability.state.ObservationDeltaEnvelope;
import space.controlnet.ae2federation.observability.state.ObservationSession;
import space.controlnet.ae2federation.observability.state.ObservationSnapshotEnvelope;
import space.controlnet.ae2federation.observability.state.ResourceUnit;
import space.controlnet.ae2federation.policy.PolicyRevision;

public final class TaskThirtyFivePacketProbe {
    private TaskThirtyFivePacketProbe() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected one evidence path");
        }
        var scope = new FederationDomainReference(new FederationDomainId("physical:task35"), 7);
        var session = ObservationSession.create(UUID.randomUUID(), UUID.randomUUID(), scope, 1);
        var initial = snapshot(scope, 4);
        var states = new ObservationClientStates();
        if (!states.open(session) || !states.apply(new ObservationSnapshotEnvelope(session, initial))) {
            throw new IllegalStateException("Initial packet state was not accepted");
        }

        var actionRequest = new FederationDomainPolicyActionRequest(FederationDomainPolicyAction.TOGGLE_POLICY, 3, UUID.randomUUID(), 0,
                scope, new PolicyRevision(0));
        var actionCodecRoundTrip = actionRequest.equals(roundTrip(actionRequest));
        var actionOversizedRejected = rejectsOversizedAction();
        var actionTrailingRejected = rejectsTrailingAction(actionRequest);
        var actionUnknownRejected = rejectsUnknownAction();
        var snapshotOversizedRejected = rejectsOversizedSnapshot();
        var deltaTrailingRejected = ObservationPayloadProbe.rejectsTrailing(new ObservationDeltaEnvelope(session,
                new FederationDomainStateDelta(snapshot(scope, 5), 4, false)));
        var wrongSession = ObservationSession.create(session.playerId(), UUID.randomUUID(), scope, 1);
        var outOfContextRejected = !states.apply(new ObservationSnapshotEnvelope(wrongSession, snapshot(scope, 5)));
        var snapshotRegressionRejected = !states.apply(new ObservationSnapshotEnvelope(session, snapshot(scope, 3)))
                && initial.equals(states.state(session).orElseThrow().snapshot().orElseThrow());
        var closedSessionReplayRejected = states.close(session)
                && !states.apply(new ObservationSnapshotEnvelope(session, snapshot(scope, 5)))
                && !states.open(session) && states.state(session).isEmpty();
        var meterFacts = meterFacts(scope);
        var allFacts = List.of(actionCodecRoundTrip, actionOversizedRejected, actionTrailingRejected,
                actionUnknownRejected, snapshotOversizedRejected, deltaTrailingRejected, outOfContextRejected,
                snapshotRegressionRejected, closedSessionReplayRejected, meterFacts.oversizedRejected(),
                meterFacts.unchangedAfterReject(), meterFacts.maximumAcceptedExactlyOnce());
        if (allFacts.stream().anyMatch(value -> !value)) {
            throw new IllegalStateException("Task 35 packet rejection probe failed");
        }

        var facts = new Properties();
        facts.setProperty("actionCodecRoundTrip", Boolean.toString(actionCodecRoundTrip));
        facts.setProperty("actionOversizedRejected", Boolean.toString(actionOversizedRejected));
        facts.setProperty("actionTrailingRejected", Boolean.toString(actionTrailingRejected));
        facts.setProperty("actionUnknownRejected", Boolean.toString(actionUnknownRejected));
        facts.setProperty("snapshotOversizedRejected", Boolean.toString(snapshotOversizedRejected));
        facts.setProperty("deltaTrailingRejected", Boolean.toString(deltaTrailingRejected));
        facts.setProperty("outOfContextRejected", Boolean.toString(outOfContextRejected));
        facts.setProperty("snapshotRegressionRejected", Boolean.toString(snapshotRegressionRejected));
        facts.setProperty("closedSessionReplayRejected", Boolean.toString(closedSessionReplayRejected));
        facts.setProperty("meterOversizedRejected", Boolean.toString(meterFacts.oversizedRejected()));
        facts.setProperty("meterUnchangedAfterReject", Boolean.toString(meterFacts.unchangedAfterReject()));
        facts.setProperty("meterMaximumAcceptedExactlyOnce",
                Boolean.toString(meterFacts.maximumAcceptedExactlyOnce()));
        try (var output = new FileOutputStream(new File(arguments[0]))) {
            facts.store(output, "Task 35 production packet boundaries");
        }
    }

    private static FederationDomainPolicyActionRequest roundTrip(FederationDomainPolicyActionRequest request) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        FederationDomainPolicyActionPayload.STREAM_CODEC.encode(buffer, new FederationDomainPolicyActionPayload(request));
        return FederationDomainPolicyActionPayload.STREAM_CODEC.decode(buffer).request();
    }

    private static boolean rejectsOversizedAction() {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        buffer.writeZero(FederationDomainPolicyActionRequest.MAX_PAYLOAD_BYTES + 1);
        return rejectsAction(buffer);
    }

    private static boolean rejectsTrailingAction(FederationDomainPolicyActionRequest request) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        FederationDomainPolicyActionPayload.STREAM_CODEC.encode(buffer, new FederationDomainPolicyActionPayload(request));
        buffer.writeByte(0);
        return rejectsAction(buffer);
    }

    private static boolean rejectsUnknownAction() {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        buffer.writeByte(255);
        return rejectsAction(buffer);
    }

    private static boolean rejectsAction(RegistryFriendlyByteBuf buffer) {
        try {
            FederationDomainPolicyActionPayload.STREAM_CODEC.decode(buffer);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static boolean rejectsOversizedSnapshot() {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        buffer.writeZero(ObservationLimits.MAX_PAYLOAD_BYTES + 1);
        try {
            FederationDomainStateSnapshotPayload.STREAM_CODEC.decode(buffer);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static MeterFacts meterFacts(FederationDomainReference scope) {
        var meter = new NativeTransportMeter(4);
        var event = OperationEventId.create();
        var before = meter.window(scope);
        var oversizedRejected = false;
        try {
            meter.recordAccepted(scope, event, "ae2:item", ObservationLimits.MAX_RESOURCE_AMOUNT + 1,
                    ResourceUnit.ITEM, FlowState.Attribution.EXACT_OPERATION);
        } catch (IllegalArgumentException expected) {
            oversizedRejected = true;
        }
        var unchangedAfterReject = before.equals(meter.window(scope));
        var accepted = meter.recordAccepted(scope, event, "ae2:item", ObservationLimits.MAX_RESOURCE_AMOUNT,
                ResourceUnit.ITEM, FlowState.Attribution.EXACT_OPERATION);
        var replayed = meter.recordAccepted(scope, event, "ae2:item", ObservationLimits.MAX_RESOURCE_AMOUNT,
                ResourceUnit.ITEM, FlowState.Attribution.EXACT_OPERATION);
        var after = meter.window(scope);
        var maximumAcceptedExactlyOnce = accepted && !replayed && after.dataRevision() == 1
                && after.events().size() == 1
                && after.events().getFirst().amount() == ObservationLimits.MAX_RESOURCE_AMOUNT;
        return new MeterFacts(oversizedRejected, unchangedAfterReject, maximumAcceptedExactlyOnce);
    }

    private static FederationDomainStateSnapshot snapshot(FederationDomainReference scope, long dataRevision) {
        return new FederationDomainStateSnapshot(scope, 9, 2, dataRevision, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of());
    }

    private record MeterFacts(boolean oversizedRejected, boolean unchangedAfterReject,
            boolean maximumAcceptedExactlyOnce) {
    }
}
