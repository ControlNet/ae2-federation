package space.controlnet.ae2federation.neoforge.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.network.RegistryFriendlyByteBuf;
import space.controlnet.ae2federation.fabric.FabricId;
import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.observability.ObservationLimits;
import space.controlnet.ae2federation.observability.id.EndpointId;
import space.controlnet.ae2federation.observability.id.FlowId;
import space.controlnet.ae2federation.observability.id.LockId;
import space.controlnet.ae2federation.observability.id.MemberId;
import space.controlnet.ae2federation.observability.id.PolicyId;
import space.controlnet.ae2federation.observability.id.ProviderId;
import space.controlnet.ae2federation.observability.id.TaskId;
import space.controlnet.ae2federation.observability.meter.OperationEventId;
import space.controlnet.ae2federation.observability.state.EndpointState;
import space.controlnet.ae2federation.observability.state.FabricStateSnapshot;
import space.controlnet.ae2federation.observability.state.FlowState;
import space.controlnet.ae2federation.observability.state.LockState;
import space.controlnet.ae2federation.observability.state.MemberState;
import space.controlnet.ae2federation.observability.state.PolicyState;
import space.controlnet.ae2federation.observability.state.ProviderState;
import space.controlnet.ae2federation.observability.state.TaskState;
import space.controlnet.ae2federation.observability.state.ObservationSession;
import space.controlnet.ae2federation.observability.id.SubscriptionId;
import space.controlnet.ae2federation.observability.id.ScopedObservationId;

final class FabricStateSnapshotCodec {
    private FabricStateSnapshotCodec() {
    }

    static void encode(RegistryFriendlyByteBuf buffer, FabricStateSnapshot snapshot) {
        writeScope(buffer, snapshot.scope());
        buffer.writeVarLong(snapshot.topologyRevision());
        buffer.writeVarLong(snapshot.policyRevision());
        buffer.writeVarLong(snapshot.dataRevision());
        writeList(buffer, snapshot.members(), FabricStateSnapshotCodec::writeMember);
        writeList(buffer, snapshot.providers(), FabricStateSnapshotCodec::writeProvider);
        writeList(buffer, snapshot.endpoints(), FabricStateSnapshotCodec::writeEndpoint);
        writeList(buffer, snapshot.policies(), FabricStateSnapshotCodec::writePolicy);
        writeList(buffer, snapshot.locks(), FabricStateSnapshotCodec::writeLock);
        writeList(buffer, snapshot.tasks(), FabricStateSnapshotCodec::writeTask);
        writeList(buffer, snapshot.flows(), FabricStateSnapshotCodec::writeFlow);
    }

    static FabricStateSnapshot decode(RegistryFriendlyByteBuf buffer) {
        var scope = readScope(buffer);
        var topologyRevision = buffer.readVarLong();
        var policyRevision = buffer.readVarLong();
        var dataRevision = buffer.readVarLong();
        return new FabricStateSnapshot(scope, topologyRevision, policyRevision, dataRevision,
                readList(buffer, ObservationLimits.MAX_MEMBERS, value -> readMember(value, scope)),
                readList(buffer, ObservationLimits.MAX_PROVIDERS, value -> readProvider(value, scope)),
                readList(buffer, ObservationLimits.MAX_ENDPOINTS, value -> readEndpoint(value, scope)),
                readList(buffer, ObservationLimits.MAX_POLICIES, value -> readPolicy(value, scope)),
                readList(buffer, ObservationLimits.MAX_LOCKS, value -> readLock(value, scope)),
                readList(buffer, ObservationLimits.MAX_TASKS, value -> readTask(value, scope)),
                readList(buffer, ObservationLimits.MAX_FLOWS, value -> readFlow(value, scope)));
    }

    static void encodeSession(RegistryFriendlyByteBuf buffer, ObservationSession session) {
        buffer.writeUUID(session.playerId());
        buffer.writeUUID(session.menuSessionId());
        writeScope(buffer, session.scope());
        buffer.writeUtf(session.subscriptionId().fabricId().value(), ObservationLimits.MAX_ID_LENGTH);
        buffer.writeUtf(session.subscriptionId().value(), ObservationLimits.MAX_ID_LENGTH);
        buffer.writeVarLong(session.subscriptionGeneration());
        buffer.writeUUID(session.nonce());
    }

    static ObservationSession decodeSession(RegistryFriendlyByteBuf buffer) {
        var playerId = buffer.readUUID();
        var menuSessionId = buffer.readUUID();
        var scope = readScope(buffer);
        var subscriptionFabric = new FabricId(buffer.readUtf(ObservationLimits.MAX_ID_LENGTH));
        if (!scope.fabricId().equals(subscriptionFabric)) {
            throw new IllegalArgumentException("Subscription belongs to another Fabric");
        }
        var subscriptionId = new SubscriptionId(subscriptionFabric,
                buffer.readUtf(ObservationLimits.MAX_ID_LENGTH));
        return new ObservationSession(playerId, menuSessionId, subscriptionId, buffer.readVarLong(), scope,
                buffer.readUUID());
    }

    private static void writeScope(RegistryFriendlyByteBuf buffer, FabricReference scope) {
        buffer.writeUtf(scope.fabricId().value(), ObservationLimits.MAX_ID_LENGTH);
        buffer.writeVarLong(scope.generation());
    }

    private static FabricReference readScope(RegistryFriendlyByteBuf buffer) {
        return new FabricReference(new FabricId(buffer.readUtf(ObservationLimits.MAX_ID_LENGTH)), buffer.readVarLong());
    }

