package space.controlnet.ae2federation.neoforge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import space.controlnet.ae2federation.observability.ObservationLimits;
import space.controlnet.ae2federation.observability.state.ObservationSnapshotEnvelope;

public record FabricStateSnapshotPayload(ObservationSnapshotEnvelope envelope) implements CustomPacketPayload {
    public static final Type<FabricStateSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("ae2federation", "fabric_state_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FabricStateSnapshotPayload> STREAM_CODEC =
            StreamCodec.ofMember(FabricStateSnapshotPayload::encode, FabricStateSnapshotPayload::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        var start = buffer.writerIndex();
        FabricStateSnapshotCodec.encodeSession(buffer, envelope.session());
        FabricStateSnapshotCodec.encode(buffer, envelope.snapshot());
        if (buffer.writerIndex() - start > ObservationLimits.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Observation snapshot payload is oversized");
        }
    }

    private static FabricStateSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
        if (buffer.readableBytes() > ObservationLimits.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Observation snapshot payload is oversized");
        }
        var session = FabricStateSnapshotCodec.decodeSession(buffer);
        var payload = new FabricStateSnapshotPayload(new ObservationSnapshotEnvelope(session,
                FabricStateSnapshotCodec.decode(buffer)));
        if (buffer.isReadable()) {
            throw new IllegalArgumentException("Observation snapshot payload has trailing data");
        }
        return payload;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
