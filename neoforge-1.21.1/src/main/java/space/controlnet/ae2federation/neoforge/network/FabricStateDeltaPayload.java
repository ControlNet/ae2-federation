package space.controlnet.ae2federation.neoforge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import space.controlnet.ae2federation.observability.ObservationLimits;
import space.controlnet.ae2federation.observability.state.FabricStateDelta;
import space.controlnet.ae2federation.observability.state.ObservationDeltaEnvelope;

public record FabricStateDeltaPayload(ObservationDeltaEnvelope envelope) implements CustomPacketPayload {
    public static final Type<FabricStateDeltaPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("ae2federation", "fabric_state_delta"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FabricStateDeltaPayload> STREAM_CODEC =
            StreamCodec.ofMember(FabricStateDeltaPayload::encode, FabricStateDeltaPayload::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        var start = buffer.writerIndex();
        FabricStateSnapshotCodec.encodeSession(buffer, envelope.session());
        buffer.writeVarLong(envelope.delta().baseDataRevision());
        buffer.writeBoolean(envelope.delta().resnapshotRequired());
        FabricStateSnapshotCodec.encode(buffer, envelope.delta().replacement());
        if (buffer.writerIndex() - start > ObservationLimits.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Observation delta payload is oversized");
        }
    }

    private static FabricStateDeltaPayload decode(RegistryFriendlyByteBuf buffer) {
        if (buffer.readableBytes() > ObservationLimits.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Observation delta payload is oversized");
        }
        var session = FabricStateSnapshotCodec.decodeSession(buffer);
        var baseRevision = buffer.readVarLong();
        var resnapshotRequired = buffer.readBoolean();
        var delta = new FabricStateDelta(FabricStateSnapshotCodec.decode(buffer), baseRevision, resnapshotRequired);
        if (buffer.isReadable()) {
            throw new IllegalArgumentException("Observation delta payload has trailing data");
        }
        return new FabricStateDeltaPayload(new ObservationDeltaEnvelope(session, delta));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
