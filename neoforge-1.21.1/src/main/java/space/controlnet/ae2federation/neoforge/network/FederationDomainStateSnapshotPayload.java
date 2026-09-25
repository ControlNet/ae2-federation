package space.controlnet.ae2federation.neoforge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import space.controlnet.ae2federation.observability.ObservationLimits;
import space.controlnet.ae2federation.observability.state.ObservationSnapshotEnvelope;

public record FederationDomainStateSnapshotPayload(ObservationSnapshotEnvelope envelope) implements CustomPacketPayload {
    public static final Type<FederationDomainStateSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("ae2federation", "domain_state_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FederationDomainStateSnapshotPayload> STREAM_CODEC =
            StreamCodec.ofMember(FederationDomainStateSnapshotPayload::encode, FederationDomainStateSnapshotPayload::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        var start = buffer.writerIndex();
        FederationDomainStateSnapshotCodec.encodeSession(buffer, envelope.session());
        FederationDomainStateSnapshotCodec.encode(buffer, envelope.snapshot());
        if (buffer.writerIndex() - start > ObservationLimits.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Observation snapshot payload is oversized");
        }
    }

    private static FederationDomainStateSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
        if (buffer.readableBytes() > ObservationLimits.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Observation snapshot payload is oversized");
        }
        var session = FederationDomainStateSnapshotCodec.decodeSession(buffer);
        var payload = new FederationDomainStateSnapshotPayload(new ObservationSnapshotEnvelope(session,
                FederationDomainStateSnapshotCodec.decode(buffer)));
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