    private static void writeMember(RegistryFriendlyByteBuf buffer, MemberState state) {
        writeId(buffer, state.id());
        buffer.writeUUID(state.networkId().value());
        writeStatus(buffer, state.status());
    }

    private static MemberState readMember(RegistryFriendlyByteBuf buffer, FabricReference scope) {
        return new MemberState(scope, new MemberId(scope.fabricId(), readId(buffer, scope)), new NetworkId(buffer.readUUID()),
                readStatus(buffer));
    }

    private static void writeProvider(RegistryFriendlyByteBuf buffer, ProviderState state) {
        writeId(buffer, state.id());
        writeStatus(buffer, state.status());
    }

    private static ProviderState readProvider(RegistryFriendlyByteBuf buffer, FabricReference scope) {
        return new ProviderState(scope, new ProviderId(scope.fabricId(), readId(buffer, scope)), readStatus(buffer));
    }

    private static void writeEndpoint(RegistryFriendlyByteBuf buffer, EndpointState state) {
        writeId(buffer, state.id());
        writeStatus(buffer, state.status());
    }

    private static EndpointState readEndpoint(RegistryFriendlyByteBuf buffer, FabricReference scope) {
        return new EndpointState(scope, new EndpointId(scope.fabricId(), readId(buffer, scope)), readStatus(buffer));
    }

    private static void writePolicy(RegistryFriendlyByteBuf buffer, PolicyState state) {
        writeId(buffer, state.id());
        writeStatus(buffer, state.status());
    }

    private static PolicyState readPolicy(RegistryFriendlyByteBuf buffer, FabricReference scope) {
        return new PolicyState(scope, new PolicyId(scope.fabricId(), readId(buffer, scope)), readStatus(buffer));
    }

    private static void writeLock(RegistryFriendlyByteBuf buffer, LockState state) {
        writeId(buffer, state.id());
        writeStatus(buffer, state.status());
    }

    private static LockState readLock(RegistryFriendlyByteBuf buffer, FabricReference scope) {
        return new LockState(scope, new LockId(scope.fabricId(), readId(buffer, scope)), readStatus(buffer));
    }

    private static void writeTask(RegistryFriendlyByteBuf buffer, TaskState state) {
        writeId(buffer, state.id());
        writeStatus(buffer, state.status());
    }

    private static TaskState readTask(RegistryFriendlyByteBuf buffer, FabricReference scope) {
        return new TaskState(scope, new TaskId(scope.fabricId(), readId(buffer, scope)), readStatus(buffer));
    }

    private static void writeFlow(RegistryFriendlyByteBuf buffer, FlowState state) {
        writeId(buffer, state.id());
        buffer.writeUUID(state.eventId().value());
        buffer.writeUtf(state.resource(), ObservationLimits.MAX_STRING_LENGTH);
        buffer.writeVarLong(state.amount());
        buffer.writeEnum(state.unit());
        buffer.writeEnum(state.attribution());
        buffer.writeBoolean(state.exactBatchCompletion());
    }

    private static FlowState readFlow(RegistryFriendlyByteBuf buffer, FabricReference scope) {
        return new FlowState(scope, new FlowId(scope.fabricId(), readId(buffer, scope)),
                new OperationEventId(buffer.readUUID()), buffer.readUtf(ObservationLimits.MAX_STRING_LENGTH),
                buffer.readVarLong(), buffer.readEnum(space.controlnet.ae2federation.observability.state.ResourceUnit.class),
                buffer.readEnum(FlowState.Attribution.class), buffer.readBoolean());
    }

    private static void writeId(RegistryFriendlyByteBuf buffer, ScopedObservationId id) {
        buffer.writeUtf(id.fabricId().value(), ObservationLimits.MAX_ID_LENGTH);
        buffer.writeUtf(id.value(), ObservationLimits.MAX_ID_LENGTH);
    }

    private static String readId(RegistryFriendlyByteBuf buffer, FabricReference scope) {
        var fabricId = new FabricId(buffer.readUtf(ObservationLimits.MAX_ID_LENGTH));
        if (!fabricId.equals(scope.fabricId())) {
            throw new IllegalArgumentException("Observation ID belongs to another Fabric");
        }
        return buffer.readUtf(ObservationLimits.MAX_ID_LENGTH);
    }

    private static void writeStatus(RegistryFriendlyByteBuf buffer, String value) {
        buffer.writeUtf(value, ObservationLimits.MAX_STRING_LENGTH);
    }

    private static String readStatus(RegistryFriendlyByteBuf buffer) {
        return buffer.readUtf(ObservationLimits.MAX_STRING_LENGTH);
    }

    private static <T> void writeList(RegistryFriendlyByteBuf buffer, List<T> values,
            BiConsumer<RegistryFriendlyByteBuf, T> encoder) {
        buffer.writeVarInt(values.size());
        values.forEach(value -> encoder.accept(buffer, value));
    }

    private static <T> List<T> readList(RegistryFriendlyByteBuf buffer, int maximum,
            Function<RegistryFriendlyByteBuf, T> decoder) {
        var size = buffer.readVarInt();
        ObservationLimits.boundedCount(size, maximum, "payload collection");
        var values = new ArrayList<T>(size);
        for (var index = 0; index < size; index++) {
            values.add(decoder.apply(buffer));
        }
        return List.copyOf(values);
    }
}
